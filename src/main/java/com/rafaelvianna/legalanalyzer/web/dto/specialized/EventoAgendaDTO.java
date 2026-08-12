package com.rafaelvianna.legalanalyzer.web.dto.specialized;

/** Evento datado relevante para a agenda do advogado (audiência, perícia, vencimento...). */
public record EventoAgendaDTO(
        String data,
        String evento,
        String tipo,
        String comparecimentoObrigatorio,
        String observacoes
) {
}
