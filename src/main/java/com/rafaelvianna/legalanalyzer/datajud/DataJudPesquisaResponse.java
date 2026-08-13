package com.rafaelvianna.legalanalyzer.datajud;

import java.time.Instant;
import java.util.List;

/** Resultado de pesquisa direta ou agregada no DataJud. */
public record DataJudPesquisaResponse(
        String consultaId,
        String tipo,
        String tribunal,
        String assunto,
        boolean executada,
        String mensagem,
        Instant consultadoEm,
        DataJudInfo processo,
        List<DataJudAmostra> amostra,
        int totalAmostra
) {
    public DataJudPesquisaResponse {
        amostra = amostra == null ? List.of() : List.copyOf(amostra);
    }
}
