package com.rafaelvianna.legalanalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "legal-analyzer")
public record AppProperties(Ai ai, Pdf pdf, Especializada especializada, LegalResearch legalResearch, Rag rag, DataJud dataJud) {
    public record Ai(String provider, String apiKey, String model, String baseUrl, int maxTokens, double temperature, int timeoutSeconds, int contextWindow, boolean jsonMode, String keepAlive) {}
    public record Pdf(long maxFileSizeBytes, int chunkCharSize, int chunkOverlapChars) {}
    public record Especializada(int amostraTextoChars, int maxRascunhos, int maxCharsRascunho) {
        public int amostraTextoCharsOuPadrao() { return amostraTextoChars > 0 ? amostraTextoChars : 16_000; }
        public int maxRascunhosOuPadrao() { return maxRascunhos > 0 ? maxRascunhos : 5; }
        public int maxCharsRascunhoOuPadrao() { return maxCharsRascunho > 0 ? maxCharsRascunho : 8_000; }
    }
    public record LegalResearch(boolean enabled, List<String> dominiosAutorizados, List<FonteJuridica> fontes, int maxCharsPorFonte, int maxFontesPorConsulta, int timeoutSeconds) {
        public List<String> dominiosOuVazio() { return dominiosAutorizados == null ? List.of() : dominiosAutorizados; }
        public List<FonteJuridica> fontesOuVazio() { return fontes == null ? List.of() : fontes; }
        public int maxCharsPorFonte() { return maxCharsPorFonte > 0 ? maxCharsPorFonte : 8_000; }
        public int maxFontesPorConsulta() { return maxFontesPorConsulta > 0 ? maxFontesPorConsulta : 3; }
        public int timeoutSeconds() { return timeoutSeconds > 0 ? timeoutSeconds : 20; }
        public static LegalResearch desabilitada() { return new LegalResearch(false, List.of(), List.of(), 8_000, 3, 20); }
    }
    public record Rag(boolean embeddingsHabilitados, String embeddingModel, String embeddingBaseUrl, int embeddingTimeoutSeconds, int tamanhoPassagemChars, int sobreposicaoPassagemChars, int maxPassagensIndexadas, int maxPassagensPorResposta, double scoreMinimo, int maxMensagensHistorico) {
        public int embeddingTimeoutSeconds() { return embeddingTimeoutSeconds > 0 ? embeddingTimeoutSeconds : 60; }
        public int tamanhoPassagemChars() { return tamanhoPassagemChars > 0 ? tamanhoPassagemChars : 1_200; }
        public int sobreposicaoPassagemChars() { return sobreposicaoPassagemChars > 0 ? sobreposicaoPassagemChars : 200; }
        public int maxPassagensIndexadas() { return maxPassagensIndexadas > 0 ? maxPassagensIndexadas : 4_000; }
        public int maxPassagensPorResposta() { return maxPassagensPorResposta > 0 ? maxPassagensPorResposta : 8; }
        public double scoreMinimo() { return scoreMinimo > 0 ? scoreMinimo : 0.05; }
        public int maxMensagensHistorico() { return maxMensagensHistorico > 0 ? maxMensagensHistorico : 6; }
        public static Rag padrao() { return new Rag(false, "nomic-embed-text", "http://localhost:11434/api/embeddings", 60, 1_200, 200, 4_000, 8, 0.05, 6); }
    }
    /** Configuração da API Pública do DataJud/CNJ e, opcionalmente, de uma fonte agregada oficial de estatísticas. */
    public record DataJud(boolean enabled, String baseUrl, String apiKey, int timeoutSeconds, String estatisticasUrl) {
        public String baseUrlOuPadrao() { return baseUrl == null || baseUrl.isBlank() ? "https://api-publica.datajud.cnj.jus.br" : baseUrl; }
        public int timeoutSecondsOuPadrao() { return timeoutSeconds > 0 ? timeoutSeconds : 30; }
        public boolean configurado() { return enabled && apiKey != null && !apiKey.isBlank(); }
        public boolean estatisticasConfiguradas() { return estatisticasUrl != null && !estatisticasUrl.isBlank() && configurado(); }
        public static DataJud desabilitado() { return new DataJud(false, "https://api-publica.datajud.cnj.jus.br", "", 30, ""); }
    }
    public record FonteJuridica(String nome, String urlTemplate) {}
}
