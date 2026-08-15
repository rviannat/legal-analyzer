package com.rafaelvianna.legalanalyzer.datajud.team3;

import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CourtAgent {
    public ExternalValidationResult analisar(DataJudInfo info) {
        if (info == null) return ExternalValidationResult.of("CourtAgent", null, "ERRO", "Dados externos ausentes.", List.of());
        List<String> findings = new ArrayList<>();
        if (info.tribunal() != null) findings.add("tribunal=" + info.tribunal());
        if (info.orgaoJulgador() != null) findings.add("órgão julgador=" + info.orgaoJulgador());
        if (info.grau() != null) findings.add("grau=" + info.grau());
        if (info.classeProcessual() != null) findings.add("classe=" + info.classeProcessual());
        return ExternalValidationResult.of("CourtAgent", info.numeroProcesso(), info.status().name(),
                "Metadados de tribunal e órgão julgador normalizados.", findings);
    }
}
