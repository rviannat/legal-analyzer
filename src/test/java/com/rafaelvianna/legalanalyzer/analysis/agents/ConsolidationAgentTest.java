package com.rafaelvianna.legalanalyzer.analysis.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.web.dto.EventoCronologiaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.ExtractionResult;
import com.rafaelvianna.legalanalyzer.web.dto.ParteDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de regressão do {@link ConsolidationAgent}.
 *
 * <p>O bug original: quando um bloco parcial, sozinho, já era maior que
 * {@code consolidacao-max-chars-lote}, o agrupamento por tamanho devolvia um
 * lote por bloco; lotes de 1 item são devolvidos sem chamar a IA, então o
 * próximo nível era idêntico ao atual e o laço {@code while (size() > 1)}
 * girava para sempre — sem chamada de IA, sem log, sem erro e sem timeout.
 * O job ficava eternamente em "CONSOLIDANDO" com progresso 55%.
 */
class ConsolidationAgentTest {

    private static final java.time.Duration LIMITE = java.time.Duration.ofSeconds(10);

    /** Cliente de IA falso: devolve a união dos blocos recebidos, sem rede. */
    private static final class FakeAiClient implements AiClient {

        private final AiJsonSupport jsonSupport = new AiJsonSupport();
        final AtomicInteger chamadas = new AtomicInteger();
        final List<Integer> maxTokensRecebidos = new ArrayList<>();

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            return complete(systemPrompt, userPrompt, 0);
        }

