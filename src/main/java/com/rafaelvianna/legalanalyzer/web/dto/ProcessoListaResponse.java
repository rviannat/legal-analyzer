package com.rafaelvianna.legalanalyzer.web.dto;

import java.time.Instant;

public record ProcessoListaResponse(
        String id,
        String nomeArquivo,
        String numeroCnj,
        String status,
        int progresso,
        String etapa,
        String mensagem,
        Instant criadoEm,
        Instant atualizadoEm,
        boolean relatorioBaseDisponivel,
        String analiseEspecializadaId,
        String analiseEspecializadaStatus,
        int analiseEspecializadaProgresso,
        String analiseEspecializadaEtapa,
        boolean relatorioEspecializadoDisponivel
) {}
