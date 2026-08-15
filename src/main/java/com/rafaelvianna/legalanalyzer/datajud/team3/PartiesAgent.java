package com.rafaelvianna.legalanalyzer.datajud.team3;

import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PartiesAgent {
    public ExternalValidationResult analisar(DataJudInfo info) {
        String process = info == null ? null : info.numeroProcesso();
        String status = info == null ? "ERRO" : info.status().name();
        return ExternalValidationResult.of("PartiesAgent", process, status,
                "Validação de partes preparada; o endpoint de partes ainda não está exposto pelo DataJudService atual.",
                List.of("Nenhuma parte foi inferida a partir de campos que não existem no contrato atual."));
    }
}
