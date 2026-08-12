package com.rafaelvianna.legalanalyzer.web.dto.specialized;

/** Obrigação assumida por uma das partes no contrato. */
public record ObrigacaoDTO(
        String parteObrigada,
        String descricao,
        String prazoCumprimento,
        String clausula,
        String consequenciaDescumprimento
) {
}
