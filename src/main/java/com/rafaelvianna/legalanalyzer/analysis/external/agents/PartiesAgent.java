package com.rafaelvianna.legalanalyzer.analysis.external.agents;

import com.rafaelvianna.legalanalyzer.analysis.external.ExternalAgentResult;
import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/** Especialista em partes. O contrato está preparado para endpoints de partes quando forem habilitados. */
@Component
public class PartiesAgent {
    public ExternalAgentResult execute(DataJudInfo info) {
        return new ExternalAgentResult("PartiesAgent", "READY", "Agente preparado para validação externa de partes e representantes.",
                Map.of("processo", info.numeroProcesso(), "fonte", info.endpoint() == null ? "" : info.endpoint()), Instant.now());
    }
}
