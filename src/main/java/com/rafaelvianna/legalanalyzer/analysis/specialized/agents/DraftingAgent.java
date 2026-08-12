package com.rafaelvianna.legalanalyzer.analysis.specialized.agents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.SpecializedPromptTemplates;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.RascunhoDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.TipoRascunho;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agente 7 — Drafting Agent: gera rascunhos de parecer, manifestação,
 * relatório, petição e e-mail ao cliente.
 *
 * Todo rascunho sai com aviso explícito de revisão obrigatória por advogado
 * e com a lista de lacunas que precisam ser preenchidas antes de qualquer uso.
 */
@Component
public class DraftingAgent {

    public static final String AVISO_REVISAO =
            "RASCUNHO GERADO POR IA — NÃO PROTOCOLAR NEM ENVIAR SEM REVISÃO E APROVAÇÃO DO ADVOGADO RESPONSÁVEL. "
            + "Confira fatos, datas, valores, fundamentos legais e citações antes de qualquer uso.";

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;

    public DraftingAgent(AiClient aiClient, AiJsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
    }

    public RascunhoDTO redigir(TipoRascunho tipo, Object contextoCaso, String parteRepresentada,
                               String contextoAdvogado, int maxChars) {
        String resposta = aiClient.complete(
                SpecializedPromptTemplates.SYSTEM_DRAFTING_AGENT,
                SpecializedPromptTemplates.rascunho(
                        tipo, jsonSupport.toJson(contextoCaso), parteRepresentada, contextoAdvogado, maxChars));

        RascunhoBruto bruto = jsonSupport.parse(resposta, RascunhoBruto.class);

        return new RascunhoDTO(
                tipo,
                bruto.titulo() == null || bruto.titulo().isBlank() ? "Rascunho — " + tipo.name() : bruto.titulo(),
                bruto.conteudo() == null ? "" : bruto.conteudo(),
                bruto.pontosDeAtencao() == null ? List.of() : bruto.pontosDeAtencao(),
                bruto.lacunasParaPreencher() == null ? List.of() : bruto.lacunasParaPreencher(),
                AVISO_REVISAO);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RascunhoBruto(String titulo, String conteudo, List<String> pontosDeAtencao,
                                 List<String> lacunasParaPreencher) {
    }
}
