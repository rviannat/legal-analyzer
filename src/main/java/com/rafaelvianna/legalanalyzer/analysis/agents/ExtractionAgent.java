package com.rafaelvianna.legalanalyzer.analysis.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.PromptTemplates;
import com.rafaelvianna.legalanalyzer.web.dto.ExtractionResult;
import org.springframework.stereotype.Component;

/**
 * Agente responsável pelas tarefas 2 a 7 em um único trecho (chunk) do
 * processo: identifica partes, cronologia, pedidos, decisões, prazos e
 * documentos importantes.
 */
@Component
public class ExtractionAgent {

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;

    public ExtractionAgent(AiClient aiClient, AiJsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
    }

    public ExtractionResult extrair(String trechoTexto) {
        String resposta = aiClient.complete(PromptTemplates.SYSTEM_JURIDICO, PromptTemplates.extracao(trechoTexto));
        return jsonSupport.parse(resposta, ExtractionResult.class);
    }
}
