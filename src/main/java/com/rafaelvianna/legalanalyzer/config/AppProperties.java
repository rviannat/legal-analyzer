package com.rafaelvianna.legalanalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Propriedades de configuração da aplicação, vinculadas ao prefixo
 * "legal-analyzer" em application.yml. Usa records (Java 17) com
 * constructor binding automático do Spring Boot 3.
 */
@ConfigurationProperties(prefix = "legal-analyzer")
public record AppProperties(Ai ai, Pdf pdf, Especializada especializada, LegalResearch legalResearch) {

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

    /**
     * Parâmetros da análise especializada (pipeline dos agentes especialistas
     * disparada após a análise base do processo).
     *
     * @param amostraTextoChars tamanho da amostra de texto original enviada aos agentes
     * @param maxRascunhos      quantidade máxima de rascunhos por requisição
     * @param maxCharsRascunho  tamanho máximo (aproximado) de cada rascunho gerado
     */
    public record Especializada(
            int amostraTextoChars,
            int maxRascunhos,
            int maxCharsRascunho
    ) {
        public int amostraTextoCharsOuPadrao() {
            return amostraTextoChars > 0 ? amostraTextoChars : 16_000;
        }

        public int maxRascunhosOuPadrao() {
            return maxRascunhos > 0 ? maxRascunhos : 5;
        }
    }

    /**
     * Pesquisa jurídica restrita a fontes autorizadas e rastreáveis.
     *
     * Desabilitada por padrão: sem fontes configuradas, o Legal Research Agent
     * informa que não pesquisou, em vez de permitir citações inventadas.
     *
     * @param enabled             liga/desliga a pesquisa
     * @param dominiosAutorizados allowlist de domínios (ex.: planalto.gov.br)
     * @param fontes              endpoints de busca, com {consulta} como placeholder
     * @param maxCharsPorFonte    corte do conteúdo baixado de cada fonte
     * @param maxFontesPorConsulta quantas fontes consultar por pesquisa
     * @param timeoutSeconds      timeout de cada requisição HTTP à fonte
     */
    public record LegalResearch(
            boolean enabled,
            List<String> dominiosAutorizados,
            List<FonteJuridica> fontes,
            int maxCharsPorFonte,
            int maxFontesPorConsulta,
            int timeoutSeconds
    ) {
        public List<String> dominiosOuVazio() {
            return dominiosAutorizados == null ? List.of() : dominiosAutorizados;
        }

        public List<FonteJuridica> fontesOuVazio() {
            return fontes == null ? List.of() : fontes;
        }

        public int maxCharsPorFonte() {
            return maxCharsPorFonte > 0 ? maxCharsPorFonte : 8_000;
        }

        public int maxFontesPorConsulta() {
            return maxFontesPorConsulta > 0 ? maxFontesPorConsulta : 3;
        }

        public int timeoutSeconds() {
            return timeoutSeconds > 0 ? timeoutSeconds : 20;
        }

        public static LegalResearch desabilitada() {
            return new LegalResearch(false, List.of(), List.of(), 8_000, 3, 20);
        }
    }

    /**
     * Uma fonte jurídica autorizada. O host da {@code urlTemplate} precisa
     * estar na allowlist de domínios para ser consultado.
     */
    public record FonteJuridica(String nome, String urlTemplate) {
    }
}
