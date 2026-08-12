package com.rafaelvianna.legalanalyzer.web.dto;

/** Um prazo ou data processualmente relevante. */
public record PrazoDTO(
        String data,
        String descricaoPrazo,
        String criticidade,
        String parteResponsavel
) {
}