        @Override
        public String complete(String systemPrompt, String userPrompt, int maxTokensSolicitado) {
            chamadas.incrementAndGet();
            maxTokensRecebidos.add(maxTokensSolicitado);

            // Reconstrói a união das partes/eventos citados no prompt para que o
            // resultado consolidado seja verificável (nada é perdido na árvore).
            Set<String> nomes = new LinkedHashSet<>();
            Set<String> datas = new LinkedHashSet<>();
            java.util.regex.Matcher mNome = java.util.regex.Pattern
                    .compile("\"nome\":\"([^\"]+)\"").matcher(userPrompt);
            while (mNome.find()) {
                nomes.add(mNome.group(1));
            }
            java.util.regex.Matcher mData = java.util.regex.Pattern
                    .compile("\"data\":\"([^\"]+)\"").matcher(userPrompt);
            while (mData.find()) {
                datas.add(mData.group(1));
            }

            List<ParteDTO> partes = nomes.stream()
                    .map(n -> new ParteDTO(n, "autor", "", ""))
                    .toList();
            List<EventoCronologiaDTO> eventos = datas.stream()
                    .map(d -> new EventoCronologiaDTO(d, "evento", "conhecimento"))
                    .toList();

            return jsonSupport.toJson(new ExtractionResult(
                    partes, eventos, List.of(), List.of(), List.of(), List.of()));
        }
    }

    private ConsolidationAgent agente(FakeAiClient cliente, int maxCharsLote, int maxTokensConsolidacao) {
        return new ConsolidationAgent(cliente, new AiJsonSupport(), maxCharsLote, maxTokensConsolidacao);
    }

    /** Bloco parcial cujo JSON é grande o suficiente para estourar o limite do lote. */
    private ExtractionResult blocoGrande(int indice, int caracteresDeRecheio) {
        String recheio = "x".repeat(caracteresDeRecheio);
        return new ExtractionResult(
                List.of(new ParteDTO("Parte " + indice, "autor", recheio, "")),
                List.of(new EventoCronologiaDTO("2024-01-" + String.format("%02d", indice), "evento", "conhecimento")),
                List.of(), List.of(), List.of(), List.of());
    }

    private ExtractionResult blocoSimples(int indice) {
        return new ExtractionResult(
                List.of(new ParteDTO("Parte " + indice, "autor", "", "")),
                List.of(new EventoCronologiaDTO("2024-01-" + String.format("%02d", indice), "evento", "conhecimento")),
                List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void deveTerminarQuandoTodosOsBlocosEstouramOLimiteDoLote() {
        // Cenário exato do bug: CADA bloco, isolado, já passa de maxCharsLote,
        // então o agrupamento por tamanho não consegue reduzir nível nenhum.
        FakeAiClient cliente = new FakeAiClient();
        ConsolidationAgent agente = agente(cliente, 500, 3072);

        List<ExtractionResult> parciais = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            parciais.add(blocoGrande(i, 2_000));
        }

        ExtractionResult consolidado = assertTimeoutPreemptively(LIMITE,
                () -> agente.consolidar(parciais));

        // Antes do patch, este ponto nunca era alcançado (laço infinito).
        assertTrue(cliente.chamadas.get() > 0,
                "a consolidação precisa chamar a IA em vez de girar sem fundir nada");
        assertEquals(8, consolidado.partes().size(),
                "nenhum bloco parcial pode ser descartado pela árvore de consolidação");
    }

    @Test
    void deveTerminarQuandoApenasUmBlocoEstouraOLimiteDoLote() {
        // Basta UM bloco gigante entre blocos pequenos para o agrupamento por
        // tamanho isolar todos em lotes de 1 item.
        FakeAiClient cliente = new FakeAiClient();
        ConsolidationAgent agente = agente(cliente, 400, 3072);

        List<ExtractionResult> parciais = List.of(
                blocoSimples(1), blocoGrande(2, 1_500), blocoSimples(3));

        ExtractionResult consolidado = assertTimeoutPreemptively(LIMITE,
                () -> agente.consolidar(parciais));

        assertEquals(3, consolidado.partes().size());
    }

    @Test
    void deveConsolidarMuitosBlocosPequenosEmVariosNiveis() {
        FakeAiClient cliente = new FakeAiClient();
        // Limite pequeno de propósito para forçar mais de um nível na árvore.
        ConsolidationAgent agente = agente(cliente, 800, 3072);

        List<ExtractionResult> parciais = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            parciais.add(blocoSimples(i));
        }

        ExtractionResult consolidado = assertTimeoutPreemptively(LIMITE,
                () -> agente.consolidar(parciais));

        assertEquals(20, consolidado.partes().size());
        assertEquals(20, consolidado.eventosCronologia().size());
    }

    @Test
    void deveReportarProgressoMonotonicoAteOTotalDeFusoes() {
        FakeAiClient cliente = new FakeAiClient();
        ConsolidationAgent agente = agente(cliente, 600, 3072);

        List<ExtractionResult> parciais = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            parciais.add(blocoSimples(i));
        }

        List<int[]> eventos = new ArrayList<>();
        agente.consolidar(parciais, (concluidas, totais, detalhe) -> {
            eventos.add(new int[]{concluidas, totais});
            assertFalse(detalhe == null || detalhe.isBlank(), "o detalhe exibido ao usuário não pode ser vazio");
        });

        assertFalse(eventos.isEmpty(), "a consolidação precisa reportar progresso, senão a barra congela em 55%");

        int anterior = 0;
        for (int[] evento : eventos) {
            assertEquals(11, evento[1], "o total de fusões é sempre n-1");
            assertTrue(evento[0] >= anterior, "o progresso não pode retroceder");
            assertTrue(evento[0] <= evento[1], "o progresso não pode passar do total");
            anterior = evento[0];
        }
        assertEquals(11, eventos.get(eventos.size() - 1)[0],
                "o último evento precisa fechar em 100% das fusões");
    }

    @Test
    void deveUsarOOrcamentoDeTokensDedicadoDaConsolidacao() {
        // O padrão global (900) truncava o JSON consolidado e quebrava o parse.
        FakeAiClient cliente = new FakeAiClient();
        ConsolidationAgent agente = agente(cliente, 6_000, 3_072);

        agente.consolidar(List.of(blocoSimples(1), blocoSimples(2)));

        assertFalse(cliente.maxTokensRecebidos.isEmpty());
        assertTrue(cliente.maxTokensRecebidos.stream().allMatch(t -> t == 3_072),
                "toda chamada de consolidação deve pedir o orçamento dedicado de tokens");
    }

    @Test
    void deveDevolverOUnicoBlocoSemChamarIA() {
        FakeAiClient cliente = new FakeAiClient();
        ExtractionResult unico = blocoSimples(1);

        ExtractionResult consolidado = agente(cliente, 6_000, 3_072).consolidar(List.of(unico));

        assertEquals(unico, consolidado);
        assertEquals(0, cliente.chamadas.get());
    }
}
