package com.rafaelvianna.legalanalyzer.analysis;

import com.rafaelvianna.legalanalyzer.async.AnaliseStatus;

@FunctionalInterface
public interface AnalysisProgressListener {
    void update(AnaliseStatus status, int progresso, String etapa, String mensagem);

    static AnalysisProgressListener noop() {
        return (status, progresso, etapa, mensagem) -> {};
    }
}
