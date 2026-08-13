package com.rafaelvianna.legalanalyzer.analysis.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.ConsolidationProgressListener;
import com.rafaelvianna.legalanalyzer.analysis.prompts.PromptTemplates;
import com.rafaelvianna.legalanalyzer.exception.AiClientException;
import com.rafaelvianna.legalanalyzer.web.dto.ExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agente responsável por consolidar os resultados parciais de extração
 * (um por trecho/chunk) em um único resultado coerente, removendo
 * duplicatas e ordenando cronologicamente. Só é chamado quando o
 * documento precisou ser dividido em mais de um trecho.
 *
 * <p>Documentos grandes (muitas dezenas de chunks) gerariam, se enviados
 * de uma só vez, um prompt de consolidação maior que a própria janela de
 * contexto do modelo (ex.: 59 chunks ≈ 25 mil tokens vs. num_ctx=4096).
 * Para evitar isso, a consolidação é feita em duas fases, no estilo
 * "map-reduce":
 * <ol>
 *   <li>Os resultados parciais são agrupados em LOTES cujo JSON somado
 *   fique dentro de {@code maxCaracteresPorLote}; cada lote é consolidado
 *   isoladamente pela IA.</li>
 *   <li>Se sobrar mais de um lote consolidado, o processo se repete
 *   recursivamente sobre esses resultados intermediários, até restar
 *   apenas um resultado final.</li>
 * </ol>
 *
 * <p><strong>Garantia de convergência.</strong> O agrupamento por tamanho,
 * isoladamente, não garante que o nível diminua: um bloco cujo JSON já passa
 * de {@code maxCaracteresPorLote} forma um lote de 1 item, e lote de 1 item é
 * devolvido sem fusão. Quando todos os lotes de um nível têm 1 item, o nível
 * seguinte é idêntico ao atual e o laço gira para sempre — sem chamar a IA,
 * sem log e sem erro, deixando o job travado em "CONSOLIDANDO" (55%).
 * Por isso, sempre que o agrupamento por tamanho não reduzir o nível, caímos
 * para o agrupamento em pares, que reduz o nível pela metade por definição.
 */
@Component
public class ConsolidationAgent {

    private static final Logger log = LoggerFactory.getLogger(ConsolidationAgent.class);

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;

    /**
     * Tamanho máximo (em caracteres) do JSON concatenado enviado em uma
     * única chamada de consolidação. Convertido grosseiramente para
     * tokens (÷ ~3.2 chars/token para português), isso deixa margem
     * confortável dentro de num_ctx mesmo em janelas de contexto
     * modestas (ex.: 4096). Ajustável via ai.consolidacao-max-chars-lote
     * caso a janela de contexto configurada seja maior/menor.
     */
    private final int maxCaracteresPorLote;

    /**
     * Orçamento de tokens de saída das chamadas de consolidação. A resposta
     * aqui é o JSON unificado inteiro, bem maior que a de um agente comum,
     * então o padrão global (legal-analyzer.ai.max-tokens) trunca a resposta
     * e faz a análise falhar ao interpretar o JSON.
     */
    private final int maxTokensConsolidacao;

