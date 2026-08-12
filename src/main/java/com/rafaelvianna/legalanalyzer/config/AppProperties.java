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
     * Configurações do provedor de IA (por padrão, Ollama — modelo local).
     *
     * @param provider      qual implementação de AiClient usar: "ollama" (padrão) ou "anthropic"
     * @param apiKey        credencial do provedor (opcional no Ollama local)
     * @param model         nome do modelo (ex.: "llama3.1:8b" no Ollama)
     * @param baseUrl       endpoint de chat/completion do provedor
     * @param maxTokens     limite de tokens gerados (num_predict no Ollama)
     * @param temperature   temperatura de amostragem
     * @param timeoutSeconds timeout da chamada HTTP
     * @param contextWindow janela de contexto (num_ctx no Ollama); 0 = usar o default do modelo
     * @param jsonMode      força o modelo a devolver JSON válido (format: "json" no Ollama)
     * @param keepAlive     por quanto tempo o Ollama mantém o modelo carregado (ex.: "30m")
     */
    public record Ai(
            String provider,
            String apiKey,
            String model,
            String baseUrl,
            int maxTokens,
            double temperature,
            int timeoutSeconds,
            int contextWindow,
            boolean jsonMode,
            String keepAlive
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
