package com.rafaelvianna.legalanalyzer.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Embeddings via Ollama (`POST /api/embeddings`), com modelo local — o
 * conteúdo do processo não sai da máquina.
 *
 * Degradação controlada: se o modelo de embeddings não estiver disponível
 * (não baixado, servidor fora), o cliente registra o problema, marca-se como
 * indisponível e o índice passa a usar somente busca léxica. A análise nunca
 * falha por causa disso.
 */
@Component
public class OllamaEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingClient.class);

    private final AppProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    /** Vira false no primeiro erro, para não repetir chamadas condenadas a falhar. */
    private final AtomicBoolean utilizavel = new AtomicBoolean(true);

    public OllamaEmbeddingClient(AppProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public boolean disponivel() {
        AppProperties.Rag cfg = properties.rag();
        return cfg != null && cfg.embeddingsHabilitados()
                && StringUtils.hasText(cfg.embeddingModel())
                && StringUtils.hasText(cfg.embeddingBaseUrl())
                && utilizavel.get();
    }

    @Override
    public float[] embed(String texto) {
        if (!disponivel() || !StringUtils.hasText(texto)) {
            return null;
        }
        AppProperties.Rag cfg = properties.rag();
        try {
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("model", cfg.embeddingModel());
            corpo.put("prompt", texto);

            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.embeddingBaseUrl()))
                    .timeout(Duration.ofSeconds(cfg.embeddingTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(corpo), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resposta = httpClient.send(
                    requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resposta.statusCode() / 100 != 2) {
                desabilitar("o Ollama respondeu status " + resposta.statusCode()
                        + " em /api/embeddings. Verifique se o modelo '" + cfg.embeddingModel()
                        + "' foi baixado (ollama pull " + cfg.embeddingModel() + ").");
                return null;
            }

            JsonNode raiz = mapper.readTree(resposta.body());
            JsonNode vetor = raiz.path("embedding");
            if (!vetor.isArray() || vetor.isEmpty()) {
                desabilitar("resposta de /api/embeddings sem o campo 'embedding'.");
                return null;
            }

            float[] resultado = new float[vetor.size()];
            for (int i = 0; i < vetor.size(); i++) {
                resultado[i] = (float) vetor.get(i).asDouble();
            }
            return resultado;
        } catch (Exception e) {
            desabilitar("falha ao chamar /api/embeddings: " + e.getMessage());
            return null;
        }
    }

    @Override
    public List<float[]> embedLote(List<String> textos) {
        List<float[]> vetores = new ArrayList<>(textos.size());
        for (String texto : textos) {
            vetores.add(embed(texto));
        }
        return vetores;
    }

    private void desabilitar(String motivo) {
        if (utilizavel.compareAndSet(true, false)) {
            log.warn("Busca semântica desabilitada nesta execução: {} "
                    + "O índice do processo continuará funcionando em modo léxico.", motivo);
        }
    }
}
