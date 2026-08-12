package com.rafaelvianna.legalanalyzer.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Garante que o contexto sobe com o Ollama como provedor padrão de IA.
 */
@SpringBootTest
class AiProviderSelectionTest {

    @Autowired
    private AiClient aiClient;

    @Test
    void provedorPadraoDeveSerOllama() {
        assertInstanceOf(OllamaAiClient.class, aiClient);
    }
}
