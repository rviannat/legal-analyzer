package com.rafaelvianna.legalanalyzer.web.dto;

import java.time.Instant;

/** Corpo padrão de resposta de erro da API. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
