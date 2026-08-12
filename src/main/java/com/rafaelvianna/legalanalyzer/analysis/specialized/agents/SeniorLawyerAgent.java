package com.rafaelvianna.legalanalyzer.analysis.specialized.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.SpecializedPromptTemplates;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.ParecerSeniorDTO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agente 8 — Senior Lawyer Agent: o agente orquestrador. Recebe o trabalho
 * dos demais agentes e produz o resultado final (síntese executiva,
 * conclusões, riscos, recomendações, próximos passos, pendências humanas e
 * divergências entre os agentes).
 */
@Component
public class SeniorLawyerAgent {

    private static final String RESSALVA_PADRAO =
            "Análise produzida por agentes de IA sobre o material enviado, sem acesso aos autos completos "
            + "nem ao sistema do tribunal. Não substitui a revisão do advogado responsável.";

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;

    public SeniorLawyerAgent(AiClient aiClient, AiJsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
    }

    public ParecerSeniorDTO consolidar(String nomeArquivo, String parteRepresentada, Object materialAgentes) {
        String resposta = aiClient.complete(
                SpecializedPromptTemplates.SYSTEM_SENIOR_LAWYER_AGENT,
                SpecializedPromptTemplates.parecerSenior(
                        nomeArquivo, parteRepresentada, jsonSupport.toJson(materialAgentes)));

        ParecerSeniorDTO parecer = jsonSupport.parse(resposta, ParecerSeniorDTO.class);

        String ressalvas = parecer.ressalvas() == null || parecer.ressalvas().isBlank()
                ? RESSALVA_PADRAO
                : parecer.ressalvas() + " " + RESSALVA_PADRAO;

        return new ParecerSeniorDTO(
                parecer.titulo() == null || parecer.titulo().isBlank()
                        ? "Parecer consolidado — " + nomeArquivo : parecer.titulo(),
                parecer.sinteseExecutiva() == null ? "" : parecer.sinteseExecutiva(),
                listaOuVazia(parecer.conclusoes()),
                listaOuVazia(parecer.riscosPrincipais()),
                listaOuVazia(parecer.recomendacoes()),
                listaOuVazia(parecer.proximosPassos()),
                listaOuVazia(parecer.pendenciasParaOAdvogado()),
                parecer.divergenciasEntreAgentes() == null ? "nenhuma" : parecer.divergenciasEntreAgentes(),
                ressalvas);
    }

    private List<String> listaOuVazia(List<String> lista) {
        return lista == null ? List.of() : lista;
    }
}
