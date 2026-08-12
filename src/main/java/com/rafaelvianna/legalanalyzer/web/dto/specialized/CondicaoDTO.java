package com.rafaelvianna.legalanalyzer.web.dto.specialized;

/** Condição contratual (suspensiva, resolutiva, termo ou outra). */
public record CondicaoDTO(
        String clausula,
        String tipo,
        String descricao,
        String efeito
) {
}
