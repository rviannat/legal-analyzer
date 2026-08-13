package com.rafaelvianna.legalanalyzer.analysis.agents;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.PromptTemplates;
import com.rafaelvianna.legalanalyzer.web.dto.ExtractionResult;
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
 */
@Component
public class ConsolidationAgent {

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

    public ConsolidationAgent(AiClient aiClient,
                               AiJsonSupport jsonSupport,
                               @Value("${legal-analyzer.ai.consolidacao-max-chars-lote:6000}") int maxCaracteresPorLote) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
        this.maxCaracteresPorLote = maxCaracteresPorLote;
    }

    public ExtractionResult consolidar(List<ExtractionResult> resultadosParciais) {
        if (resultadosParciais.size() == 1) {
            return resultadosParciais.get(0);
        }

        List<ExtractionResult> nivelAtual = resultadosParciais;

        // Reduz em lotes sucessivamente até restar um único resultado.
        // Na prática, para o volume de chunks esperado, 1-2 passadas bastam.
        while (nivelAtual.size() > 1) {
            List<List<ExtractionResult>> lotes = agruparEmLotes(nivelAtual);

            // Se todo o nível já coube em um único lote, resolve direto.
            if (lotes.size() == 1) {
                return consolidarLote(lotes.get(0));
            }

            List<ExtractionResult> proximoNivel = new ArrayList<>(lotes.size());
            for (List<ExtractionResult> lote : lotes) {
                proximoNivel.add(lote.size() == 1 ? lote.get(0) : consolidarLote(lote));
            }
            nivelAtual = proximoNivel;
        }

        return nivelAtual.get(0);
    }

    private ExtractionResult consolidarLote(List<ExtractionResult> lote) {
        String blocosJson = lote.stream()
                .map(jsonSupport::toJson)
                .collect(Collectors.joining(",\n"));

        String resposta = aiClient.complete(
                PromptTemplates.SYSTEM_JURIDICO,
                PromptTemplates.consolidacao(blocosJson));

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
}
