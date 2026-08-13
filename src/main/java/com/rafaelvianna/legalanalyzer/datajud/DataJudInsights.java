package com.rafaelvianna.legalanalyzer.datajud;

import java.time.Instant;

/** Insights estatísticos do processo. Nunca infere uma taxa quando não existe fonte estatística configurada. */
public record DataJudInsights(
        Status status,
        String tribunal,
        String classeProcessualCodigo,
        String classeProcessual,
        String orgaoJulgadorCodigo,
        String orgaoJulgador,
        Integer idadeDias,
        Integer duracaoMediaDias,
        Integer percentualDuracao,
        Double probabilidadeAcordo,
        Double probabilidadePericia,
        Double congestionamento,
        String nivelCongestionamento,
        String fonte,
        String mensagem,
        Instant consultadoEm) {
    public enum Status { DISPONIVEL, PARCIAL, NAO_DISPONIVEL }

    public static DataJudInsights indisponivel(DataJudInfo info, String mensagem) {
        return new DataJudInsights(Status.NAO_DISPONIVEL, info.tribunal(), null, info.classeProcessual(), null,
                info.orgaoJulgador(), null, null, null, null, null, null, null, null, mensagem, Instant.now());
    }
}
