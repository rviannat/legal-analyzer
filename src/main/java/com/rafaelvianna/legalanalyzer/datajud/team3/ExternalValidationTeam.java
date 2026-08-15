package com.rafaelvianna.legalanalyzer.datajud.team3;

import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.BiConsumer;

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
        return executar(numeroProcesso, (agente, progresso) -> { });
    }

    public List<ExternalValidationResult> executar(String numeroProcesso, BiConsumer<String, Integer> progresso) {
        DataJudInfo info = processSearchAgent.consultar(numeroProcesso);
        return executar(info, progresso);
    }

    public List<ExternalValidationResult> executar(DataJudInfo info) {
        return executar(info, (agente, progresso) -> { });
    }

    public List<ExternalValidationResult> executar(DataJudInfo info, BiConsumer<String, Integer> progresso) {
        progresso.accept("ProcessSearchAgent", 10);
        ExternalValidationResult process = processSearchAgent.analisar(info);
        progresso.accept("MovementAgent", 22);
        ExternalValidationResult movement = movementAgent.analisar(info);
        progresso.accept("PartiesAgent", 34);
        ExternalValidationResult parties = partiesAgent.analisar(info);
        progresso.accept("DecisionsAgent", 46);
        ExternalValidationResult decisions = decisionsAgent.analisar(info);
        progresso.accept("CourtAgent", 58);
        ExternalValidationResult court = courtAgent.analisar(info);
        progresso.accept("TimelineAgent", 70);
        ExternalValidationResult timeline = timelineAgent.analisar(info);
        progresso.accept("ExternalEvidenceAgent", 82);
        ExternalValidationResult evidence = externalEvidenceAgent.analisar(info);
        List<ExternalValidationResult> results = List.of(process, movement, parties, decisions, court, timeline, evidence);
        progresso.accept("JusReconciliationAgent", 94);
        ExternalValidationResult reconciliation = reconciliationAgent.reconciliar(info, results);
        progresso.accept("JusReconciliationAgent", 100);
        return java.util.stream.Stream.concat(results.stream(), java.util.stream.Stream.of(reconciliation)).toList();
    }
}
