package com.rafaelvianna.legalanalyzer.web.dto.specialized;

import java.util.List;

/** Resultado do Evidence Agent: matriz alegação x documentos de suporte. */
public record MatrizEvidenciasDTO(
        List<AlegacaoEvidenciaDTO> alegacoes,
        List<String> lacunasProbatorias,
        List<String> provasSugeridas,
        String observacoes
) {
}
