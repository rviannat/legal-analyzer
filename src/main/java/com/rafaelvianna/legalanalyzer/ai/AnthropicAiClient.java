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
 * Implementação de {@link AiClient} para a API de Mensagens da Anthropic
 * (Claude). Usa {@link HttpClient} nativo do Java para evitar dependências
 * adicionais de SDK.
 *
 * Documentação da API: https://docs.claude.com/en/api/messages
 *
 * Só é registrada como bean quando `legal-analyzer.ai.provider=anthropic`.
 * O provedor padrão do projeto é o Ollama ({@link OllamaAiClient}).
 */
@Component
@ConditionalOnProperty(name = "legal-analyzer.ai.provider", havingValue = "anthropic")
public class AnthropicAiClient implements AiClient {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final AppProperties properties;

    public AnthropicAiClient(AppProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, userPrompt, 0);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, int maxTokensSolicitado) {
        if (!StringUtils.hasText(properties.ai().apiKey())) {
            throw new AiClientException(
                    "Chave de API de IA não configurada. Defina a variável de ambiente ANTHROPIC_API_KEY.");
        }

        try {
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("model", properties.ai().model());
            corpo.put("max_tokens", maxTokensSolicitado > 0 ? maxTokensSolicitado : properties.ai().maxTokens());
            corpo.put("temperature", properties.ai().temperature());
            corpo.put("system", systemPrompt);
            corpo.put("messages", List.of(Map.of("role", "user", "content", userPrompt)));

            String corpoJson = mapper.writeValueAsString(corpo);

            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(properties.ai().baseUrl()))
                    .timeout(Duration.ofSeconds(properties.ai().timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", properties.ai().apiKey())
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(corpoJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resposta = httpClient.send(requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resposta.statusCode() / 100 != 2) {
                throw new AiClientException(
                        "Falha ao chamar a API de IA (status " + resposta.statusCode() + "): " + resposta.body());
            }

            return extrairTexto(resposta.body());
        } catch (AiClientException e) {
            throw e;
        } catch (IOException e) {
            throw new AiClientException("Erro de comunicação com a API de IA: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiClientException("Chamada à API de IA interrompida: " + e.getMessage(), e);
        }
    }

    private String extrairTexto(String corpoResposta) throws IOException {
        JsonNode raiz = mapper.readTree(corpoResposta);
        JsonNode blocosConteudo = raiz.path("content");

        StringBuilder textoCompleto = new StringBuilder();
        if (blocosConteudo.isArray()) {
            for (JsonNode bloco : blocosConteudo) {
                if ("text".equals(bloco.path("type").asText())) {
                    textoCompleto.append(bloco.path("text").asText());
                }
            }
        }

        String texto = textoCompleto.toString();
        if (!StringUtils.hasText(texto)) {
            throw new AiClientException("Resposta vazia recebida da API de IA.");
        }
        return texto;
    }
}
