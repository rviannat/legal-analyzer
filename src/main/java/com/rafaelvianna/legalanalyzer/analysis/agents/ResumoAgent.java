package com.rafaelvianna.legalanalyzer.analysis.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.PromptTemplates;
import com.rafaelvianna.legalanalyzer.web.dto.ExtractionResult;
import com.rafaelvianna.legalanalyzer.web.dto.ResumoResult;
import org.springframework.stereotype.Component;

/** Agente responsável pela tarefa 8: resumir o processo. */
@Component
public class ResumoAgent {

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;

    public ResumoAgent(AiClient aiClient, AiJsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
    }

    public String resumir(ExtractionResult dadosConsolidados, String amostraTexto) {
        String dadosJson = jsonSupport.toJson(dadosConsolidados);
        String resposta = aiClient.complete(
                PromptTemplates.SYSTEM_JURIDICO,
                PromptTemplates.resumo(dadosJson, amostraTexto));

        ResumoResult resultado = jsonSupport.parse(resposta, ResumoResult.class);
        return resultado.resumoProcesso();
    }
}
