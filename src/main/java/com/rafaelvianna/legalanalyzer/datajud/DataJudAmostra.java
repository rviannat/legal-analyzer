package com.rafaelvianna.legalanalyzer.datajud;

import java.util.List;

/** Amostra de processo retornada por uma pesquisa agregada no índice público do DataJud. */
public record DataJudAmostra(
        String numeroProcesso,
        String classeCodigo,
        String classeNome,
        List<String> assuntos,
        String grau,
        String orgaoJulgador,
        String dataAjuizamento,
        String ultimaMovimentacao,
        boolean possuiMovimentoDeBaixa
) {
    public DataJudAmostra {
        assuntos = assuntos == null ? List.of() : List.copyOf(assuntos);
    }
}
