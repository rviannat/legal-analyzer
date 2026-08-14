package com.rafaelvianna.legalanalyzer.analysis.specialized;

import com.rafaelvianna.legalanalyzer.async.AnaliseEspecializadaStatus;

import java.util.List;

/** Recebe telemetria detalhada da análise especializada. */
@FunctionalInterface
public interface SpecializedProgressListener {
    void update(AnaliseEspecializadaStatus status, int progresso, String etapa, String mensagem);

    /** Compatibilidade com listeners existentes, mantendo a telemetria rica no log persistido. */
    default void updateRich(AnaliseEspecializadaStatus status, int progresso, String agente, int agenteNumero,
                            int totalAgentes, String acao, String mensagem, List<String> contextoRecebido,
                            String resultadoParcial) {
        String contexto = contextoRecebido == null || contextoRecebido.isEmpty() ? "nenhum" : String.join(" -> ", contextoRecebido);
        String resultado = resultadoParcial == null || resultadoParcial.isBlank() ? "em processamento" : resultadoParcial;
        update(status, progresso, agente + " — " + acao,
                mensagem + " | contexto recebido: " + contexto + " | resultado: " + resultado + " | agente " + agenteNumero + "/" + totalAgentes);
    }

    static SpecializedProgressListener noop() { return (status, progresso, etapa, mensagem) -> {}; }
}
