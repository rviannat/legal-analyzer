package com.rafaelvianna.legalanalyzer.rag;

/**
 * Uma passagem indexada do caso — a unidade de recuperação do RAG.
 *
 * Há dois tipos de passagem:
 * <ul>
 *   <li>{@link Tipo#TEXTO_PROCESSO}: trecho literal do PDF, com número de página;</li>
 *   <li>{@link Tipo#FICHA_ANALISE}: fato já estruturado pelos agentes (parte,
 *       evento da cronologia, pedido, decisão, prazo, inconsistência...).</li>
 * </ul>
 *
 * Toda resposta do chat precisa se apoiar em passagens — é isso que permite
 * dizer ao advogado onde conferir cada afirmação.
 *
 * @param id       identificador estável dentro do índice (ex.: "p42#1")
 * @param tipo     origem da passagem
 * @param rotulo   como citar a passagem (ex.: "Página 42" ou "Cronologia")
 * @param pagina   página do PDF; {@code null} para fichas da análise
 * @param texto    conteúdo da passagem
 * @param vetor    embedding, quando disponível; {@code null} no modo léxico
 */
public record Passagem(
        String id,
        Tipo tipo,
        String rotulo,
        Integer pagina,
        String texto,
        float[] vetor
) {
    public enum Tipo {
        TEXTO_PROCESSO,
        FICHA_ANALISE
    }

    public Passagem comVetor(float[] novoVetor) {
        return new Passagem(id, tipo, rotulo, pagina, texto, novoVetor);
    }

    /** Citação legível para o advogado (o que aparece na resposta do chat). */
    public String citacao() {
        return pagina == null ? rotulo : rotulo + " (página " + pagina + ")";
    }
}
