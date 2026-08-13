package com.rafaelvianna.legalanalyzer.datajud;

import java.time.Instant;
import java.util.List;

public record DataJudInfo(
        DataJudStatus status,
        String numeroProcesso,
        String tribunal,
        String endpoint,
        boolean encontrado,
        Integer quantidadeMovimentos,
        String ultimaMovimentacao,
        String classeProcessual,
        String orgaoJulgador,
        String grau,
        String mensagem,
        Instant consultadoEm,
        List<DataJudMovimento> movimentos
) {
    public DataJudInfo {
        movimentos = movimentos == null ? List.of() : List.copyOf(movimentos);
    }

    public static DataJudInfo aguardando(String numeroProcesso) {
        return new DataJudInfo(DataJudStatus.AGUARDANDO, numeroProcesso, null, null, false, null, null, null, null, null,
                "Consulta DataJud será executada em paralelo à análise do documento.", null, List.of());
    }

    public static DataJudInfo naoConfigurado() {
        return new DataJudInfo(DataJudStatus.NAO_CONFIGURADO, "não identificado", null, null, false, null, null, null, null, null,
                "Integração DataJud não configurada.", null, List.of());
    }

    public static DataJudInfo numeroNaoIdentificado() {
        return new DataJudInfo(DataJudStatus.NUMERO_NAO_IDENTIFICADO, "não identificado", null, null, false, null, null, null, null, null,
                "Não foi possível identificar uma numeração CNJ no documento.", Instant.now(), List.of());
    }
}
