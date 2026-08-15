package com.rafaelvianna.legalanalyzer.async;

import com.rafaelvianna.legalanalyzer.analysis.specialized.SpecializedAnalysisOrchestrator;
import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import com.rafaelvianna.legalanalyzer.datajud.DataJudService;
import com.rafaelvianna.legalanalyzer.datajud.team3.ExternalValidationResult;
import com.rafaelvianna.legalanalyzer.datajud.team3.ExternalValidationTeam;
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

/** Orquestra a Equipe 2 e, somente depois da geração do PDF da Equipe 2, a Equipe 3/DataJud. */
@Service
public class AnaliseEspecializadaJobService {
    private static final Logger log = LoggerFactory.getLogger(AnaliseEspecializadaJobService.class);
    public static final List<String> AGENTES = List.of("Document Agent", "Process Agent", "Contract Agent", "Deadline Agent", "Evidence Agent", "Legal Research Agent", "Drafting Agent", "Senior Lawyer Agent");
    public static final List<String> AGENTES_EQUIPE_3 = List.of("ProcessSearchAgent", "MovementAgent", "PartiesAgent", "DecisionsAgent", "CourtAgent", "TimelineAgent", "ExternalEvidenceAgent", "JusReconciliationAgent");

    private final AnaliseJobService analiseJobService;
    private final SpecializedAnalysisOrchestrator orchestrator;
    private final ProcessoIndexService indexService;
    private final AnaliseEspecializadaPersistenceService persistenceService;
    private final RelatorioPdfService relatorioPdfService;
    private final DataJudService dataJudService;
    private final ExternalValidationTeam externalValidationTeam;
    private final Executor executor;
    private final Map<String, AnaliseEspecializadaJob> jobs = new ConcurrentHashMap<>();

    public AnaliseEspecializadaJobService(AnaliseJobService analiseJobService, SpecializedAnalysisOrchestrator orchestrator,
                                          ProcessoIndexService indexService, AnaliseEspecializadaPersistenceService persistenceService,
                                          RelatorioPdfService relatorioPdfService, DataJudService dataJudService,
                                          ExternalValidationTeam externalValidationTeam, Executor legalAnalysisExecutor) {
        this.analiseJobService = analiseJobService; this.orchestrator = orchestrator; this.indexService = indexService;
        this.persistenceService = persistenceService; this.relatorioPdfService = relatorioPdfService;
        this.dataJudService = dataJudService; this.externalValidationTeam = externalValidationTeam; this.executor = legalAnalysisExecutor;
    }

    public OpcaoAnaliseEspecializadaDTO opcao(AnaliseJob analiseBase) {
        if (analiseBase.status() != AnaliseStatus.CONCLUIDO || analiseBase.resultado() == null)
            return OpcaoAnaliseEspecializadaDTO.indisponivel("A análise especializada fica disponível quando a análise base for concluída.");
        boolean pesquisaHabilitada = orchestrator.pesquisaJuridicaHabilitada();
        String observacao = pesquisaHabilitada ? "Equipe 2 será executada, seu PDF será gerado e somente depois a Equipe 3/DataJud fará a validação externa."
                : "Pesquisa jurídica desabilitada. A Equipe 2 continuará disponível, seu PDF será gerado e a Equipe 3 será iniciada somente após sua conclusão.";
        return new OpcaoAnaliseEspecializadaDTO(true, "/api/v1/processos/analises/" + analiseBase.id() + "/especializada", AGENTES,
                Arrays.asList(TipoRascunho.values()), pesquisaHabilitada, observacao);
    }

