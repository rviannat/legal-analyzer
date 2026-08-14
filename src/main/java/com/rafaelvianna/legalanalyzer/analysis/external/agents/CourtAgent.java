package com.rafaelvianna.legalanalyzer.analysis.external.agents;

import com.rafaelvianna.legalanalyzer.analysis.external.ExternalAgentResult;
import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class CourtAgent {
    public ExternalAgentResult execute(DataJudInfo info) {
        return new ExternalAgentResult("CourtAgent", info.status().name(), "Metadados oficiais do tribunal normalizados para validação.",
                Map.of("tribunal", info.tribunal() == null ? "" : info.tribunal(),
                        "orgaoJulgador", info.orgaoJulgador() == null ? "" : info.orgaoJulgador(),
                        "grau", info.grau() == null ? "" : info.grau(),
                        "classeProcessual", info.classeProcessual() == null ? "" : info.classeProcessual()), Instant.now());
    }
}
