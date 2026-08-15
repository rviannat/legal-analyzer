package com.rafaelvianna.legalanalyzer.datajud.team3;

import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JusReconciliationAgent {
    public ExternalValidationResult reconciliar(DataJudInfo info, List<ExternalValidationResult> resultados) {
        String process = info == null ? null : info.numeroProcesso();
        List<String> findings = new ArrayList<>();
        if (resultados != null) {
            resultados.stream().filter(r -> r != null).forEach(r -> findings.add(r.agent() + ": " + r.summary()));
        }
        if (info != null && info.status() != null) findings.add("statusDataJud=" + info.status().name());
        return ExternalValidationResult.of("JusReconciliationAgent", process,
                info == null || info.status() == null ? "ERRO" : info.status().name(),
                "Reconciliação inicial concluída sem inventar confirmações: divergências dependem do contexto interno das Equipes 1 e 2.", findings);
    }
}
