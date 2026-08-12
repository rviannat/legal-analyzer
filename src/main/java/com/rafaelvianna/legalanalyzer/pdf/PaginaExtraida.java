package com.rafaelvianna.legalanalyzer.pdf;

/**
 * Texto de uma página do PDF, com o número da página preservado.
 *
 * O número da página é o que permite ao briefing e ao chat citarem
 * "Documento X — página 42" de forma verificável pelo advogado.
 *
 * @param numero número da página (1-based, como o advogado vê no leitor de PDF)
 * @param texto  texto normalizado da página
 */
public record PaginaExtraida(int numero, String texto) {
}
