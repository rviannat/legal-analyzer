package com.rafaelvianna.legalanalyzer.analysis;

/**
 * Recebe o avanço da consolidação map-reduce dos resultados parciais.
 *
 * <p>Sem isso, a consolidação de um documento grande executa dezenas de
 * chamadas de IA sem reportar nada, e a barra de progresso fica congelada
 * no último valor publicado antes dela (55%) por dezenas de minutos.
 *
 * <p>O total de passos é conhecido de antemão: consolidar {@code n} blocos
 * parciais em 1 exige exatamente {@code n - 1} fusões, independentemente de
 * como os lotes são formados. Cada lote de {@code k} itens realiza
 * {@code k - 1} fusões, então o progresso é monotônico e chega sempre a 100%.
 */
@FunctionalInterface
public interface ConsolidationProgressListener {

    /**
     * @param fusoesConcluidas quantas fusões já foram feitas
     * @param fusoesTotais     total de fusões necessárias ({@code n - 1})
     * @param detalhe          texto curto para exibir ao usuário
     */
    void onProgresso(int fusoesConcluidas, int fusoesTotais, String detalhe);

    static ConsolidationProgressListener noop() {
        return (fusoesConcluidas, fusoesTotais, detalhe) -> {
        };
    }
}
