package com.rafaelvianna.legalanalyzer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import com.rafaelvianna.legalanalyzer.exception.AiClientException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do {@link OllamaAiClient} contra um servidor HTTP local que simula
 * o endpoint /api/chat do Ollama — sem custo e sem depender de um modelo real.
 */
class OllamaAiClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> ultimoCorpoRecebido = new AtomicReference<>();
    private volatile String respostaSimulada;
    private volatile int statusSimulado = 200;

    @BeforeEach
    void iniciarServidor() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            ultimoCorpoRecebido.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] corpo = respostaSimulada.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusSimulado, corpo.length);
            exchange.getResponseBody().write(corpo);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/chat";
    }

    @AfterEach
    void pararServidor() {
        server.stop(0);
    }

    private OllamaAiClient clienteComBaseUrl(String url) {
        AppProperties.Ai ai = new AppProperties.Ai(
                "ollama", "", "llama3.1:8b", url, 4096, 0.2, 30, 16384, true, "30m");
        return new OllamaAiClient(new AppProperties(ai, new AppProperties.Pdf(1024, 100, 10), new AppProperties.Especializada(16000, 5, 8000), AppProperties.LegalResearch.desabilitada()));
    }

    @Test
    void deveExtrairConteudoDaMensagemEEnviarPayloadNoFormatoDoOllama() throws Exception {
        respostaSimulada = "{\"model\":\"llama3.1:8b\",\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"ok\\\":true}\"},\"done\":true}";

        String texto = clienteComBaseUrl(baseUrl).complete("Você é um analista jurídico.", "Analise o trecho.");

        assertEquals("{\"ok\":true}", texto);

        JsonNode enviado = new ObjectMapper().readTree(ultimoCorpoRecebido.get());
        assertEquals("llama3.1:8b", enviado.get("model").asText());
        assertEquals(false, enviado.get("stream").asBoolean());
        assertEquals("json", enviado.get("format").asText());
        assertEquals("system", enviado.get("messages").get(0).get("role").asText());
        assertEquals("user", enviado.get("messages").get(1).get("role").asText());
        assertEquals(4096, enviado.get("options").get("num_predict").asInt());
        assertEquals(16384, enviado.get("options").get("num_ctx").asInt());
        assertEquals("30m", enviado.get("keep_alive").asText());
    }

    @Test
    void deveFalharComMensagemClaraQuandoOllamaRetornaErro() {
        respostaSimulada = "{\"error\":\"model 'llama3.1:8b' not found, try pulling it first\"}";

        AiClientException ex = assertThrows(AiClientException.class,
                () -> clienteComBaseUrl(baseUrl).complete("sys", "user"));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void deveFalharComMensagemClaraQuandoOllamaEstaIndisponivel() {
        respostaSimulada = "{}";

        AiClientException ex = assertThrows(AiClientException.class,
                () -> clienteComBaseUrl("http://127.0.0.1:1/api/chat").complete("sys", "user"));
        assertTrue(ex.getMessage().contains("ollama serve"));
    }
}
