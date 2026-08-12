package com.rafaelvianna.legalanalyzer.web.dto.specialized;

/** Multa/penalidade prevista no contrato. */
public record MultaDTO(
        String clausula,
        String hipoteseIncidencia,
        String valorOuPercentual,
        String parteResponsavel,
        String cumulatividade
) {
}
