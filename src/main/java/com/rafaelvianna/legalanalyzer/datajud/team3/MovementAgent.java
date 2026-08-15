package com.rafaelvianna.legalanalyzer.datajud.team3;

import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import com.rafaelvianna.legalanalyzer.datajud.DataJudMovimento;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MovementAgent {
    public ExternalValidationResult analisar(DataJudInfo info) {
        if (info == null) return ExternalValidationResult.of("MovementAgent", null, "ERRO", "Dados externos ausentes.", List.of());
        List<String> findings = info.movimentos().stream().map(this::formatar).toList();
        return ExternalValidationResult.of("MovementAgent", info.numeroProcesso(), info.status().name(),
                "Movimentações externas normalizadas: " + findings.size(), findings);
    }

    private String formatar(DataJudMovimento movimento) {
        return (movimento.dataHora() == null ? "sem data" : movimento.dataHora()) + " — "
                + (movimento.nome() == null ? "evento sem nome" : movimento.nome())
                + (movimento.complemento() == null ? "" : " — " + movimento.complemento());
    }
}
