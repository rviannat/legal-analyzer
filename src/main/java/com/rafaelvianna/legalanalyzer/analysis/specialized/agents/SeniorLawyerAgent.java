package com.rafaelvianna.legalanalyzer.analysis.specialized.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.SpecializedPromptTemplates;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.ParecerSeniorDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SeniorLawyerAgent.class);

    private static final String RESSALVA_PADRAO =
            "Análise produzida por agentes de IA sobre o material enviado, sem acesso aos autos completos "
            + "nem ao sistema do tribunal. Não substitui a revisão do advogado responsável.";

    private static final String INSTRUCAO_DATAJUD = """
            ATENÇÃO — ACHADOS OFICIAIS DATAJUD:
            O material pode conter uma seção de auditoria DataJud com movimentações oficiais,
            divergências em relação ao PDF e eventos potencialmente geradores de prazo.
            Esses achados são evidências prioritárias para a consolidação final.

            REGRAS OBRIGATÓRIAS PARA O RESULTADO FINAL:
            - Toda divergência DataJud/PDF relevante deve aparecer em "riscosPrincipais" ou "pendenciasParaOAdvogado".
            - Toda movimentação oficial potencialmente geradora de prazo deve ser considerada em "riscosPrincipais",
              "recomendacoes" ou "proximosPassos", conforme sua relevância.
            - Quando uma movimentação oficial não possuir correspondente claro no PDF, trate isso como pendência
              de verificação e deixe explícito que o documento/ato oficial deve ser conferido.
            - Não invente prazo, quantidade de dias ou data de vencimento. Se a contagem não estiver explicitamente
              disponível no material, registre "não identificado" e recomende conferência no tribunal.
            - Diferencie claramente fato oficial do DataJud, informação extraída do PDF e hipótese dos agentes.
            - Não descarte achados DataJud apenas porque não foram mencionados por outro agente.
            - Se não houver achados DataJud no material, não crie nenhum.
            """;

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;

    public SeniorLawyerAgent(AiClient aiClient, AiJsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
    }

    public ParecerSeniorDTO consolidar(String nomeArquivo, String parteRepresentada, Object materialAgentes) {
        String materialJson = jsonSupport.toJson(materialAgentes);
        String materialComAuditoria = INSTRUCAO_DATAJUD + "\n\nMATERIAL DOS AGENTES:\n" + materialJson;

        log.info("[SENIOR_LAWYER] consolidando parecer | arquivo={} | parteRepresentada={} | materialChars={}",
                nomeArquivo, parteRepresentada, materialComAuditoria.length());
        log.info("[SENIOR_LAWYER] DataJud/Prazos marcados como achados prioritários para o parecer final | arquivo={}",
                nomeArquivo);

        String resposta = aiClient.complete(
                SpecializedPromptTemplates.SYSTEM_SENIOR_LAWYER_AGENT,
                SpecializedPromptTemplates.parecerSenior(
                        nomeArquivo, parteRepresentada, materialComAuditoria));

        ParecerSeniorDTO parecer = jsonSupport.parse(resposta, ParecerSeniorDTO.class);

        String ressalvas = parecer.ressalvas() == null || parecer.ressalvas().isBlank()
                ? RESSALVA_PADRAO
                : parecer.ressalvas() + " " + RESSALVA_PADRAO;

        log.info("[SENIOR_LAWYER] parecer consolidado | arquivo={} | riscos={} | recomendacoes={} | pendencias={}",
                nomeArquivo,
                parecer.riscosPrincipais() == null ? 0 : parecer.riscosPrincipais().size(),
                parecer.recomendacoes() == null ? 0 : parecer.recomendacoes().size(),
                parecer.pendenciasParaOAdvogado() == null ? 0 : parecer.pendenciasParaOAdvogado().size());

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
