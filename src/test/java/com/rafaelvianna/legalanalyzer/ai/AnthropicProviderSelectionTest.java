package com.rafaelvianna.legalanalyzer.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Garante que, configurando provider=anthropic, o contexto usa o cliente
 * da Anthropic em vez do Ollama.
 */
@SpringBootTest
@TestPropertySource(properties = "legal-analyzer.ai.provider=anthropic")
class AnthropicProviderSelectionTest {

    @Autowired
    private AiClient aiClient;

    @Test
    void deveUsarAnthropicQuandoConfigurado() {
        assertInstanceOf(AnthropicAiClient.class, aiClient);
    }
}
