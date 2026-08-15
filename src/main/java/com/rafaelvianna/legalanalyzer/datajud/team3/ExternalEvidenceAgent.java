package com.rafaelvianna.legalanalyzer.datajud.team3;

import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExternalEvidenceAgent {
    public ExternalValidationResult analisar(DataJudInfo info) {
        if (info == null) return ExternalValidationResult.of("ExternalEvidenceAgent", null, "ERRO", "Dados externos ausentes.", List.of());
        return ExternalValidationResult.of("ExternalEvidenceAgent", info.numeroProcesso(), info.status().name(),
                "Camada de evidência externa preparada. Nenhuma conclusão é marcada como confirmada sem comparação com contexto interno.",
                List.of("movimentosExternos=" + info.movimentos().size(), "consultadoEm=" + info.consultadoEm()));
    }
}
