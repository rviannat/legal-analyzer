package com.rafaelvianna.legalanalyzer.analysis.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.PromptTemplates;
import com.rafaelvianna.legalanalyzer.web.dto.ExtractionResult;
import com.rafaelvianna.legalanalyzer.web.dto.InconsistenciaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.RelatorioExecutivoDTO;
import com.rafaelvianna.legalanalyzer.web.dto.RelatorioExecutivoResult;
import org.springframework.stereotype.Component;

import java.util.List;

/** Agente responsável pela tarefa 12: produzir o relatório executivo final. */
@Component
public class RelatorioExecutivoAgent {

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;

    public RelatorioExecutivoAgent(AiClient aiClient, AiJsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
    }

    public RelatorioExecutivoDTO gerar(String nomeArquivo, String resumo, ExtractionResult dadosConsolidados,
                                        List<InconsistenciaDTO> inconsistencias, List<String> perguntas) {
        String dadosJson = jsonSupport.toJson(dadosConsolidados);
        String inconsistenciasJson = jsonSupport.toJson(inconsistencias);
        String perguntasJson = jsonSupport.toJson(perguntas);

        String resposta = aiClient.complete(
                PromptTemplates.SYSTEM_JURIDICO,
                PromptTemplates.relatorioExecutivo(nomeArquivo, resumo, dadosJson, inconsistenciasJson, perguntasJson));

        RelatorioExecutivoResult resultado = jsonSupport.parse(resposta, RelatorioExecutivoResult.class);
        return resultado.relatorioExecutivo();
    }
}
