package com.rafaelvianna.legalanalyzer.web.dto.specialized;

import java.util.List;

/**
 * Informa ao cliente que a análise especializada está disponível para uma
 * análise base já concluída, com o endpoint e as opções aceitas.
 */
public record OpcaoAnaliseEspecializadaDTO(
        boolean disponivel,
        String endpoint,
        List<String> agentesDisponiveis,
        List<TipoRascunho> tiposRascunho,
        boolean pesquisaJuridicaHabilitada,
        String observacao
) {
    public static OpcaoAnaliseEspecializadaDTO indisponivel(String motivo) {
        return new OpcaoAnaliseEspecializadaDTO(false, null, List.of(), List.of(), false, motivo);
    }
}
