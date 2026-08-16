package com.rafaelvianna.legalanalyzer.analysis.specialized.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.SpecializedPromptTemplates;
import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import com.rafaelvianna.legalanalyzer.datajud.DataJudMovimento;
import com.rafaelvianna.legalanalyzer.datajud.DataJudService;
import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AgendaPrazosDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/** Agente 5 — Deadline Agent: extrai datas e eventos importantes e cruza-os com os eventos oficiais do DataJud. */
@Component
public class DeadlineAgent {

    private static final Logger log = LoggerFactory.getLogger(DeadlineAgent.class);
    private static final String AVISO_PADRAO =
            "Prazos e datas extraídos do material analisado. A contagem processual deve ser conferida "
            + "no sistema do tribunal e no CPC antes de qualquer providência.";

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;
    private final DataJudService dataJudService;

    public DeadlineAgent(AiClient aiClient, AiJsonSupport jsonSupport, DataJudService dataJudService) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
        this.dataJudService = dataJudService;
    }

    public AgendaPrazosDTO montarAgenda(AnaliseProcessoResponse analiseBase, String numeroProcesso, String amostraTexto) {
        String contextoDataJud = carregarContextoDataJud(numeroProcesso);
        String prompt = SpecializedPromptTemplates.agendaPrazos(
                jsonSupport.toJson(analiseBase.prazos()),
                jsonSupport.toJson(analiseBase.cronologia()),
                amostraTexto + "\n\n===== EVENTOS OFICIAIS DATAJUD/CNJ =====\n" + contextoDataJud);

        String resposta = aiClient.complete(SpecializedPromptTemplates.SYSTEM_DEADLINE_AGENT, prompt);
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

    private String carregarContextoDataJud(String numeroProcesso) {
        if (numeroProcesso == null || numeroProcesso.isBlank() || "não identificado".equalsIgnoreCase(numeroProcesso)) {
            log.info("[DEADLINE_AGENT][DATAJUD] não consultado | CNJ não identificado");
            return "INFORMAÇÃO INDISPONÍVEL: não foi identificado número CNJ para consulta oficial.";
        }

        try {
            log.info("[DEADLINE_AGENT][DATAJUD] consultando eventos oficiais | CNJ={}", numeroProcesso);
            DataJudInfo info = dataJudService.consultar(numeroProcesso);
            if (!info.encontrado()) {
                log.info("[DEADLINE_AGENT][DATAJUD] sem eventos oficiais | CNJ={} | status={} | mensagem={}",
                        numeroProcesso, info.status(), info.mensagem());
                return "CONSULTA DATAJUD: " + info.status() + "\nMensagem: " + safe(info.mensagem());
            }

            String movimentos = info.movimentos().stream()
                    .map(this::formatarMovimento)
                    .collect(Collectors.joining("\n"));
            log.info("[DEADLINE_AGENT][DATAJUD] eventos recebidos | CNJ={} | tribunal={} | movimentos={} | classe={} | órgão={}",
                    numeroProcesso, info.tribunal(), info.movimentos().size(), info.classeProcessual(), info.orgaoJulgador());

            return "Status: " + info.status()
                    + "\nTribunal: " + safe(info.tribunal())
                    + "\nClasse oficial: " + safe(info.classeProcessual())
                    + "\nÓrgão julgador: " + safe(info.orgaoJulgador())
                    + "\nQuantidade de movimentações oficiais: " + info.movimentos().size()
                    + "\nMovimentações oficiais:\n" + (movimentos.isBlank() ? "nenhuma" : movimentos);
        } catch (RuntimeException e) {
            log.warn("[DEADLINE_AGENT][DATAJUD] falha na consulta | CNJ={} | {}", numeroProcesso, e.getMessage());
            return "CONSULTA DATAJUD INDISPONÍVEL: " + safe(e.getMessage());
        }
    }

    private String formatarMovimento(DataJudMovimento movimento) {
        if (movimento == null) return "- movimento não informado";
        StringBuilder out = new StringBuilder("- ");
        if (movimento.dataHora() != null && !movimento.dataHora().isBlank()) out.append(movimento.dataHora()).append(" | ");
        out.append(safe(movimento.nome()));
        if (movimento.complemento() != null && !movimento.complemento().isBlank()) out.append(" | ").append(movimento.complemento());
        return out.toString();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "não informado" : value;
    }
}
