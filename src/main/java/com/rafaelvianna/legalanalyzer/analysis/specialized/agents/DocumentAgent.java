package com.rafaelvianna.legalanalyzer.analysis.specialized.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.SpecializedPromptTemplates;
import org.springframework.stereotype.Component;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.ClassificacaoDocumentalDTO;

import java.util.List;

/**
 * Agente 3 — Document Agent: classifica automaticamente o material analisado.
 * Roda primeiro na análise especializada, porque sua classificação orienta o
 * roteamento entre o Process Agent e o Contract Agent.
 */
@Component
public class DocumentAgent {

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;

    public DocumentAgent(AiClient aiClient, AiJsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
    }

    public ClassificacaoDocumentalDTO classificar(String nomeArquivo, Object documentosImportantes, String amostraTexto) {
        String resposta = aiClient.complete(
                SpecializedPromptTemplates.SYSTEM_DOCUMENT_AGENT,
                SpecializedPromptTemplates.classificacaoDocumental(
                        nomeArquivo, jsonSupport.toJson(documentosImportantes), amostraTexto));

        ClassificacaoDocumentalDTO classificacao = jsonSupport.parse(resposta, ClassificacaoDocumentalDTO.class);
        return new ClassificacaoDocumentalDTO(
                textoOu(classificacao.naturezaPrincipal(), "não identificado"),
                textoOu(classificacao.confianca(), "não identificado"),
                classificacao.documentos() == null ? List.of() : classificacao.documentos(),
                classificacao.indicios() == null ? List.of() : classificacao.indicios(),
                textoOu(classificacao.observacoes(), ""));
    }

    private String textoOu(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor;
    }
}
