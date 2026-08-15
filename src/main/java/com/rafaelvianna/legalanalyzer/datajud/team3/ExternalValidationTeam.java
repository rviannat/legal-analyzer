package com.rafaelvianna.legalanalyzer.datajud.team3;

import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExternalValidationTeam {
    private final ProcessSearchAgent processSearchAgent;
    private final MovementAgent movementAgent;
    private final PartiesAgent partiesAgent;
    private final DecisionsAgent decisionsAgent;
    private final CourtAgent courtAgent;
    private final TimelineAgent timelineAgent;
    private final ExternalEvidenceAgent externalEvidenceAgent;
    private final JusReconciliationAgent reconciliationAgent;

    public ExternalValidationTeam(ProcessSearchAgent processSearchAgent, MovementAgent movementAgent,
                                  PartiesAgent partiesAgent, DecisionsAgent decisionsAgent,
                                  CourtAgent courtAgent, TimelineAgent timelineAgent,
                                  ExternalEvidenceAgent externalEvidenceAgent,
                                  JusReconciliationAgent reconciliationAgent) {
        this.processSearchAgent = processSearchAgent;
        this.movementAgent = movementAgent;
        this.partiesAgent = partiesAgent;
        this.decisionsAgent = decisionsAgent;
        this.courtAgent = courtAgent;
        this.timelineAgent = timelineAgent;
        this.externalEvidenceAgent = externalEvidenceAgent;
        this.reconciliationAgent = reconciliationAgent;
    }

    public List<ExternalValidationResult> executar(String numeroProcesso) {
        DataJudInfo info = processSearchAgent.consultar(numeroProcesso);
        return executar(info);
    }

    public List<ExternalValidationResult> executar(DataJudInfo info) {
        ExternalValidationResult process = processSearchAgent.analisar(info);
        ExternalValidationResult movement = movementAgent.analisar(info);
        ExternalValidationResult parties = partiesAgent.analisar(info);
        ExternalValidationResult decisions = decisionsAgent.analisar(info);
        ExternalValidationResult court = courtAgent.analisar(info);
        ExternalValidationResult timeline = timelineAgent.analisar(info);
        ExternalValidationResult evidence = externalEvidenceAgent.analisar(info);
        List<ExternalValidationResult> results = List.of(process, movement, parties, decisions, court, timeline, evidence);
        ExternalValidationResult reconciliation = reconciliationAgent.reconciliar(info, results);
        return java.util.stream.Stream.concat(results.stream(), java.util.stream.Stream.of(reconciliation)).toList();
    }
}
