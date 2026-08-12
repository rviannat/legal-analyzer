package com.rafaelvianna.legalanalyzer.web.dto;

/** Um evento da linha do tempo processual. */
public record EventoCronologiaDTO(
        String data,
        String descricaoEvento,
        String fase
) {
}
