package com.rafaelvianna.legalanalyzer.analysis.external.agents;

import com.rafaelvianna.legalanalyzer.analysis.external.ExternalAgentResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Reunião da Equipe 3. Nesta primeira etapa apenas estrutura o contrato; a reconciliação LLM será definida após o mapeamento completo da API. */
@Component
public class JusReconciliationAgent {
    public ExternalAgentResult execute(List<ExternalAgentResult> resultados, Object internalContext) {
        int total = resultados == null ? 0 : resultados.size();
        long successful = resultados == null ? 0 : resultados.stream().filter(r -> "ENCONTRADO".equals(r.status()) || "READY".equals(r.status())).count();
        return new ExternalAgentResult("JusReconciliationAgent", "READY",
                "Reunião da Equipe 3 preparada para consolidar confirmações, divergências e lacunas.",
                Map.of("agentesRecebidos", total, "resultadosComDados", successful, "contextoInternoRecebido", internalContext != null), Instant.now());
    }
}
