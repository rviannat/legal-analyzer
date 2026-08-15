package com.rafaelvianna.legalanalyzer.analysis.external;

import com.rafaelvianna.legalanalyzer.analysis.external.agents.CourtAgent;
import com.rafaelvianna.legalanalyzer.analysis.external.agents.DecisionsAgent;
import com.rafaelvianna.legalanalyzer.analysis.external.agents.ExternalEvidenceAgent;
import com.rafaelvianna.legalanalyzer.analysis.external.agents.JusReconciliationAgent;
import com.rafaelvianna.legalanalyzer.analysis.external.agents.MovementAgent;
import com.rafaelvianna.legalanalyzer.analysis.external.agents.PartiesAgent;
import com.rafaelvianna.legalanalyzer.analysis.external.agents.ProcessSearchAgent;
import com.rafaelvianna.legalanalyzer.analysis.external.agents.TimelineAgent;
import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import com.rafaelvianna.legalanalyzer.datajud.DataJudService;
import com.rafaelvianna.legalanalyzer.web.dto.ExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Orquestra a Equipe 3. A consulta oficial é feita uma única vez e os resultados são reconciliados. */
@Service
public class ExternalValidationTeam {
    private static final Logger log = LoggerFactory.getLogger(ExternalValidationTeam.class);
    private static final Pattern CNJ = Pattern.compile("\\b\\d{7}-\\d{2}\\.\\d{4}\\.\\d{1,2}\\.\\d{2}\\.\\d{4}\\b");
    private static final String INSUFICIENTE_DADOS = "INSUFICIENTE_DADOS";

    private final DataJudService dataJudService;
    private final ProcessSearchAgent processSearchAgent;
    private final MovementAgent movementAgent;
    private final PartiesAgent partiesAgent;
    private final DecisionsAgent decisionsAgent;
    private final CourtAgent courtAgent;
    private final TimelineAgent timelineAgent;
    private final ExternalEvidenceAgent externalEvidenceAgent;
    private final JusReconciliationAgent reconciliationAgent;

    public ExternalValidationTeam(DataJudService dataJudService,
                                  ProcessSearchAgent processSearchAgent,
                                  MovementAgent movementAgent,
                                  PartiesAgent partiesAgent,
                                  DecisionsAgent decisionsAgent,
                                  CourtAgent courtAgent,
                                  TimelineAgent timelineAgent,
                                  ExternalEvidenceAgent externalEvidenceAgent,
                                  JusReconciliationAgent reconciliationAgent) {
        this.dataJudService = dataJudService;
        this.processSearchAgent = processSearchAgent;
        this.movementAgent = movementAgent;
        this.partiesAgent = partiesAgent;
        this.decisionsAgent = decisionsAgent;
        this.courtAgent = courtAgent;
        this.timelineAgent = timelineAgent;
        this.externalEvidenceAgent = externalEvidenceAgent;
        this.reconciliationAgent = reconciliationAgent;
    }

    public ExternalValidationResult execute(String textoCompleto, ExtractionResult contextoInterno) {
        return execute(textoCompleto, contextoInterno, null);
    }

    public ExternalValidationResult execute(String textoCompleto, ExtractionResult contextoInterno,
                                            BiConsumer<String, Integer> progresso) {
        String numeroProcesso = extrairCnj(textoCompleto);
        if (numeroProcesso == null) {
            log.info("[EQUIPE 3] CNJ não identificado; validação externa não iniciada.");
            return ExternalValidationResult.semProcesso("CNJ não identificado no documento.");
        }
        DataJudInfo info = dataJudService.consultar(numeroProcesso);
        return execute(info, contextoInterno, progresso);
    }