    public AnaliseEspecializadaJobResponse iniciar(String analiseBaseId, AnaliseEspecializadaRequest request) {
        AnaliseJob analiseBase = analiseJobService.buscar(analiseBaseId);
        if (analiseBase.status() != AnaliseStatus.CONCLUIDO || analiseBase.resultado() == null)
            throw new PdfProcessingException("A análise base ainda não foi concluída (status atual: " + analiseBase.status() + "). Aguarde a conclusão antes de solicitar a análise especializada.");
        String id = UUID.randomUUID().toString();
        AnaliseEspecializadaJob job = new AnaliseEspecializadaJob(id, analiseBaseId, analiseBase.nomeArquivo());
        jobs.put(id, job); persistenceService.criar(id, analiseBaseId, analiseBase.nomeArquivo()); persistirStatus(job);
        AnaliseEspecializadaRequest opcoes = request == null ? AnaliseEspecializadaRequest.padrao() : request;
        log.info("[PROCESSO:{}][EQUIPE_2] INICIADA | job={} | arquivo={} | agentes={}", analiseBaseId, id, analiseBase.nomeArquivo(), AGENTES.size());
        executor.execute(() -> processar(job, analiseBase, opcoes));
        return AnaliseEspecializadaJobResponse.status(job);
    }

    public AnaliseEspecializadaResponse ultimoResultadoDaBase(String analiseBaseId) {
        AnaliseEspecializadaResponse memoria = jobs.values().stream().filter(j -> analiseBaseId.equals(j.analiseBaseId()))
                .filter(j -> j.status() == AnaliseEspecializadaStatus.CONCLUIDO && j.resultado() != null)
                .max(Comparator.comparing(AnaliseEspecializadaJob::atualizadoEm)).map(AnaliseEspecializadaJob::resultado).orElse(null);
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
            Object value = logs.get(logs.size() - 1).get("estimativaRestanteSegundos"); if (value instanceof Number number) eta = number.longValue();
        }
        AnaliseEspecializadaStatus status;
        try { status = AnaliseEspecializadaStatus.valueOf(entity.getStatus()); } catch (IllegalArgumentException e) { status = AnaliseEspecializadaStatus.ERRO; }
        String equipe = "EQUIPE_2";
        if (!logs.isEmpty()) { Object valor = logs.get(logs.size() - 1).get("equipe"); if (valor != null) equipe = String.valueOf(valor); }
        return new AnaliseEspecializadaJobResponse(entity.getId(), entity.getAnaliseBaseId(), entity.getNomeArquivo(), status,
                equipe, entity.getProgresso(), entity.getEtapa(), entity.getMensagem(), eta, entity.getCriadoEm(), entity.getAtualizadoEm(),
                logs, entity.getRelatorioPdf() != null, resultado);
    }

    public byte[] relatorio(String id) {
        byte[] pdf = persistenceService.relatorio(id);
        if (pdf == null || pdf.length == 0) throw new PdfProcessingException("Relatório especializado ainda não está disponível: " + id);
        return pdf;
    }

    private void processar(AnaliseEspecializadaJob job, AnaliseJob analiseBase, AnaliseEspecializadaRequest opcoes) {
        try {
            var resultado = orchestrator.analisar(analiseBase.id(), analiseBase.nomeArquivo(), analiseBase.textoExtraido(), analiseBase.resultado(), opcoes,
                    (status, progresso, etapa, mensagem) -> {
                        log.info("[PROCESSO:{}][EQUIPE_2] AGENTE/ETAPA | status={} | {}% | {} | {}", analiseBase.id(), status, progresso, etapa, mensagem);
                        job.atualizar(status, progresso, etapa, mensagem); persistirStatus(job);
                    });
            try { indexService.indexar(analiseBase.id(), analiseBase.paginas(), analiseBase.resultado(), resultado); log.info("[PROCESSO:{}][EQUIPE_2] RAG | índice atualizado com os oito agentes", analiseBase.id()); }
            catch (Exception e) { log.warn("[PROCESSO:{}][EQUIPE_2] RAG | falha ao reindexar: {}", analiseBase.id(), e.getMessage()); }

            // Fase 1: a Equipe 2 precisa terminar inclusive a geração do seu PDF antes de qualquer consulta DataJud.
            job.atualizar(AnaliseEspecializadaStatus.PARECER_SENIOR, 96, "Equipe 2 — Gerando PDF", "Gerando o relatório PDF da Equipe 2. A Equipe 3 aguardará até o PDF estar completamente gerado.");
            persistirStatus(job);
            log.info("[PROCESSO:{}][EQUIPE_2] GERANDO_PDF | Equipe 3 bloqueada até o término da geração", analiseBase.id());
            byte[] pdfEquipe2 = relatorioPdfService.gerarEspecializada(analiseBase.nomeArquivo(), analiseBase.id(), resultado);
            if (pdfEquipe2 == null || pdfEquipe2.length == 0) throw new PdfProcessingException("O PDF da Equipe 2 não foi gerado; a Equipe 3 não será iniciada.");
            log.info("[PROCESSO:{}][EQUIPE_2] PDF_GERADO | bytes={} | Equipe 3 liberada", analiseBase.id(), pdfEquipe2.length);

            // Fase 2: somente agora a Equipe 3/DataJud começa do zero.
            log.info("[PROCESSO:{}][EQUIPE_2] CONCLUÍDA | PDF pronto | iniciando Equipe 3", analiseBase.id());
            executarEquipe3(job, analiseBase);

            job.atualizar(AnaliseEspecializadaStatus.PARECER_SENIOR, 98, "Equipe 3 — Gerando relatório final", "Gerando o PDF final após a validação externa e salvando no PostgreSQL.");
            persistirStatus(job);
            byte[] relatorioFinal = relatorioPdfService.gerarEspecializada(analiseBase.nomeArquivo(), analiseBase.id(), resultado);
            job.concluir(resultado);
            persistenceService.concluir(job.id(), resultado, relatorioFinal, job.logs());
            log.info("[PROCESSO:{}][EQUIPE_3] CONCLUÍDA | PDF final persistido=true | bytes={} | duração={}s", analiseBase.id(), relatorioFinal.length, java.time.Duration.between(job.criadoEm(), Instant.now()).toSeconds());
        } catch (Exception e) {
            log.error("[PROCESSO:{}][{}] ERRO | etapa={} | progresso={} | {}", analiseBase.id(), job.equipeAtual(), job.etapa(), job.progresso(), e.getMessage(), e);
            job.falhar(e.getMessage() == null ? "Erro inesperado durante o processamento das equipes." : e.getMessage());
            persistenceService.falhar(job.id(), job.progresso(), job.etapa(), job.mensagem(), job.logs());
        }
    }

    private void executarEquipe3(AnaliseEspecializadaJob job, AnaliseJob analiseBase) {
        job.iniciarEquipe3(); persistirStatus(job);
        String numeroProcesso = analiseBase.numeroProcesso();
        if (numeroProcesso == null || numeroProcesso.isBlank() || "não identificado".equalsIgnoreCase(numeroProcesso)) {
            log.warn("[PROCESSO:{}][EQUIPE_3_DATAJUD] NÃO EXECUTADA | CNJ não identificado | Equipe 2 já possui PDF concluído", analiseBase.id());
            job.atualizarEquipe3(AnaliseEspecializadaStatus.PARECER_SENIOR, 100, "SYSTEM", 0, "INSUFICIENTE_DADOS", "Informações insuficientes: não foi identificado um número CNJ no documento. A Equipe 3 não possui uma chave para consultar o DataJud; as Equipes 1 e 2 permanecem válidas.", List.of("Equipe 1", "Equipe 2"), "Fonte não consultada: DataJud/CNJ");
            persistirStatus(job);
            return;
        }

        job.atualizarEquipe3(AnaliseEspecializadaStatus.PARECER_SENIOR, 5, "ProcessSearchAgent", 1, "SEARCH_PROCESS", "Consultando o processo oficial no DataJud/CNJ. Os demais agentes aguardarão o resultado desta consulta.", List.of("Equipe 1", "Equipe 2"), "CNJ=" + numeroProcesso);
        persistirStatus(job);
        try {
            DataJudInfo info = dataJudService.consultar(numeroProcesso);
            String disponibilidade = info.encontrado() ? "Processo localizado na fonte externa." : "Informações insuficientes: processo não localizado na fonte externa DataJud/CNJ. Agentes dependentes não irão inferir dados ausentes.";
            log.info("[PROCESSO:{}][EQUIPE_3_DATAJUD] PROCESSO | status={} | encontrado={} | tribunal={} | movimentos={} | mensagem={}", analiseBase.id(), info.status(), info.encontrado(), info.tribunal(), info.quantidadeMovimentos(), disponibilidade);
            List<ExternalValidationResult> resultados = externalValidationTeam.executar(info, (agente, progresso) -> {
                int numero = AGENTES_EQUIPE_3.indexOf(agente) + 1;
                String acao = agente.equals("JusReconciliationAgent") ? "RECONCILING" : "VALIDATING_EXTERNAL_DATA";
                String descricao = info.encontrado() ? descricaoEquipe3(agente, info) : "Informações insuficientes: DataJud não localizou o processo; este agente registrará a limitação sem interromper a análise.";
                job.atualizarEquipe3(AnaliseEspecializadaStatus.PARECER_SENIOR, progresso, agente, Math.max(1, numero), acao,
                        descricao, List.of("Equipe 1", "Equipe 2", "Equipe 3"), "Processo=" + numeroProcesso + " | DataJud=" + info.status());
                persistirStatus(job);
                log.info("[PROCESSO:{}][EQUIPE_3_DATAJUD][AGENTE:{}] {}% | statusDataJud={} | {}", analiseBase.id(), agente, progresso, info.status(), descricao);
            });
            for (ExternalValidationResult r : resultados) {
                log.info("[PROCESSO:{}][EQUIPE_3_DATAJUD][RESULTADO:{}] status={} | {} | achados={}", analiseBase.id(), r.agent(), r.status(), r.summary(), r.findings().size());
            }
            job.atualizarEquipe3(AnaliseEspecializadaStatus.PARECER_SENIOR, 100, "JusReconciliationAgent", 8, "RECONCILING",
                    info.encontrado() ? "Reconciliação externa concluída. Confirmações, divergências e lacunas foram registradas." : "Reconciliação concluída com informações insuficientes: o processo não foi localizado no DataJud/CNJ. As limitações foram registradas para os agentes dependentes e no relatório.",
                    List.of("Equipe 1", "Equipe 2", "Equipe 3"), "DataJud=" + info.status() + " | Resultados externos=" + resultados.size());
            persistirStatus(job);
        } catch (Exception e) {
            log.warn("[PROCESSO:{}][EQUIPE_3_DATAJUD] FALHA | validação externa marcada como indisponível; análise continuará | {}", analiseBase.id(), e.getMessage());
            job.atualizarEquipe3(AnaliseEspecializadaStatus.PARECER_SENIOR, 100, "SYSTEM", 0, "EXTERNAL_VALIDATION_UNAVAILABLE",
                    "Informações insuficientes: o DataJud não pôde ser consultado. A Equipe 3 foi encerrada sem descartar os resultados das Equipes 1 e 2.",
                    List.of("Equipe 1", "Equipe 2"), e.getMessage());
            persistirStatus(job);
        }
    }

    private String descricaoEquipe3(String agente, DataJudInfo info) {
        return switch (agente) {
            case "ProcessSearchAgent" -> "Confirmando a identificação oficial do processo.";
            case "MovementAgent" -> "Normalizando movimentações e eventos oficiais.";
            case "PartiesAgent" -> "Validando partes e polos disponíveis na fonte externa.";
            case "DecisionsAgent" -> "Verificando decisões e atos relevantes disponíveis.";
            case "CourtAgent" -> "Validando tribunal, órgão julgador, classe e grau.";
            case "TimelineAgent" -> "Construindo a linha do tempo externa para confronto.";
            case "ExternalEvidenceAgent" -> "Transformando dados externos em evidências auditáveis.";
            case "JusReconciliationAgent" -> "Cruzando os achados externos e preparando a reconciliação.";
            default -> "Processando validação externa.";
        };
    }

    private void persistirStatus(AnaliseEspecializadaJob job) {
        try { persistenceService.atualizar(job.id(), job.status().name(), job.progresso(), job.etapa(), job.mensagem(), job.logs()); }
        catch (Exception e) { log.error("[PROCESSO:{}][{}] PERSISTENCIA | falha ao salvar progresso: {}", job.analiseBaseId(), job.equipeAtual(), e.getMessage(), e); }
    }
}
