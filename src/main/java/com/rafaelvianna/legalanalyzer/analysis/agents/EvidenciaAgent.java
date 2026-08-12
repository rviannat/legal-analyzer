package com.rafaelvianna.legalanalyzer.analysis.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.PromptTemplates;
import com.rafaelvianna.legalanalyzer.web.dto.EvidenciaResult;
import com.rafaelvianna.legalanalyzer.web.dto.ExtractionResult;
import com.rafaelvianna.legalanalyzer.web.dto.GrupoEvidenciaDTO;
import org.springframework.stereotype.Component;

import java.util.List;

/** Agente responsável pela tarefa 10: organizar evidências. */
@Component
public class EvidenciaAgent {

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;

    public EvidenciaAgent(AiClient aiClient, AiJsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
    }

    public List<GrupoEvidenciaDTO> organizar(ExtractionResult dadosConsolidados) {
        String dadosJson = jsonSupport.toJson(dadosConsolidados);
        String resposta = aiClient.complete(
                PromptTemplates.SYSTEM_JURIDICO,
                PromptTemplates.evidencias(dadosJson));

        EvidenciaResult resultado = jsonSupport.parse(resposta, EvidenciaResult.class);
        return resultado.gruposEvidencia();
    }
}
