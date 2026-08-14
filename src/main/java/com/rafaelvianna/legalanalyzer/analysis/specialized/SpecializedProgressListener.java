package com.rafaelvianna.legalanalyzer.analysis.specialized;

import com.rafaelvianna.legalanalyzer.async.AnaliseEspecializadaStatus;

import java.util.List;

/** Recebe telemetria detalhada da análise especializada. */
@FunctionalInterface
public interface SpecializedProgressListener {
    void update(AnaliseEspecializadaStatus status, int progresso, String etapa, String mensagem);

    /** Evento rico: o listener legado continua funcionando, enquanto o job pode persistir telemetria detalhada. */
    default void updateRich(AnaliseEspecializadaStatus status, int progresso, String agente, int agenteNumero,
                            int totalAgentes, String acao, String mensagem, List<String> contextoRecebido,
                            String resultadoParcial) {
        update(status, progresso, agente + " — " + acao, mensagem);
    }

    static SpecializedProgressListener noop() { return (status, progresso, etapa, mensagem) -> {}; }
}
