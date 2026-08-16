package com.rafaelvianna.legalanalyzer.web.dto;

import java.util.List;

/** Uma inconsistência, contradição ou lacuna identificada no processo. */
public record InconsistenciaDTO(
        String descricao,
        List<String> elementosConflitantes,
        String gravidade,
        String recomendacao
) {
}
