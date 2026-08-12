package com.rafaelvianna.legalanalyzer.rag;

import java.util.List;

/**
 * Gera embeddings para a busca semântica do RAG.
 *
 * Implementações devem ser tolerantes a falha: se o modelo de embeddings não
 * estiver disponível, o índice cai automaticamente para busca léxica em vez
 * de derrubar a análise.
 */
public interface EmbeddingClient {

    /** Indica se a busca semântica está habilitada e utilizável. */
    boolean disponivel();

    /** Gera o vetor de um texto; devolve {@code null} se não for possível. */
    float[] embed(String texto);

    /** Gera vetores em lote, mantendo a ordem da entrada. */
    List<float[]> embedLote(List<String> textos);
}
