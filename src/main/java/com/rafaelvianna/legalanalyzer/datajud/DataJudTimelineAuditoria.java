package com.rafaelvianna.legalanalyzer.datajud;

import java.util.List;

/** Resultado da sincronização entre a cronologia extraída do PDF e as movimentações oficiais. */
public record DataJudTimelineAuditoria(
        DataJudStatus status,
        int movimentacoesOficiais,
        int eventosPdf,
        int correspondencias,
        int movimentacoesOcultas,
        List<DataJudMovimento> movimentacoesOficiaisDetalhadas,
        List<DataJudTimelineEvento> linhaDoTempoHibrida,
        List<DataJudTimelineEvento> alertasMovimentacoesOcultas,
        String dataPublicacaoOficial,
        String dataTransitoEmJulgadoOficial,
        String observacao
) {
    public static DataJudTimelineAuditoria indisponivel(DataJudInfo info) {
        return new DataJudTimelineAuditoria(
                info == null ? DataJudStatus.INDISPONIVEL : info.status(), 0, 0, 0, 0,
                List.of(), List.of(), List.of(), null, null,
                "A sincronização não foi executada porque não há movimentações públicas do DataJud disponíveis.");
    }
}
