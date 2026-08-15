package com.rafaelvianna.legalanalyzer.datajud.team3;

import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class TimelineAgent {
    public ExternalValidationResult analisar(DataJudInfo info) {
        if (info == null) return ExternalValidationResult.of("TimelineAgent", null, "ERRO", "Dados externos ausentes.", List.of());
        List<String> timeline = info.movimentos().stream()
                .sorted(Comparator.comparing(m -> m.dataInstant() == null ? java.time.Instant.MIN : m.dataInstant()))
                .map(m -> m.textoComparacao())
                .toList();
        return ExternalValidationResult.of("TimelineAgent", info.numeroProcesso(), info.status().name(),
                "Linha do tempo externa normalizada com " + timeline.size() + " eventos.", timeline);
    }
}
