package com.rafaelvianna.legalanalyzer.async;

import com.rafaelvianna.legalanalyzer.analysis.LegalAnalysisOrchestrator;
import com.rafaelvianna.legalanalyzer.exception.PdfProcessingException;
import com.rafaelvianna.legalanalyzer.persistence.ProcessoEntity;
import com.rafaelvianna.legalanalyzer.persistence.ProcessoPersistenceService;
import com.rafaelvianna.legalanalyzer.pdf.PaginaExtraida;
import com.rafaelvianna.legalanalyzer.pdf.PdfTextExtractionService;
import com.rafaelvianna.legalanalyzer.pdf.RelatorioPdfService;
import com.rafaelvianna.legalanalyzer.rag.ProcessoIndexService;
import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
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
    private final ProcessoPersistenceService persistenceService;
    private final RelatorioPdfService relatorioPdfService;
    private final Executor executor;
    private final AnaliseEspecializadaJobService especializadaService;
    private final Map<String, AnaliseJob> jobs = new ConcurrentHashMap<>();

    public AnaliseJobService(PdfTextExtractionService pdfTextExtractionService, LegalAnalysisOrchestrator orchestrator,
                             ProcessoIndexService indexService, ProcessoPersistenceService persistenceService,
                             RelatorioPdfService relatorioPdfService, Executor legalAnalysisExecutor,
                             @Lazy AnaliseEspecializadaJobService especializadaService) {
        this.pdfTextExtractionService = pdfTextExtractionService;
        this.orchestrator = orchestrator;
        this.indexService = indexService;
        this.persistenceService = persistenceService;
        this.relatorioPdfService = relatorioPdfService;
        this.executor = legalAnalysisExecutor;
        this.especializadaService = especializadaService;
    }

    public AnaliseJobResponse iniciar(MultipartFile arquivo) {
        String nome = arquivo.getOriginalFilename() == null ? "processo.pdf" : arquivo.getOriginalFilename();
        final byte[] conteudo;
        try { conteudo = arquivo.getBytes(); }
        catch (Exception e) { throw new PdfProcessingException("Não foi possível preparar o PDF para processamento: " + e.getMessage(), e); }
        String id = UUID.randomUUID().toString();
        AnaliseJob job = new AnaliseJob(id, nome);
        jobs.put(id, job);
        persistenceService.criar(id, nome, conteudo);
        log.info("[PROCESSO:{}][EQUIPE_1] RECEBIDO | arquivo={} | bytes={} | persistido=true", id, nome, conteudo.length);
        executor.execute(() -> processar(job, conteudo));
        return AnaliseJobResponse.status(job);
    }

    public AnaliseJobResponse consultar(String id) { return AnaliseJobResponse.status(buscar(id)); }
    public AnaliseJob buscar(String id) {
        AnaliseJob job = jobs.get(id);
        if (job == null) throw new PdfProcessingException("Análise não encontrada: " + id);
        return job;
    }

    private void atualizar(AnaliseJob job, AnaliseStatus status, int progresso, String etapa, String mensagem) {
        job.atualizar(status, progresso, etapa, mensagem);
        try { persistenceService.atualizar(job.id(), job.numeroProcesso(), ProcessoEntity.Status.valueOf(status.name()), progresso, etapa, mensagem); }
        catch (Exception e) { log.error("[PROCESSO:{}][EQUIPE_1] PERSISTENCIA | status={} | {}", job.id(), status, e.getMessage(), e); }
    }

    private void indexarComSeguranca(AnaliseJob job, AnaliseProcessoResponse resultado) {
        try { indexService.indexar(job.id(), job.paginas(), resultado, null); }
        catch (Exception e) { log.warn("[PROCESSO:{}][EQUIPE_1] RAG | falha ao indexar: {}", job.id(), e.getMessage()); }
    }

    private void processar(AnaliseJob job, byte[] conteudo) {
        try {
            atualizar(job, AnaliseStatus.EXTRAINDO_PDF, 10, "Equipe 1 — Extraindo PDF", "Lendo e normalizando o conteúdo do documento.");
            log.info("[PROCESSO:{}][EQUIPE_1] PDF | extração iniciada | arquivo={}", job.id(), job.nomeArquivo());
            List<PaginaExtraida> paginas = pdfTextExtractionService.extractPages(conteudo, job.nomeArquivo());
            String texto = pdfTextExtractionService.extractText(conteudo, job.nomeArquivo());
            job.paginas(paginas); job.textoExtraido(texto);
            job.numeroProcesso(ProcessoIndexService.numeroProcesso(paginas, texto));
            log.info("[PROCESSO:{}][EQUIPE_1] PDF | páginas={} | caracteres={} | CNJ={}", job.id(), paginas.size(), texto == null ? 0 : texto.length(), job.numeroProcesso());

            atualizar(job, AnaliseStatus.ANALISANDO_PARTES, 35, "Equipe 1 — Analisando documento", "Identificando partes, cronologia, pedidos, decisões, prazos e documentos.");
            log.info("[PROCESSO:{}][EQUIPE_1] AGENTES | análise documental iniciada", job.id());
            var resultado = orchestrator.analisar(job.nomeArquivo(), texto, (status, progresso, etapa, mensagem) -> {
                log.info("[PROCESSO:{}][EQUIPE_1] ETAPA | {} | {}% | {}", job.id(), etapa, progresso, mensagem);
                atualizar(job, status, progresso, "Equipe 1 — " + etapa, mensagem);
            });

            atualizar(job, AnaliseStatus.GERANDO_RELATORIO, 92, "Equipe 1 — Gerando relatório", "Montando o relatório final da análise documental.");
            byte[] relatorio = relatorioPdfService.gerar(job.nomeArquivo(), job.numeroProcesso(), resultado);
            persistenceService.salvarRelatorio(job.id(), relatorio);
            atualizar(job, AnaliseStatus.CONSOLIDANDO, 95, "Equipe 1 — Indexando o caso", "Montando o índice do processo para as equipes seguintes.");
            indexarComSeguranca(job, resultado);
            job.concluir(resultado);
            persistenceService.atualizar(job.id(), job.numeroProcesso(), ProcessoEntity.Status.CONCLUIDO, 100, "Equipe 1 concluída", "Análise documental concluída. Equipe 2 será iniciada em seguida.");
            log.info("[PROCESSO:{}][EQUIPE_1] CONCLUÍDA | relatório persistido=true | bytes={}", job.id(), relatorio.length);

            executor.execute(() -> {
                try {
                    especializadaService.iniciar(job.id(), null);
                    log.info("[PROCESSO:{}][EQUIPE_2] JOB CRIADO | início sequencial após Equipe 1", job.id());
                } catch (Exception e) {
                    log.error("[PROCESSO:{}][EQUIPE_2] não foi possível iniciar: {}", job.id(), e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            log.error("[PROCESSO:{}][EQUIPE_1] ERRO | etapa={} | progresso={} | {}", job.id(), job.etapa(), job.progresso(), e.getMessage(), e);
            job.falhar(e.getMessage() == null ? "Erro inesperado durante a análise." : e.getMessage());
            try { persistenceService.atualizar(job.id(), job.numeroProcesso(), ProcessoEntity.Status.ERRO, job.progresso(), job.etapa(), job.mensagem()); }
            catch (Exception persistencia) { log.error("[PROCESSO:{}] PERSISTENCIA | falha ao salvar erro: {}", job.id(), persistencia.getMessage(), persistencia); }
        }
    }
}
