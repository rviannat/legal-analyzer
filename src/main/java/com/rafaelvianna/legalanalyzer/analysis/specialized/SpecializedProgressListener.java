package com.rafaelvianna.legalanalyzer.analysis.specialized;

import com.rafaelvianna.legalanalyzer.async.AnaliseEspecializadaStatus;

/** Recebe o progresso da análise especializada (análogo ao AnalysisProgressListener). */
@FunctionalInterface
public interface SpecializedProgressListener {

    void update(AnaliseEspecializadaStatus status, int progresso, String etapa, String mensagem);

    static SpecializedProgressListener noop() {
        return (status, progresso, etapa, mensagem) -> {};
    }
}
