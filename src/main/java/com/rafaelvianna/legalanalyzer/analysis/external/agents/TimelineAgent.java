package com.rafaelvianna.legalanalyzer.analysis.external.agents;

import com.rafaelvianna.legalanalyzer.analysis.external.ExternalAgentResult;
import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class TimelineAgent {
    public ExternalAgentResult execute(DataJudInfo info) {
        return new ExternalAgentResult("TimelineAgent", info.status().name(),
                "Linha do tempo externa preparada para reconciliação com os eventos encontrados nos documentos.",
                Map.of("movimentos", info.movimentos(), "quantidade", info.quantidadeMovimentos() == null ? 0 : info.quantidadeMovimentos()), Instant.now());
    }
}
