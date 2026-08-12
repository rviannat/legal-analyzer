package com.rafaelvianna.legalanalyzer.web.dto.specialized;

/** Prazo processual detalhado, com base de contagem e fundamento. */
public record PrazoDetalhadoDTO(
        String descricao,
        String dataInicio,
        String dataFinal,
        String prazoEmDias,
        String tipoContagem,
        String fundamento,
        String criticidade,
        String parteResponsavel
) {
}
