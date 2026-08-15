package com.rafaelvianna.legalanalyzer.datajud.team3;

import java.time.Instant;
import java.util.List;

public record ExternalValidationResult(
        String agent,
        String processNumber,
        String status,
        String summary,
        List<String> findings,
        Instant executedAt) {
    public ExternalValidationResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
        executedAt = executedAt == null ? Instant.now() : executedAt;
    }

    public static ExternalValidationResult of(String agent, String processNumber, String status,
                                               String summary, List<String> findings) {
        return new ExternalValidationResult(agent, processNumber, status, summary, findings, Instant.now());
    }
}
