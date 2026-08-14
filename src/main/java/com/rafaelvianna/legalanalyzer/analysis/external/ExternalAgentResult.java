package com.rafaelvianna.legalanalyzer.analysis.external;

import java.time.Instant;
import java.util.Map;

/** Resultado padronizado dos agentes da Equipe 3. */
public record ExternalAgentResult(
        String agent,
        String status,
        String summary,
        Map<String, Object> data,
        Instant generatedAt) {
    public ExternalAgentResult {
        data = data == null ? Map.of() : Map.copyOf(data);
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
    }
}
