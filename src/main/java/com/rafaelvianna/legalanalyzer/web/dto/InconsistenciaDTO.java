package com.rafaelvianna.legalanalyzer.web.dto;

/** Uma inconsistência, contradição ou lacuna identificada no processo. */
public record InconsistenciaDTO(
        String descricao,
        String elementosConflitantes,
        String gravidade,
        String recomendacao
) {
}
