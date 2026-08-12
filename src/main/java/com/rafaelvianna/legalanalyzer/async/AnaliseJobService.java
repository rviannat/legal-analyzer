package com.rafaelvianna.legalanalyzer.async;

import com.rafaelvianna.legalanalyzer.analysis.LegalAnalysisOrchestrator;
import com.rafaelvianna.legalanalyzer.exception.PdfProcessingException;
import com.rafaelvianna.legalanalyzer.pdf.PdfTextExtractionService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class AnaliseJobService {
    private final PdfTextExtractionService pdfTextExtractionService;
    private final LegalAnalysisOrchestrator orchestrator;
    private final Executor executor;
    private final Map<String, AnaliseJob> jobs = new ConcurrentHashMap<>();

    public AnaliseJobService(PdfTextExtractionService pdfTextExtractionService,
                             LegalAnalysisOrchestrator orchestrator,
                             Executor legalAnalysisExecutor) {
        this.pdfTextExtractionService = pdfTextExtractionService;
        this.orchestrator = orchestrator;
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
        AnaliseJob job = jobs.get(id);
        if (job == null) {
            throw new PdfProcessingException("Análise não encontrada: " + id);
        }
        return AnaliseJobResponse.status(job);
    }

    private void processar(AnaliseJob job, byte[] conteudo) {
        try {
            job.atualizar(AnaliseStatus.EXTRAINDO_PDF, 10, "Extraindo PDF", "Lendo e normalizando o conteúdo do documento.");
            String texto = pdfTextExtractionService.extractText(conteudo, job.nomeArquivo());

            job.atualizar(AnaliseStatus.ANALISANDO_PARTES, 35, "Analisando partes e fatos", "Identificando partes, cronologia, pedidos, decisões, prazos e documentos.");
            var resultado = orchestrator.analisar(job.nomeArquivo(), texto, (status, progresso, etapa, mensagem) ->
                    job.atualizar(status, progresso, etapa, mensagem));

            job.concluir(resultado);
        } catch (Exception e) {
            String mensagem = e.getMessage() == null ? "Erro inesperado durante a análise." : e.getMessage();
            job.falhar(mensagem);
        }
    }
}
