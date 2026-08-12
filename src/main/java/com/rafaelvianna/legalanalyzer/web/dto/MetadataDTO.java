package com.rafaelvianna.legalanalyzer.web.dto;

import java.time.Instant;

/** Metadados sobre o processamento da análise. */
public record MetadataDTO(
        String nomeArquivo,
        int quantidadeCaracteresExtraidos,
        int quantidadeTrechosProcessados,
        String modeloIaUtilizado,
        Instant dataProcessamento
) {
}
