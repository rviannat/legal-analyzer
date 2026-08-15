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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orquestra a Equipe 3. A consulta oficial é feita uma única vez e o mesmo
 * DataJudInfo é distribuído aos especialistas. A reunião final recebe também
 * o resultado consolidado da Equipe 1 para procurar divergências reais.
 */
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
        String numeroProcesso = extrairCnj(textoCompleto);
        if (numeroProcesso == null) {
            log.info("[EQUIPE 3] CNJ não identificado; validação externa não iniciada.");
            return ExternalValidationResult.semProcesso("CNJ não identificado no documento.");
        }

        log.info("[EQUIPE 3] Iniciando validação externa do processo {}", numeroProcesso);
        DataJudInfo info = dataJudService.consultar(numeroProcesso);
        List<ExternalAgentResult> resultados = new ArrayList<>();

        resultados.add(processSearchAgent.execute(info));
        resultados.add(movementAgent.execute(info));
        resultados.add(partiesAgent.execute(info));
        resultados.add(decisionsAgent.execute(info));
        resultados.add(courtAgent.execute(info));
        resultados.add(timelineAgent.execute(info));
        resultados.add(externalEvidenceAgent.execute(info, contextoInterno));

        ExternalAgentResult reconciliation = reconciliationAgent.execute(resultados, contextoInterno, info);
        resultados.add(reconciliation);

        log.info("[EQUIPE 3] Validação concluída: status={}, divergências={}, confirmações={}",
                reconciliation.status(),
                reconciliation.data().getOrDefault("divergencias", List.of()).toString().split("severidade").length - 1,
                reconciliation.data().getOrDefault("confirmacoes", List.of()).toString().split("tipo").length - 1);

        return new ExternalValidationResult(numeroProcesso, info, resultados, reconciliation, Instant.now());
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
