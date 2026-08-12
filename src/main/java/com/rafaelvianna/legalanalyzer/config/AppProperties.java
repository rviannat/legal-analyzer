package com.rafaelvianna.legalanalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de configuração da aplicação, vinculadas ao prefixo
 * "legal-analyzer" em application.yml. Usa records (Java 17) com
 * constructor binding automático do Spring Boot 3.
 */
@ConfigurationProperties(prefix = "legal-analyzer")
public record AppProperties(Ai ai, Pdf pdf) {

    /**
     * Configurações do provedor de IA (por padrão, Anthropic Claude).
     */
    public record Ai(
            String apiKey,
            String model,
            String baseUrl,
            int maxTokens,
            double temperature,
            int timeoutSeconds
    ) {
    }

    /**
     * Limites e parâmetros de processamento de PDF.
     */
    public record Pdf(
            long maxFileSizeBytes,
            int chunkCharSize,
            int chunkOverlapChars
    ) {
    }
}
