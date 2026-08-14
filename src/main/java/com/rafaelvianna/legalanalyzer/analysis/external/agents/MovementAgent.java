package com.rafaelvianna.legalanalyzer.analysis.external.agents;

import com.rafaelvianna.legalanalyzer.analysis.external.ExternalAgentResult;
import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class MovementAgent {
    public ExternalAgentResult execute(DataJudInfo info) {
        return new ExternalAgentResult("MovementAgent", info.status().name(),
                "Movimentações externas normalizadas para análise da linha do tempo.",
                Map.of("quantidadeMovimentos", info.quantidadeMovimentos() == null ? 0 : info.quantidadeMovimentos(),
                        "ultimaMovimentacao", info.ultimaMovimentacao() == null ? "" : info.ultimaMovimentacao(),
                        "movimentos", info.movimentos()), Instant.now());
    }
}
