package com.rafaelvianna.legalanalyzer.analysis.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.PromptTemplates;
import com.rafaelvianna.legalanalyzer.web.dto.ExtractionResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agente responsável por consolidar os resultados parciais de extração
 * (um por trecho/chunk) em um único resultado coerente, removendo
 * duplicatas e ordenando cronologicamente. Só é chamado quando o
 * documento precisou ser dividido em mais de um trecho.
 */
@Component
public class ConsolidationAgent {

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;

    public ConsolidationAgent(AiClient aiClient, AiJsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
    }

    public ExtractionResult consolidar(List<ExtractionResult> resultadosParciais) {
        String blocosJson = resultadosParciais.stream()
                .map(jsonSupport::toJson)
                .collect(Collectors.joining(",\n"));

        String resposta = aiClient.complete(
                PromptTemplates.SYSTEM_JURIDICO,
                PromptTemplates.consolidacao(blocosJson));

        return jsonSupport.parse(resposta, ExtractionResult.class);
    }
}
