package com.rafaelvianna.legalanalyzer.web.dto.specialized;

/** Documento que pode sustentar (ou enfraquecer) uma alegação. */
public record DocumentoSuporteDTO(
        String nomeDocumento,
        String localizacao,
        String comoSustenta,
        String forcaProbatoria
) {
}
