package com.rafaelvianna.legalanalyzer.web.dto.specialized;

/** Um documento identificado no material analisado, com sua classificação. */
public record DocumentoClassificadoDTO(
        String nomeDocumento,
        String categoria,
        String subtipo,
        String dataDocumento,
        String confianca,
        String justificativa
) {
}
