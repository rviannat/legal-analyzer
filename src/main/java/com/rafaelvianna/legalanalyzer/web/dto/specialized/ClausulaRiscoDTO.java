package com.rafaelvianna.legalanalyzer.web.dto.specialized;

/** Cláusula contratual que representa risco para a parte analisada. */
public record ClausulaRiscoDTO(
        String clausula,
        String trechoCitado,
        String risco,
        String gravidade,
        String impacto,
        String recomendacao
) {
}
