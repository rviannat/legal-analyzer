package com.rafaelvianna.legalanalyzer.rag;

/**
 * Passagem devolvida pela busca, com a pontuação de relevância e a origem
 * do casamento (semântico, léxico ou híbrido) — útil para depurar respostas.
 */
public record PassagemRecuperada(Passagem passagem, double score, String estrategia) {
}
