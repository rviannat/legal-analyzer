package com.rafaelvianna.legalanalyzer.datajud.team3;

import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import com.rafaelvianna.legalanalyzer.datajud.DataJudService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProcessSearchAgent {
    private final DataJudService dataJudService;

    public ProcessSearchAgent(DataJudService dataJudService) {
        this.dataJudService = dataJudService;
    }

    public DataJudInfo consultar(String numeroProcesso) {
        return dataJudService.consultar(numeroProcesso);
    }

    public ExternalValidationResult analisar(DataJudInfo info) {
        if (info == null) return ExternalValidationResult.of("ProcessSearchAgent", null, "ERRO", "Resultado DataJud ausente.", List.of());
        return ExternalValidationResult.of("ProcessSearchAgent", info.numeroProcesso(), info.status().name(),
                info.mensagem(), List.of("tribunal=" + info.tribunal(), "classe=" + info.classeProcessual(),
                        "órgão julgador=" + info.orgaoJulgador(), "grau=" + info.grau()));
    }
}
