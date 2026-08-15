package com.rafaelvianna.legalanalyzer.analysis.external.agents;

import com.rafaelvianna.legalanalyzer.analysis.external.ExternalAgentResult;
import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import com.rafaelvianna.legalanalyzer.datajud.DataJudService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class ProcessSearchAgent {
    private final DataJudService dataJudService;
    public ProcessSearchAgent(DataJudService dataJudService) { this.dataJudService = dataJudService; }

    public ExternalAgentResult execute(String numeroProcesso) {
        return execute(dataJudService.consultar(numeroProcesso));
    }

    /** Usa uma consulta já realizada para evitar chamadas duplicadas ao DataJud. */
    public ExternalAgentResult execute(DataJudInfo info) {
        return new ExternalAgentResult("ProcessSearchAgent", info.status().name(), info.mensagem(), Map.of(
                "numeroProcesso", info.numeroProcesso() == null ? "" : info.numeroProcesso(),
                "tribunal", info.tribunal() == null ? "" : info.tribunal(),
                "classeProcessual", info.classeProcessual() == null ? "" : info.classeProcessual(),
                "orgaoJulgador", info.orgaoJulgador() == null ? "" : info.orgaoJulgador(),
                "grau", info.grau() == null ? "" : info.grau(), "encontrado", info.encontrado()), Instant.now());
    }
}
