package com.rafaelvianna.legalanalyzer.async;

import com.rafaelvianna.legalanalyzer.analysis.specialized.SpecializedAnalysisOrchestrator;
import com.rafaelvianna.legalanalyzer.exception.PdfProcessingException;
import com.rafaelvianna.legalanalyzer.persistence.AnaliseEspecializadaEntity;
import com.rafaelvianna.legalanalyzer.persistence.AnaliseEspecializadaPersistenceService;
import com.rafaelvianna.legalanalyzer.pdf.RelatorioPdfService;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaRequest;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.OpcaoAnaliseEspecializadaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.TipoRascunho;
import com.rafaelvianna.legalanalyzer.rag.ProcessoIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/** Dispara, persiste e acompanha a análise especializada de longa duração. */
@Service
public class AnaliseEspecializadaJobService {
    private static final Logger log = LoggerFactory.getLogger(AnaliseEspecializadaJobService.class);

    public static final List<String> AGENTES = List.of(
            "Document Agent", "Process Agent", "Contract Agent", "Deadline Agent",
            "Evidence Agent", "Legal Research Agent", "Drafting Agent", "Senior Lawyer Agent");

    private final AnaliseJobService analiseJobService;
    private final SpecializedAnalysisOrchestrator orchestrator;
    private final ProcessoIndexService indexService;
    private final AnaliseEspecializadaPersistenceService persistenceService;
    private final RelatorioPdfService relatorioPdfService;
    private final Executor executor;
    private final Map<String, AnaliseEspecializadaJob> jobs = new ConcurrentHashMap<>();

    public AnaliseEspecializadaJobService(AnaliseJobService analiseJobService,
                                          SpecializedAnalysisOrchestrator orchestrator,
                                          ProcessoIndexService indexService,
                                          AnaliseEspecializadaPersistenceService persistenceService,
                                          RelatorioPdfService relatorioPdfService,
                                          Executor legalAnalysisExecutor) {
        this.analiseJobService = analiseJobService;
        this.orchestrator = orchestrator;
        this.indexService = indexService;
        this.persistenceService = persistenceService;
        this.relatorioPdfService = relatorioPdfService;
        this.executor = legalAnalysisExecutor;
    }

    public OpcaoAnaliseEspecializadaDTO opcao(AnaliseJob analiseBase) {
        if (analiseBase.status() != AnaliseStatus.CONCLUIDO || analiseBase.resultado() == null) {
            return OpcaoAnaliseEspecializadaDTO.indisponivel(
                    "A análise especializada fica disponível quando a análise base for concluída.");
        }
        boolean pesquisaHabilitada = orchestrator.pesquisaJuridicaHabilitada();
        String observacao = pesquisaHabilitada
                ? "Todos os resultados são rascunhos/pareceres de apoio e dependem de revisão do advogado."
                : "Pesquisa jurídica desabilitada: sem fontes autorizadas configuradas, nenhuma legislação ou jurisprudência será citada. Os demais agentes continuam disponíveis.";
        return new OpcaoAnaliseEspecializadaDTO(true,
                "/api/v1/processos/analises/" + analiseBase.id() + "/especializada", AGENTES,
                Arrays.asList(TipoRascunho.values()), pesquisaHabilitada, observacao);
    }

    public AnaliseEspecializadaJobResponse iniciar(String analiseBaseId, AnaliseEspecializadaRequest request) {
        AnaliseJob analiseBase = analiseJobService.buscar(analiseBaseId);
        if (analiseBase.status() != AnaliseStatus.CONCLUIDO || analiseBase.resultado() == null) {
            throw new PdfProcessingException("A análise base ainda não foi concluída (status atual: " + analiseBase.status() + "). Aguarde a conclusão antes de solicitar a análise especializada.");
        }

        String id = UUID.randomUUID().toString();
        AnaliseEspecializadaJob job = new AnaliseEspecializadaJob(id, analiseBaseId, analiseBase.nomeArquivo());
        jobs.put(id, job);
        persistenceService.criar(id, analiseBaseId, analiseBase.nomeArquivo());
        persistirStatus(job);

        AnaliseEspecializadaRequest opcoes = request == null ? AnaliseEspecializadaRequest.padrao() : request;
        log.info("[ESPECIALIZADA:{}] INICIADA | base={} | arquivo={} | agentes={}", id, analiseBaseId, analiseBase.nomeArquivo(), AGENTES.size());
        executor.execute(() -> processar(job, analiseBase, opcoes));
        return AnaliseEspecializadaJobResponse.status(job);
    }

