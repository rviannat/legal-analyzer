package com.rafaelvianna.legalanalyzer.datajud.team3;

import java.util.List;

/** Compatibilidade temporária para o job assíncrono legado. */
public record ExternalValidationResult(String agent, String status, String summary, List<String> findings) {
    public ExternalValidationResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
