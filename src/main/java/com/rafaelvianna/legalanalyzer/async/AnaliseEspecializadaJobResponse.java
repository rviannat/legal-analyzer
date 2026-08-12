package com.rafaelvianna.legalanalyzer.async;

import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;

import java.time.Instant;

/** Status (e resultado, quando pronto) de uma análise especializada. */
public record AnaliseEspecializadaJobResponse(
        String id,
        String analiseBaseId,
        String nomeArquivo,
        AnaliseEspecializadaStatus status,
        int progresso,
        String etapa,
        String mensagem,
        Instant criadoEm,
        Instant atualizadoEm,
        AnaliseEspecializadaResponse resultado
) {
    public static AnaliseEspecializadaJobResponse status(AnaliseEspecializadaJob job) {
        return new AnaliseEspecializadaJobResponse(
                job.id(), job.analiseBaseId(), job.nomeArquivo(), job.status(), job.progresso(),
                job.etapa(), job.mensagem(), job.criadoEm(), job.atualizadoEm(), job.resultado());
    }
}
