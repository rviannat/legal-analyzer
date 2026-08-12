package com.rafaelvianna.legalanalyzer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import com.rafaelvianna.legalanalyzer.exception.AiClientException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementação de {@link AiClient} para o Ollama (modelos locais / self-hosted).
 *
 * Usa o endpoint de chat do Ollama (`POST /api/chat`) com `stream: false`,
 * mapeando o system prompt e o user prompt para a lista de mensagens.
 * Como todos os agentes esperam JSON de volta, o cliente ativa por padrão
 * o modo `format: "json"` do Ollama, que obriga o modelo a devolver um
 * documento JSON válido.
 *
 * Documentação da API: https://github.com/ollama/ollama/blob/main/docs/api.md
 */
@Component
@ConditionalOnProperty(name = "legal-analyzer.ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaAiClient implements AiClient {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final AppProperties properties;

    public OllamaAiClient(AppProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        AppProperties.Ai ai = properties.ai();

        if (!StringUtils.hasText(ai.baseUrl())) {
            throw new AiClientException(
                    "URL do Ollama não configurada. Defina OLLAMA_BASE_URL (ex.: http://localhost:11434/api/chat).");
        }
        if (!StringUtils.hasText(ai.model())) {
            throw new AiClientException(
                    "Modelo do Ollama não configurado. Defina OLLAMA_MODEL (ex.: llama3.1:8b).");
        }

        try {
            Map<String, Object> opcoes = new LinkedHashMap<>();
            opcoes.put("temperature", ai.temperature());
            // No Ollama, num_predict é o equivalente a max_tokens.
            opcoes.put("num_predict", ai.maxTokens());
            if (ai.contextWindow() > 0) {
                // num_ctx precisa ser grande o suficiente para o chunk + prompt.
                opcoes.put("num_ctx", ai.contextWindow());
            }

            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("model", ai.model());
            corpo.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)));
            corpo.put("stream", false);
            if (ai.jsonMode()) {
                corpo.put("format", "json");
            }
            corpo.put("options", opcoes);
            if (StringUtils.hasText(ai.keepAlive())) {
                corpo.put("keep_alive", ai.keepAlive());
            }

            String corpoJson = mapper.writeValueAsString(corpo);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(ai.baseUrl()))
                    .timeout(Duration.ofSeconds(ai.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corpoJson, StandardCharsets.UTF_8));

            // Ollama local não exige autenticação, mas instâncias expostas atrás de
            // um proxy/gateway podem exigir um bearer token.
            if (StringUtils.hasText(ai.apiKey())) {
                builder.header("Authorization", "Bearer " + ai.apiKey());
            }

            HttpResponse<String> resposta = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resposta.statusCode() / 100 != 2) {
                throw new AiClientException(
                        "Falha ao chamar o Ollama (status " + resposta.statusCode() + "): " + resposta.body());
            }

            return extrairTexto(resposta.body());
        } catch (AiClientException e) {
            throw e;
        } catch (IOException e) {
            throw new AiClientException(
                    "Erro de comunicação com o Ollama em " + properties.ai().baseUrl()
                            + " — verifique se o serviço está em execução (`ollama serve`): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiClientException("Chamada ao Ollama interrompida: " + e.getMessage(), e);
        }
    }

    private String extrairTexto(String corpoResposta) throws IOException {
        JsonNode raiz = mapper.readTree(corpoResposta);

        // Erro lógico devolvido com status 200 (ex.: modelo inexistente).
        String erro = raiz.path("error").asText(null);
        if (StringUtils.hasText(erro)) {
            throw new AiClientException("Ollama retornou erro: " + erro);
        }

        String texto = raiz.path("message").path("content").asText("");
        if (!StringUtils.hasText(texto)) {
            // Compatibilidade com o endpoint /api/generate, que devolve "response".
            texto = raiz.path("response").asText("");
        }

        if (!StringUtils.hasText(texto)) {
            throw new AiClientException("Resposta vazia recebida do Ollama.");
        }
        return texto;
    }
}
