package com.rafaelvianna.legalanalyzer.analysis.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.PromptTemplates;
import com.rafaelvianna.legalanalyzer.web.dto.ExtractionResult;
import com.rafaelvianna.legalanalyzer.web.dto.InconsistenciaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.InconsistenciaResult;
import org.springframework.stereotype.Component;

import java.util.List;

/** Agente responsável pela tarefa 9: apontar inconsistências. */
@Component
public class InconsistenciaAgent {

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;

    public InconsistenciaAgent(AiClient aiClient, AiJsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
    }

    public List<InconsistenciaDTO> identificar(ExtractionResult dadosConsolidados, String amostraTexto) {
        String dadosJson = jsonSupport.toJson(dadosConsolidados);
        String resposta = aiClient.complete(
                PromptTemplates.SYSTEM_JURIDICO,
                PromptTemplates.inconsistencias(dadosJson, amostraTexto));

        InconsistenciaResult resultado = jsonSupport.parse(resposta, InconsistenciaResult.class);
        return resultado.inconsistencias();
    }
}
