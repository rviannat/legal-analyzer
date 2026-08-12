package com.rafaelvianna.legalanalyzer.web.dto.specialized;

import java.util.List;

/** Contradição, lacuna ou ambiguidade identificada no contrato. */
public record InconsistenciaContratualDTO(
        String descricao,
        List<String> clausulasEnvolvidas,
        String gravidade,
        String sugestaoCorrecao
) {
}
