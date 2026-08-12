package com.rafaelvianna.legalanalyzer.web.dto;

/** Uma parte identificada no processo (autor, réu, terceiro interessado, etc.). */
public record ParteDTO(
        String nome,
        String papel,
        String qualificacao,
        String observacoes
) {
}