    public AnaliseEspecializadaResponse ultimoResultadoDaBase(String analiseBaseId) {
        AnaliseEspecializadaResponse memoria = jobs.values().stream()
                .filter(j -> analiseBaseId.equals(j.analiseBaseId()))
                .filter(j -> j.status() == AnaliseEspecializadaStatus.CONCLUIDO && j.resultado() != null)
                .max(Comparator.comparing(AnaliseEspecializadaJob::atualizadoEm))
                .map(AnaliseEspecializadaJob::resultado).orElse(null);
        if (memoria != null) return memoria;
        AnaliseEspecializadaEntity entity = persistenceService.ultimaDaBase(analiseBaseId);
        return entity == null || !"CONCLUIDO".equals(entity.getStatus()) ? null : persistenceService.resultado(entity);
    }

    public AnaliseEspecializadaJobResponse consultar(String id) {
        AnaliseEspecializadaJob job = jobs.get(id);
        if (job != null) return AnaliseEspecializadaJobResponse.status(job);

        AnaliseEspecializadaEntity entity = persistenceService.buscar(id);
        if (entity == null) throw new PdfProcessingException("Análise especializada não encontrada: " + id);
        AnaliseEspecializadaResponse resultado = persistenceService.resultado(entity);
        List<Map<String, Object>> logs = persistenceService.desserializarLogs(entity.getLogsJson());
        long eta = 0;
        if (!"CONCLUIDO".equals(entity.getStatus()) && !logs.isEmpty()) {
            Object value = logs.get(logs.size() - 1).get("estimativaRestanteSegundos");
            if (value instanceof Number number) eta = number.longValue();
        }
        AnaliseEspecializadaStatus status;
        try { status = AnaliseEspecializadaStatus.valueOf(entity.getStatus()); }
        catch (IllegalArgumentException e) { status = AnaliseEspecializadaStatus.ERRO; }
        return new AnaliseEspecializadaJobResponse(entity.getId(), entity.getAnaliseBaseId(), entity.getNomeArquivo(),
                status, entity.getProgresso(), entity.getEtapa(), entity.getMensagem(), eta,
                entity.getCriadoEm(), entity.getAtualizadoEm(), logs, entity.getRelatorioPdf() != null, resultado);
    }

    public byte[] relatorio(String id) {
        byte[] pdf = persistenceService.relatorio(id);
        if (pdf == null || pdf.length == 0) throw new PdfProcessingException("Relatório especializado ainda não está disponível: " + id);
        return pdf;
    }

    private void processar(AnaliseEspecializadaJob job, AnaliseJob analiseBase, AnaliseEspecializadaRequest opcoes) {
        try {
            var resultado = orchestrator.analisar(analiseBase.id(), analiseBase.nomeArquivo(), analiseBase.textoExtraido(),
                    analiseBase.resultado(), opcoes, (status, progresso, etapa, mensagem) -> {
                        log.info("[ESPECIALIZADA:{}] AGENTE/ETAPA | status={} | {}% | {} | {}", job.id(), status, progresso, etapa, mensagem);
                        job.atualizar(status, progresso, etapa, mensagem);
                        persistirStatus(job);
                    });

            try {
                indexService.indexar(analiseBase.id(), analiseBase.paginas(), analiseBase.resultado(), resultado);
                log.info("[ESPECIALIZADA:{}] RAG | índice atualizado com os oito agentes", job.id());
            } catch (Exception e) {
                log.warn("[ESPECIALIZADA:{}] RAG | falha ao reindexar: {}", job.id(), e.getMessage());
            }

            job.atualizar(AnaliseEspecializadaStatus.PARECER_SENIOR, 98, "Gerando relatório",
                    "Gerando o PDF final da análise dos oito agentes e salvando no PostgreSQL.");
            persistirStatus(job);
            byte[] relatorio = relatorioPdfService.gerarEspecializada(analiseBase.nomeArquivo(), analiseBase.id(), resultado);
            persistenceService.concluir(job.id(), resultado, relatorio, job.logs());
            job.concluir(resultado);
            log.info("[ESPECIALIZADA:{}] CONCLUÍDA | PDF persistido=true | bytes={} | duração={}s",
                    job.id(), relatorio.length, java.time.Duration.between(job.criadoEm(), Instant.now()).toSeconds());
        } catch (Exception e) {
            log.error("[ESPECIALIZADA:{}] ERRO | etapa={} | progresso={} | {}", job.id(), job.etapa(), job.progresso(), e.getMessage(), e);
            job.falhar(e.getMessage() == null ? "Erro inesperado durante a análise especializada." : e.getMessage());
            persistenceService.falhar(job.id(), job.progresso(), job.etapa(), job.mensagem(), job.logs());
        }
    }

    private void persistirStatus(AnaliseEspecializadaJob job) {
        try {
            persistenceService.atualizar(job.id(), job.status().name(), job.progresso(), job.etapa(), job.mensagem(), job.logs());
        } catch (Exception e) {
            log.error("[ESPECIALIZADA:{}] PERSISTENCIA | falha ao salvar progresso: {}", job.id(), e.getMessage(), e);
        }
    }
}
