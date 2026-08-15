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
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Orquestra a Equipe 3. A consulta oficial é feita uma única vez e os resultados são reconciliados. */
@Service
public class ExternalValidationTeam {
    private static final Logger log = LoggerFactory.getLogger(ExternalValidationTeam.class);
    private static final Pattern CNJ = Pattern.compile("\\b\\d{7}-\\d{2}\\.\\d{4}\\.\\d{1,2}\\.\\d{2}\\.\\d{4}\\b");

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

    /** Executa a equipe e notifica o progresso depois de cada agente. */
    public ExternalValidationResult execute(String textoCompleto, ExtractionResult contextoInterno,
                                            BiConsumer<String, Integer> progresso) {
        String numeroProcesso = extrairCnj(textoCompleto);
        if (numeroProcesso == null) {
            log.info("[EQUIPE 3] CNJ não identificado; validação externa não iniciada.");
            return ExternalValidationResult.semProcesso("CNJ não identificado no documento.");
        }

        log.info("[EQUIPE 3] Iniciando validação externa do processo {}", numeroProcesso);
        DataJudInfo info = dataJudService.consultar(numeroProcesso);
        List<ExternalAgentResult> resultados = new ArrayList<>();

        executar("ProcessSearchAgent", 12, () -> processSearchAgent.execute(info), resultados, progresso);
        executar("MovementAgent", 24, () -> movementAgent.execute(info), resultados, progresso);
        executar("PartiesAgent", 36, () -> partiesAgent.execute(info), resultados, progresso);
        executar("DecisionsAgent", 48, () -> decisionsAgent.execute(info), resultados, progresso);
        executar("CourtAgent", 60, () -> courtAgent.execute(info), resultados, progresso);
        executar("TimelineAgent", 72, () -> timelineAgent.execute(info), resultados, progresso);
        executar("ExternalEvidenceAgent", 84, () -> externalEvidenceAgent.execute(info, contextoInterno), resultados, progresso);

        ExternalAgentResult reconciliation = reconciliationAgent.execute(resultados, contextoInterno, info);
        resultados.add(reconciliation);
        if (progresso != null) progresso.accept("JusReconciliationAgent", 100);

        log.info("[EQUIPE 3] Validação concluída: status={}, agentes={}", reconciliation.status(), resultados.size());
        return new ExternalValidationResult(numeroProcesso, info, resultados, reconciliation, Instant.now());
    }

    private void executar(String agente, int progresso, java.util.function.Supplier<ExternalAgentResult> acao,
                          List<ExternalAgentResult> resultados, BiConsumer<String, Integer> callback) {
        log.info("[EQUIPE 3][AGENTE:{}] iniciando", agente);
        ExternalAgentResult resultado = acao.get();
        resultados.add(resultado);
        log.info("[EQUIPE 3][AGENTE:{}] concluído | status={} | {}", agente, resultado.status(), resultado.summary());
        if (callback != null) callback.accept(agente, progresso);
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
                    "ExternalValidationTeam", "SEM_CNJ", mensagem, java.util.Map.of(), Instant.now());
            return new ExternalValidationResult(null, null, List.of(result), result, Instant.now(), true);
        }
    }
}
