package com.rafaelvianna.legalanalyzer.analysis.external.agents;

import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import com.rafaelvianna.legalanalyzer.datajud.DataJudMovimento;
import com.rafaelvianna.legalanalyzer.datajud.DataJudStatus;
import com.rafaelvianna.legalanalyzer.web.dto.EventoCronologiaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.ExtractionResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JusReconciliationAgentTest {

    @Test
    void deveIdentificarMovimentacaoDivergente() {
        ExtractionResult interno = new ExtractionResult(
                List.of(),
                List.of(new EventoCronologiaDTO("2026-03-15", "Despacho", "processual")),
                List.of(), List.of(), List.of(), List.of());

        DataJudInfo externo = new DataJudInfo(
                DataJudStatus.ENCONTRADO,
                "0001234-56.2026.8.26.0000",
                "tjsp",
                "https://api-publica.datajud.cnj.jus.br/api_publica_tjsp/_search",
                true, 1, "2026-03-18T10:00:00Z — Decisão",
                "Procedimento Comum", "Vara Cível", "1º Grau", "ok", Instant.now(),
                List.of(new DataJudMovimento("2026-03-18T10:00:00Z", "Decisão", null)));

        var result = new JusReconciliationAgent().execute(List.of(), interno, externo);

        assertEquals("DIVERGENCIAS_ENCONTRADAS", result.status());
        assertTrue(result.data().get("divergencias").toString().contains("2026-03-15"));
        assertTrue(result.data().get("novosDados").toString().contains("2026-03-18"));
    }
}
