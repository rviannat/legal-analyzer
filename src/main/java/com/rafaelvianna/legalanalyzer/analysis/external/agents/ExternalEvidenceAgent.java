package com.rafaelvianna.legalanalyzer.analysis.external.agents;

import com.rafaelvianna.legalanalyzer.analysis.external.ExternalAgentResult;
import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/** Compara futuramente evidências externas com o contexto produzido pelas Equipes 1 e 2. */
@Component
public class ExternalEvidenceAgent {
    public ExternalAgentResult execute(DataJudInfo info, Object internalContext) {
        return new ExternalAgentResult("ExternalEvidenceAgent", info.status().name(),
                "Camada preparada para confronto de evidências externas com o contexto interno.",
                Map.of("processo", info.numeroProcesso(), "contextoInternoRecebido", internalContext != null), Instant.now());
    }
}
