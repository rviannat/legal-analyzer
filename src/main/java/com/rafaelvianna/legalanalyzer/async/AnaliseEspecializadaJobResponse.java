package com.rafaelvianna.legalanalyzer.async;

import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Status, equipe atual, telemetria e resultado de uma análise especializada longa. */
public record AnaliseEspecializadaJobResponse(
        String id,
        String analiseBaseId,
        String nomeArquivo,
        AnaliseEspecializadaStatus status,
        String equipeAtual,
        int progresso,
        String etapa,
        String mensagem,
        long estimativaRestanteSegundos,
        Instant criadoEm,
        Instant atualizadoEm,
        List<Map<String, Object>> logs,
        boolean relatorioDisponivel,
        AnaliseEspecializadaResponse resultado
) {
    public static AnaliseEspecializadaJobResponse status(AnaliseEspecializadaJob job) {
        return new AnaliseEspecializadaJobResponse(
                job.id(), job.analiseBaseId(), job.nomeArquivo(), job.status(), job.equipeAtual(), job.progresso(),
                job.etapa(), job.mensagem(), job.estimativaRestanteSegundos(), job.criadoEm(), job.atualizadoEm(),
                job.logs(), job.resultado() != null, job.resultado());
    }
}
