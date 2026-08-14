package com.rafaelvianna.legalanalyzer.analysis.external.agents;

import com.rafaelvianna.legalanalyzer.analysis.external.ExternalAgentResult;
import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/** Especialista em decisões. Será conectado aos endpoints específicos após o mapeamento da API. */
@Component
public class DecisionsAgent {
    public ExternalAgentResult execute(DataJudInfo info) {
        return new ExternalAgentResult("DecisionsAgent", "READY", "Agente preparado para identificação e confronto de decisões externas.",
                Map.of("processo", info.numeroProcesso(), "tribunal", info.tribunal() == null ? "" : info.tribunal()), Instant.now());
    }
}
