package com.rafaelvianna.legalanalyzer.web.dto;

/** Um documento relevante citado ou anexado ao processo. */
public record DocumentoImportanteDTO(
        String nomeDocumento,
        String tipo,
        String dataDocumento,
        String relevancia
) {
}
