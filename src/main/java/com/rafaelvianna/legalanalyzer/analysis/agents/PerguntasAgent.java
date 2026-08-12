package com.rafaelvianna.legalanalyzer.analysis.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.PromptTemplates;
import com.rafaelvianna.legalanalyzer.web.dto.ExtractionResult;
import com.rafaelvianna.legalanalyzer.web.dto.InconsistenciaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.PerguntasResult;
import org.springframework.stereotype.Component;

import java.util.List;

/** Agente responsável pela tarefa 11: gerar perguntas de investigação para o advogado. */
@Component
public class PerguntasAgent {

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;

    public PerguntasAgent(AiClient aiClient, AiJsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
    }

    public List<String> gerar(ExtractionResult dadosConsolidados, List<InconsistenciaDTO> inconsistencias, String resumo) {
        String dadosJson = jsonSupport.toJson(dadosConsolidados);
        String inconsistenciasJson = jsonSupport.toJson(inconsistencias);

        String resposta = aiClient.complete(
                PromptTemplates.SYSTEM_JURIDICO,
                PromptTemplates.perguntasInvestigacao(dadosJson, inconsistenciasJson, resumo));

        PerguntasResult resultado = jsonSupport.parse(resposta, PerguntasResult.class);
        return resultado.perguntasInvestigacao();
    }
}