    /** Reutiliza uma consulta DataJud já realizada, evitando chamadas duplicadas. */
    public ExternalValidationResult execute(DataJudInfo info, ExtractionResult contextoInterno,
                                            BiConsumer<String, Integer> progresso) {
        if (info == null) return ExternalValidationResult.semProcesso("Dados DataJud ausentes.");
        log.info("[EQUIPE 3] Iniciando especialistas para o processo {} | status={} | encontrado={}",
                info.numeroProcesso(), info.status(), info.encontrado());
        List<ExternalAgentResult> resultados = new ArrayList<>();

        executar("ProcessSearchAgent", 12, () -> processSearchAgent.execute(info), resultados, progresso);

        if (!info.encontrado()) {
            String motivo = motivoInsuficiencia(info);
            log.warn("[EQUIPE 3] PROCESSO NÃO LOCALIZADO | status={} | motivo={} | agentes dependentes receberão INSUFICIENTE_DADOS",
                    info.status(), motivo);
            registrarSemDados("MovementAgent", 24, motivo, resultados, progresso);
            registrarSemDados("PartiesAgent", 36, motivo, resultados, progresso);
            registrarSemDados("DecisionsAgent", 48, motivo, resultados, progresso);
            registrarSemDados("CourtAgent", 60, motivo, resultados, progresso);
            registrarSemDados("TimelineAgent", 72, motivo, resultados, progresso);
            registrarSemDados("ExternalEvidenceAgent", 84, motivo, resultados, progresso);
        } else {
            executar("MovementAgent", 24, () -> movementAgent.execute(info), resultados, progresso);
            executar("PartiesAgent", 36, () -> partiesAgent.execute(info), resultados, progresso);
            executar("DecisionsAgent", 48, () -> decisionsAgent.execute(info), resultados, progresso);
            executar("CourtAgent", 60, () -> courtAgent.execute(info), resultados, progresso);
            executar("TimelineAgent", 72, () -> timelineAgent.execute(info), resultados, progresso);
            executar("ExternalEvidenceAgent", 84, () -> externalEvidenceAgent.execute(info, contextoInterno), resultados, progresso);
        }

        ExternalAgentResult reconciliation = reconciliationAgent.execute(resultados, contextoInterno, info);
        resultados.add(reconciliation);
        if (progresso != null) progresso.accept("JusReconciliationAgent", 100);
        log.info("[EQUIPE 3] Validação concluída: status={}, agentes={}, datajudEncontrado={}",
                reconciliation.status(), resultados.size(), info.encontrado());
        return new ExternalValidationResult(info.numeroProcesso(), info, resultados, reconciliation, Instant.now());
    }

    private void executar(String agente, int progresso, java.util.function.Supplier<ExternalAgentResult> acao,
                          List<ExternalAgentResult> resultados, BiConsumer<String, Integer> callback) {
        log.info("[EQUIPE 3][AGENTE:{}] iniciando", agente);
        ExternalAgentResult resultado = acao.get();
        resultados.add(resultado);
        log.info("[EQUIPE 3][AGENTE:{}] concluído | status={} | {}", agente, resultado.status(), resultado.summary());
        if (callback != null) callback.accept(agente, progresso);
    }

    private void registrarSemDados(String agente, int progresso, String motivo,
                                   List<ExternalAgentResult> resultados, BiConsumer<String, Integer> callback) {
        log.info("[EQUIPE 3][AGENTE:{}] INSUFICIENTE_DADOS | {}", agente, motivo);
        resultados.add(new ExternalAgentResult(
                agente,
                INSUFICIENTE_DADOS,
                "Informações insuficientes: o processo não foi localizado na fonte externa. O agente não interrompeu a análise e não deve inferir dados ausentes.",
                Map.of("fonte", "DataJud/CNJ", "disponibilidade", false, "motivo", motivo),
                Instant.now()));
        if (callback != null) callback.accept(agente, progresso);
    }

    private String motivoInsuficiencia(DataJudInfo info) {
        if (info == null) return "Dados DataJud ausentes.";
        if (info.mensagem() != null && !info.mensagem().isBlank()) return info.mensagem();
        return "Processo não localizado na fonte externa DataJud/CNJ.";
    }

    private String extrairCnj(String texto) {
        if (texto == null || texto.isBlank()) return null;
        Matcher matcher = CNJ.matcher(texto);
        return matcher.find() ? matcher.group() : null;
    }

    public record ExternalValidationResult(
            String numeroProcesso,
            DataJudInfo dataJud,
            List<ExternalAgentResult> agentes,
            ExternalAgentResult reconciliacao,
            Instant concluidoEm,
            boolean semProcesso) {
        public ExternalValidationResult(String numeroProcesso, DataJudInfo dataJud,
                                        List<ExternalAgentResult> agentes,
                                        ExternalAgentResult reconciliacao, Instant concluidoEm) {
            this(numeroProcesso, dataJud, List.copyOf(agentes), reconciliacao, concluidoEm, false);
        }
        public static ExternalValidationResult semProcesso(String mensagem) {
            ExternalAgentResult result = new ExternalAgentResult(
                    "ExternalValidationTeam", "SEM_CNJ", mensagem, Map.of(), Instant.now());
            return new ExternalValidationResult(null, null, List.of(result), result, Instant.now(), true);
        }
    }
}
