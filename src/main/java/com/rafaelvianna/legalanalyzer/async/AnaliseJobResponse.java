package com.rafaelvianna.legalanalyzer.async;

import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;

import java.time.Instant;

public record AnaliseJobResponse(
        String id,
        String nomeArquivo,
        AnaliseStatus status,
        int progresso,
        String etapa,
        String mensagem,
        Instant criadoEm,
        Instant atualizadoEm,
        AnaliseProcessoResponse resultado
) {
    public static AnaliseJobResponse status(AnaliseJob job) {
        return new AnaliseJobResponse(
                job.id(), job.nomeArquivo(), job.status(), job.progresso(), job.etapa(),
                job.mensagem(), job.criadoEm(), job.atualizadoEm(), job.resultado());
    }
}