    public ConsolidationAgent(AiClient aiClient,
                               AiJsonSupport jsonSupport,
                               @Value("${legal-analyzer.ai.consolidacao-max-chars-lote:12000}") int maxCaracteresPorLote,
                               @Value("${legal-analyzer.ai.max-tokens-consolidacao:3072}") int maxTokensConsolidacao) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
        this.maxCaracteresPorLote = maxCaracteresPorLote;
        this.maxTokensConsolidacao = maxTokensConsolidacao;
    }

    public ExtractionResult consolidar(List<ExtractionResult> resultadosParciais) {
        return consolidar(resultadosParciais, ConsolidationProgressListener.noop());
    }

    public ExtractionResult consolidar(List<ExtractionResult> resultadosParciais,
                                       ConsolidationProgressListener progresso) {
        if (resultadosParciais == null || resultadosParciais.isEmpty()) {
            throw new AiClientException("Nenhum resultado parcial para consolidar.");
        }
        if (resultadosParciais.size() == 1) {
            return resultadosParciais.get(0);
        }

        // Consolidar n blocos em 1 exige exatamente n-1 fusões, qualquer que
        // seja o agrupamento — isso dá um denominador estável para o progresso.
        final int fusoesTotais = resultadosParciais.size() - 1;
        int fusoesConcluidas = 0;

        List<ExtractionResult> nivelAtual = resultadosParciais;
        int nivel = 0;

        // Reduz em lotes sucessivamente até restar um único resultado.
        while (nivelAtual.size() > 1) {
            nivel++;
            List<List<ExtractionResult>> lotes = agruparEmLotes(nivelAtual);

            // GARANTIA DE PROGRESSO: se o agrupamento por tamanho devolveu um
            // lote por item, nada seria fundido e o nível seguinte seria igual
            // ao atual (loop infinito silencioso). Nesse caso, força pares.
            if (lotes.size() >= nivelAtual.size()) {
                log.warn("Nível {} da consolidação: {} blocos parciais maiores que o limite de {} chars por lote — "
                                + "agrupando em pares para garantir convergência. "
                                + "Considere aumentar OLLAMA_CONSOLIDACAO_MAX_CHARS_LOTE e OLLAMA_CONTEXT_WINDOW.",
                        nivel, nivelAtual.size(), maxCaracteresPorLote);
                lotes = agruparEmPares(nivelAtual);
            }

            log.info("Consolidação nível {}: {} blocos -> {} lotes ({} de {} fusões concluídas).",
                    nivel, nivelAtual.size(), lotes.size(), fusoesConcluidas, fusoesTotais);

            List<ExtractionResult> proximoNivel = new ArrayList<>(lotes.size());
            for (int i = 0; i < lotes.size(); i++) {
                List<ExtractionResult> lote = lotes.get(i);
                if (lote.size() == 1) {
                    proximoNivel.add(lote.get(0));
                    continue;
                }

                proximoNivel.add(consolidarLote(lote, nivel, i + 1, lotes.size()));

                fusoesConcluidas += lote.size() - 1;
                progresso.onProgresso(fusoesConcluidas, fusoesTotais,
                        "Consolidando trechos: " + fusoesConcluidas + " de " + fusoesTotais + " fusões.");
            }

            // Rede de segurança: com o fallback em pares isso não deve ocorrer,
            // mas é melhor falhar com uma mensagem clara do que girar para sempre.
            if (proximoNivel.size() >= nivelAtual.size()) {
                throw new AiClientException(String.format(
                        "Consolidação não converge: %d blocos parciais não puderam ser reduzidos "
                                + "(consolidacao-max-chars-lote=%d). Aumente OLLAMA_CONSOLIDACAO_MAX_CHARS_LOTE "
                                + "e OLLAMA_CONTEXT_WINDOW, ou aumente PDF_CHUNK_CHAR_SIZE para gerar menos trechos.",
                        nivelAtual.size(), maxCaracteresPorLote));
            }

            nivelAtual = proximoNivel;
        }

        progresso.onProgresso(fusoesTotais, fusoesTotais, "Consolidação concluída.");
        return nivelAtual.get(0);
    }

    private ExtractionResult consolidarLote(List<ExtractionResult> lote, int nivel, int indiceLote, int totalLotes) {
        String blocosJson = lote.stream()
                .map(jsonSupport::toJson)
                .collect(Collectors.joining(",\n"));

        log.info("Consolidando lote {}/{} do nível {}: {} blocos, {} chars de JSON.",
                indiceLote, totalLotes, nivel, lote.size(), blocosJson.length());
        long inicio = System.currentTimeMillis();

        String resposta = aiClient.complete(
                PromptTemplates.SYSTEM_JURIDICO,
                PromptTemplates.consolidacao(blocosJson),
                maxTokensConsolidacao);

        log.info("Lote {}/{} do nível {} consolidado em {} ms.",
                indiceLote, totalLotes, nivel, System.currentTimeMillis() - inicio);

        return jsonSupport.parse(resposta, ExtractionResult.class);
    }

    /**
     * Agrupa os resultados em lotes cujo JSON somado não ultrapasse
     * {@code maxCaracteresPorLote}. Um único item maior que o limite
     * ainda assim forma seu próprio lote (nunca é descartado).
     */
    private List<List<ExtractionResult>> agruparEmLotes(List<ExtractionResult> resultados) {
        List<List<ExtractionResult>> lotes = new ArrayList<>();
        List<ExtractionResult> loteAtual = new ArrayList<>();
        int caracteresLoteAtual = 0;

        for (ExtractionResult resultado : resultados) {
            int tamanho = jsonSupport.toJson(resultado).length();
            if (!loteAtual.isEmpty() && caracteresLoteAtual + tamanho > maxCaracteresPorLote) {
                lotes.add(loteAtual);
                loteAtual = new ArrayList<>();
                caracteresLoteAtual = 0;
            }
            loteAtual.add(resultado);
            caracteresLoteAtual += tamanho;
        }
        if (!loteAtual.isEmpty()) {
            lotes.add(loteAtual);
        }
        return lotes;
    }

    /**
     * Agrupamento de fallback, usado quando o agrupamento por tamanho não
     * reduz o nível. Sempre divide a quantidade de blocos (aproximadamente)
     * pela metade, garantindo que o laço de consolidação termine.
     */
    private List<List<ExtractionResult>> agruparEmPares(List<ExtractionResult> resultados) {
        List<List<ExtractionResult>> lotes = new ArrayList<>();
        for (int i = 0; i < resultados.size(); i += 2) {
            lotes.add(new ArrayList<>(resultados.subList(i, Math.min(i + 2, resultados.size()))));
        }
        return lotes;
    }
}
