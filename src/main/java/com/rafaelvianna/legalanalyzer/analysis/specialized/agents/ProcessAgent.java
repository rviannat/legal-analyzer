package com.rafaelvianna.legalanalyzer.analysis.specialized.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.SpecializedPromptTemplates;
import org.springframework.stereotype.Component;
import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseProcessualDTO;

/**
 * Agente 1 — Process Agent: analisa o processo completo em chave estratégica
 * (fase, teses, pontos controvertidos, forças, fragilidades e prognóstico).
 */
@Component
public class ProcessAgent {

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;

    public ProcessAgent(AiClient aiClient, AiJsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
    }

    public AnaliseProcessualDTO analisar(AnaliseProcessoResponse analiseBase, String parteRepresentada,
                                         String contextoAdvogado, String amostraTexto) {
        String dadosBaseJson = jsonSupport.toJson(new DadosBase(
                analiseBase.partes(), analiseBase.cronologia(), analiseBase.pedidos(),
                analiseBase.decisoes(), analiseBase.prazos(), analiseBase.documentosImportantes(),
                analiseBase.inconsistencias()));

        String resposta = aiClient.complete(
                SpecializedPromptTemplates.SYSTEM_PROCESS_AGENT,
                SpecializedPromptTemplates.analiseProcessual(
                        dadosBaseJson, analiseBase.resumoProcesso(), parteRepresentada, contextoAdvogado, amostraTexto));

        return jsonSupport.parse(resposta, AnaliseProcessualDTO.class);
    }

    /** Recorte da análise base enviado ao modelo (evita reenviar o relatório inteiro). */
    private record DadosBase(Object partes, Object cronologia, Object pedidos, Object decisoes,
                             Object prazos, Object documentosImportantes, Object inconsistencias) {
    }
}
