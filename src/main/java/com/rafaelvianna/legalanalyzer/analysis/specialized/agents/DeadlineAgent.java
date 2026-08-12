package com.rafaelvianna.legalanalyzer.analysis.specialized.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.SpecializedPromptTemplates;
import org.springframework.stereotype.Component;
import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AgendaPrazosDTO;

import java.util.List;

/** Agente 5 — Deadline Agent: extrai datas e eventos importantes. */
@Component
public class DeadlineAgent {

    private static final String AVISO_PADRAO =
            "Prazos e datas extraídos do material analisado. A contagem processual deve ser conferida "
            + "no sistema do tribunal e no CPC antes de qualquer providência.";

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;

    public DeadlineAgent(AiClient aiClient, AiJsonSupport jsonSupport) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
    }

    public AgendaPrazosDTO montarAgenda(AnaliseProcessoResponse analiseBase, String amostraTexto) {
        String resposta = aiClient.complete(
                SpecializedPromptTemplates.SYSTEM_DEADLINE_AGENT,
                SpecializedPromptTemplates.agendaPrazos(
                        jsonSupport.toJson(analiseBase.prazos()),
                        jsonSupport.toJson(analiseBase.cronologia()),
                        amostraTexto));

        AgendaPrazosDTO agenda = jsonSupport.parse(resposta, AgendaPrazosDTO.class);
        String aviso = agenda.aviso() == null || agenda.aviso().isBlank()
                ? AVISO_PADRAO
                : agenda.aviso() + " " + AVISO_PADRAO;

        return new AgendaPrazosDTO(
                agenda.prazos() == null ? List.of() : agenda.prazos(),
                agenda.eventos() == null ? List.of() : agenda.eventos(),
                agenda.datasAmbiguas() == null ? List.of() : agenda.datasAmbiguas(),
                aviso);
    }
}
