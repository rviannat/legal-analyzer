package com.rafaelvianna.legalanalyzer.web.dto.specialized;

import java.util.List;

/**
 * Resultado do Contract Agent: cláusulas de risco, obrigações, multas,
 * prazos, condições e inconsistências do contrato analisado.
 */
public record AnaliseContratualDTO(
        boolean contratoIdentificado,
        String objetoContrato,
        String partesContratantes,
        List<ClausulaRiscoDTO> clausulasRisco,
        List<ObrigacaoDTO> obrigacoes,
        List<MultaDTO> multas,
        List<PrazoContratualDTO> prazos,
        List<CondicaoDTO> condicoes,
        List<InconsistenciaContratualDTO> inconsistencias,
        String observacoes
) {
    public static AnaliseContratualDTO naoAplicavel(String motivo) {
        return new AnaliseContratualDTO(false, "não identificado", "não identificado",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), motivo);
    }
}
