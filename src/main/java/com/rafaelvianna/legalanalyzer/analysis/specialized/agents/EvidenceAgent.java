package com.rafaelvianna.legalanalyzer.analysis.specialized.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.SpecializedPromptTemplates;
import org.springframework.stereotype.Component;
import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.MatrizEvidenciasDTO;

import java.util.List;

/**
 * Agente 6 — Evidence Agent: relaciona alegações com os documentos do
 * material que podem sustentá-las e aponta as lacunas probatórias.
 */
@Component
public class EvidenceAgent {

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;

    public EvidenceAgent(AiClient aiClient, AiJsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
    }

    public MatrizEvidenciasDTO relacionar(AnaliseProcessoResponse analiseBase, String amostraTexto) {
        String dadosBaseJson = jsonSupport.toJson(new DadosBase(
                analiseBase.partes(), analiseBase.pedidos(), analiseBase.decisoes(),
                analiseBase.documentosImportantes(), analiseBase.inconsistencias()));

        String resposta = aiClient.complete(
                SpecializedPromptTemplates.SYSTEM_EVIDENCE_AGENT,
                SpecializedPromptTemplates.matrizEvidencias(
                        dadosBaseJson, jsonSupport.toJson(analiseBase.gruposEvidencia()), amostraTexto));

        MatrizEvidenciasDTO matriz = jsonSupport.parse(resposta, MatrizEvidenciasDTO.class);
        return new MatrizEvidenciasDTO(
                matriz.alegacoes() == null ? List.of() : matriz.alegacoes(),
                matriz.lacunasProbatorias() == null ? List.of() : matriz.lacunasProbatorias(),
                matriz.provasSugeridas() == null ? List.of() : matriz.provasSugeridas(),
                matriz.observacoes() == null ? "" : matriz.observacoes());
    }

    private record DadosBase(Object partes, Object pedidos, Object decisoes,
                             Object documentosImportantes, Object inconsistencias) {
    }
}
