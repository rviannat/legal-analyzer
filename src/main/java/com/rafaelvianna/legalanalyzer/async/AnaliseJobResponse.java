package com.rafaelvianna.legalanalyzer.async;

import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.OpcaoAnaliseEspecializadaDTO;

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
        AnaliseProcessoResponse resultado,
        /** Opção de análise especializada, oferecida quando a análise base termina. */
        OpcaoAnaliseEspecializadaDTO analiseEspecializada
) {
    public static AnaliseJobResponse status(AnaliseJob job) {
        return status(job, null);
    }

    public static AnaliseJobResponse status(AnaliseJob job, OpcaoAnaliseEspecializadaDTO opcao) {
        return new AnaliseJobResponse(
                job.id(), job.nomeArquivo(), job.status(), job.progresso(), job.etapa(),
                job.mensagem(), job.criadoEm(), job.atualizadoEm(), job.resultado(), opcao);
    }
}
