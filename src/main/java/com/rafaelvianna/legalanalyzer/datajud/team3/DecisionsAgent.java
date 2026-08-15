package com.rafaelvianna.legalanalyzer.datajud.team3;

import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DecisionsAgent {
    public ExternalValidationResult analisar(DataJudInfo info) {
        String process = info == null ? null : info.numeroProcesso();
        String status = info == null ? "ERRO" : info.status().name();
        return ExternalValidationResult.of("DecisionsAgent", process, status,
                "Validação de decisões preparada; decisões serão classificadas quando o payload externo correspondente estiver disponível.",
                List.of());
    }
}
