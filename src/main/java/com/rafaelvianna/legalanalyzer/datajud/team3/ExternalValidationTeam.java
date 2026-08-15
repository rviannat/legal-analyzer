package com.rafaelvianna.legalanalyzer.datajud.team3;

import com.rafaelvianna.legalanalyzer.analysis.external.ExternalAgentResult;
import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Fachada de compatibilidade para código legado. A implementação real fica em
 * analysis.external.ExternalValidationTeam; esta classe não possui agentes próprios.
 */
@Service
public class ExternalValidationTeam {
    private final com.rafaelvianna.legalanalyzer.analysis.external.ExternalValidationTeam delegate;

    public ExternalValidationTeam(com.rafaelvianna.legalanalyzer.analysis.external.ExternalValidationTeam delegate) {
        this.delegate = delegate;
    }

    public List<ExternalValidationResult> executar(DataJudInfo info, BiConsumer<String, Integer> progresso) {
        var result = delegate.execute(info, null, progresso);
        return result.agentes().stream().map(this::adaptar).toList();
    }

    public List<ExternalValidationResult> executar(DataJudInfo info) {
        return executar(info, (agente, progresso) -> { });
    }

    private ExternalValidationResult adaptar(ExternalAgentResult result) {
        Object achados = result.data().get("findings");
        List<String> findings;
        if (achados instanceof List<?> lista) {
            findings = lista.stream().map(String::valueOf).toList();
        } else {
            findings = List.of(result.summary());
        }
        return new ExternalValidationResult(result.agent(), result.status(), result.summary(), findings);
    }
}
