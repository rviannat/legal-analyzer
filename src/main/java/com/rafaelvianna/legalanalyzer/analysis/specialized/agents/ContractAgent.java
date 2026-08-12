package com.rafaelvianna.legalanalyzer.analysis.specialized.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.SpecializedPromptTemplates;
import org.springframework.stereotype.Component;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseContratualDTO;

/**
 * Agente 2 — Contract Agent: analisa contratos e identifica cláusulas de
 * risco, obrigações, multas, prazos, condições e inconsistências.
 */
@Component
public class ContractAgent {

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;

    public ContractAgent(AiClient aiClient, AiJsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
    }

    public AnaliseContratualDTO analisar(String parteRepresentada, String contextoAdvogado, String textoContrato) {
        String resposta = aiClient.complete(
                SpecializedPromptTemplates.SYSTEM_CONTRACT_AGENT,
                SpecializedPromptTemplates.analiseContratual(parteRepresentada, contextoAdvogado, textoContrato));

        return jsonSupport.parse(resposta, AnaliseContratualDTO.class);
    }
}
