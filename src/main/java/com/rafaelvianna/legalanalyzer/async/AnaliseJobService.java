package com.rafaelvianna.legalanalyzer.async;

import com.rafaelvianna.legalanalyzer.analysis.LegalAnalysisOrchestrator;
import com.rafaelvianna.legalanalyzer.exception.PdfProcessingException;
import com.rafaelvianna.legalanalyzer.pdf.PdfTextExtractionService;
import com.rafaelvianna.legalanalyzer.pdf.PaginaExtraida;
import com.rafaelvianna.legalanalyzer.rag.ProcessoIndexService;
import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class AnaliseJobService {

    private static final Logger log = LoggerFactory.getLogger(AnaliseJobService.class);

    private final PdfTextExtractionService pdfTextExtractionService;
    private final LegalAnalysisOrchestrator orchestrator;
    private final ProcessoIndexService indexService;
    private final Executor executor;
    private final Map<String, AnaliseJob> jobs = new ConcurrentHashMap<>();

    public AnaliseJobService(PdfTextExtractionService pdfTextExtractionService,
                             LegalAnalysisOrchestrator orchestrator,
                             ProcessoIndexService indexService,
                             Executor legalAnalysisExecutor) {
        this.pdfTextExtractionService = pdfTextExtractionService;
        this.orchestrator = orchestrator;
        this.indexService = indexService;
        this.executor = legalAnalysisExecutor;
    }

    public AnaliseJobResponse iniciar(MultipartFile arquivo) {
        String nome = arquivo.getOriginalFilename() == null ? "processo.pdf" : arquivo.getOriginalFilename();
        final byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (Exception e) {
            throw new PdfProcessingException("Não foi possível preparar o PDF para processamento: " + e.getMessage(), e);
        }
        String id = UUID.randomUUID().toString();
        AnaliseJob job = new AnaliseJob(id, nome);
        jobs.put(id, job);

        executor.execute(() -> processar(job, conteudo));
        return AnaliseJobResponse.status(job);
    }

    public AnaliseJobResponse consultar(String id) {
        return AnaliseJobResponse.status(buscar(id));
    }

    /**
     * Recupera o job da análise base (com texto extraído e resultado), para que
     * a análise especializada não precise reprocessar o PDF.
     */
    public AnaliseJob buscar(String id) {
        AnaliseJob job = jobs.get(id);
        if (job == null) {
            throw new PdfProcessingException("Análise não encontrada: " + id);
        }
        return job;
    }

    /**
     * Monta o índice de recuperação. Uma falha aqui não invalida a análise:
     * o relatório continua sendo entregue, apenas sem briefing/chat.
     */
    private void indexarComSeguranca(AnaliseJob job, AnaliseProcessoResponse resultado) {
        try {
            indexService.indexar(job.id(), job.paginas(), resultado, null);
        } catch (Exception e) {
            log.warn("Falha ao indexar o caso {} para o RAG: {}", job.id(), e.getMessage());
        }
    }

    private void processar(AnaliseJob job, byte[] conteudo) {
        try {
            job.atualizar(AnaliseStatus.EXTRAINDO_PDF, 10, "Extraindo PDF", "Lendo e normalizando o conteúdo do documento.");
            // Extração página por página: sem o número da página não há como
            // o briefing e o chat apontarem onde conferir cada informação.
            List<PaginaExtraida> paginas = pdfTextExtractionService.extractPages(conteudo, job.nomeArquivo());
            String texto = pdfTextExtractionService.extractText(conteudo, job.nomeArquivo());
            job.paginas(paginas);
            job.textoExtraido(texto);
            job.numeroProcesso(ProcessoIndexService.numeroProcesso(paginas, texto));

            job.atualizar(AnaliseStatus.ANALISANDO_PARTES, 35, "Analisando partes e fatos", "Identificando partes, cronologia, pedidos, decisões, prazos e documentos.");
            var resultado = orchestrator.analisar(job.nomeArquivo(), texto, (status, progresso, etapa, mensagem) ->
                    job.atualizar(status, progresso, etapa, mensagem));

            job.atualizar(AnaliseStatus.CONCLUIDO, 95, "Indexando o caso",
                    "Montando o índice do processo para o briefing e o chat.");
            indexarComSeguranca(job, resultado);

            job.concluir(resultado);
        } catch (Exception e) {
            // Sem este log a causa da falha se perdia: o job ficava em ERRO com o
            // progresso congelado no último valor publicado (tipicamente 55%) e
            // nada no console indicava se foi timeout, JSON inválido ou OOM.
            log.error("Falha na análise {} ({}) na etapa '{}' ({}%): {}",
                    job.id(), job.nomeArquivo(), job.etapa(), job.progresso(), e.getMessage(), e);
            String mensagem = e.getMessage() == null ? "Erro inesperado durante a análise." : e.getMessage();
            job.falhar(mensagem);
        }
    }
}
