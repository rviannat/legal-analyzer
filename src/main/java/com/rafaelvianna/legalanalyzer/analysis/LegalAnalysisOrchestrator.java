package com.rafaelvianna.legalanalyzer.analysis;

import com.rafaelvianna.legalanalyzer.analysis.agents.ConsolidationAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.EvidenciaAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.ExtractionAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.InconsistenciaAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.PerguntasAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.RelatorioExecutivoAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.ResumoAgent;
import com.rafaelvianna.legalanalyzer.async.AnaliseStatus;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import com.rafaelvianna.legalanalyzer.pdf.PdfTextChunker;
import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.ExtractionResult;
import com.rafaelvianna.legalanalyzer.web.dto.GrupoEvidenciaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.InconsistenciaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.MetadataDTO;
import com.rafaelvianna.legalanalyzer.web.dto.RelatorioExecutivoDTO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orquestra a análise jurídica sem transformar um PDF grande em milhares de
 * chamadas ao LLM. Em máquinas locais/CPU, o custo da inferência é o gargalo;
 * por isso a pipeline usa uma amostra determinística e representativa dos
 * trechos, seguida de consolidação hierárquica.
 */
@Service
public class LegalAnalysisOrchestrator {

    private static final int TAMANHO_AMOSTRA_TEXTO = 6_000;
    private static final int TAMANHO_LOTE_CONSOLIDACAO = 3;
    private static final int MAX_TRECHOS_IA = 16;

    private final PdfTextChunker chunker;
    private final ExtractionAgent extractionAgent;
    private final ConsolidationAgent consolidationAgent;
    private final ResumoAgent resumoAgent;
    private final InconsistenciaAgent inconsistenciaAgent;
    private final EvidenciaAgent evidenciaAgent;
    private final PerguntasAgent perguntasAgent;
    private final RelatorioExecutivoAgent relatorioExecutivoAgent;
    private final AppProperties properties;

    public LegalAnalysisOrchestrator(PdfTextChunker chunker,
                                      ExtractionAgent extractionAgent,
                                      ConsolidationAgent consolidationAgent,
                                      ResumoAgent resumoAgent,
                                      InconsistenciaAgent inconsistenciaAgent,
                                      EvidenciaAgent evidenciaAgent,
                                      PerguntasAgent perguntasAgent,
                                      RelatorioExecutivoAgent relatorioExecutivoAgent,
                                      AppProperties properties) {
        this.chunker = chunker;
        this.extractionAgent = extractionAgent;
        this.consolidationAgent = consolidationAgent;
        this.resumoAgent = resumoAgent;
        this.inconsistenciaAgent = inconsistenciaAgent;
        this.evidenciaAgent = evidenciaAgent;
        this.perguntasAgent = perguntasAgent;
        this.relatorioExecutivoAgent = relatorioExecutivoAgent;
        this.properties = properties;
    }

    public AnaliseProcessoResponse analisar(String nomeArquivo, String textoCompleto) {
        return analisar(nomeArquivo, textoCompleto, AnalysisProgressListener.noop());
    }

    public AnaliseProcessoResponse analisar(String nomeArquivo,
                                             String textoCompleto,
                                             AnalysisProgressListener progress) {
        progress.update(AnaliseStatus.EXTRAINDO_PDF, 18, "PDF extraído",
                "Texto extraído com sucesso. Preparando uma amostra representativa para a IA.");

        List<String> todosTrechos = chunker.chunk(
                textoCompleto,
                properties.pdf().chunkCharSize(),
                properties.pdf().chunkOverlapChars());

        if (todosTrechos.isEmpty()) {
            throw new IllegalArgumentException("O PDF não contém texto suficiente para análise.");
        }

        List<String> trechos = selecionarTrechosRepresentativos(todosTrechos, MAX_TRECHOS_IA);
        String observacaoAmostragem = todosTrechos.size() > trechos.size()
                ? " Documento grande: " + trechos.size() + " trechos representativos foram selecionados entre "
                  + todosTrechos.size() + " trechos extraídos para manter a análise executável em CPU."
                : "";

        progress.update(AnaliseStatus.ANALISANDO_PARTES, 35,
                "Analisando partes e fatos",
                "Analisando trecho 1 de " + trechos.size() + "." + observacaoAmostragem);

        List<ExtractionResult> resultadosParciais = new ArrayList<>();
        for (int i = 0; i < trechos.size(); i++) {
            int progresso = progressoExtracao(i + 1, trechos.size());
            progress.update(AnaliseStatus.ANALISANDO_PARTES, progresso,
                    "Analisando partes e fatos",
                    "Analisando trecho " + (i + 1) + " de " + trechos.size() + ".");
            resultadosParciais.add(extractionAgent.extrair(trechos.get(i)));
        }

        progress.update(AnaliseStatus.CONSOLIDANDO, 55,
                "Consolidando resultados",
                "Unificando os resultados dos trechos em lotes pequenos.");

        ExtractionResult dadosConsolidados = consolidarHierarquicamente(resultadosParciais, progress);
        String amostraTexto = amostrar(textoCompleto, TAMANHO_AMOSTRA_TEXTO);

        progress.update(AnaliseStatus.CONSOLIDANDO, 62,
                "Gerando resumo", "Produzindo uma visão executiva do processo.");
        String resumo = resumoAgent.resumir(dadosConsolidados, amostraTexto);

        progress.update(AnaliseStatus.CONSOLIDANDO, 67,
                "Verificando inconsistências", "Identificando divergências, lacunas e pontos de atenção.");
        List<InconsistenciaDTO> inconsistencias = inconsistenciaAgent.identificar(dadosConsolidados, amostraTexto);

        progress.update(AnaliseStatus.ANALISANDO_EVIDENCIAS, 72,
                "Analisando evidências", "Organizando evidências e perguntas de investigação.");
        List<GrupoEvidenciaDTO> gruposEvidencia = evidenciaAgent.organizar(dadosConsolidados);

        progress.update(AnaliseStatus.ANALISANDO_EVIDENCIAS, 80,
                "Gerando perguntas de investigação", "Preparando perguntas para revisão do advogado.");
        List<String> perguntas = perguntasAgent.gerar(dadosConsolidados, inconsistencias, resumo);

        progress.update(AnaliseStatus.GERANDO_RELATORIO, 90,
                "Gerando relatório executivo", "Consolidando conclusões, recomendações e próximos passos.");
        RelatorioExecutivoDTO relatorioExecutivo = relatorioExecutivoAgent.gerar(
                nomeArquivo, resumo, dadosConsolidados, inconsistencias, perguntas);

        MetadataDTO metadata = new MetadataDTO(
                nomeArquivo, textoCompleto.length(), trechos.size(), properties.ai().model(), Instant.now());

        return new AnaliseProcessoResponse(
                metadata,
                dadosConsolidados.partes(),
                dadosConsolidados.eventosCronologia(),
                dadosConsolidados.pedidos(),
                dadosConsolidados.decisoes(),
                dadosConsolidados.prazos(),
                dadosConsolidados.documentosImportantes(),
                resumo,
                inconsistencias,
                gruposEvidencia,
                perguntas,
                relatorioExecutivo);
    }

    /**
     * Seleciona deterministicamente trechos no início, no fim e ao longo do
     * documento. Isso evita o comportamento anterior de fazer uma chamada ao
     * Ollama para cada um dos milhares de chunks de um processo extenso.
     */
    private List<String> selecionarTrechosRepresentativos(List<String> todos, int limite) {
        if (todos.size() <= limite) return todos;
        Set<Integer> indices = new LinkedHashSet<>();
        indices.add(0);
        indices.add(1);
        indices.add(2);
        indices.add(todos.size() - 3);
        indices.add(todos.size() - 2);
        indices.add(todos.size() - 1);

        int restantes = limite - indices.size();
        for (int i = 1; i <= restantes; i++) {
            int indice = (int) Math.round((double) i * (todos.size() - 1) / (restantes + 1));
            indices.add(indice);
        }

        return indices.stream().sorted().map(todos::get).toList();
    }

    private ExtractionResult consolidarHierarquicamente(List<ExtractionResult> resultados,
                                                         AnalysisProgressListener progress) {
        if (resultados.size() == 1) return resultados.get(0);

        List<ExtractionResult> nivelAtual = resultados;
        int nivel = 1;
        while (nivelAtual.size() > 1) {
            List<ExtractionResult> proximoNivel = new ArrayList<>();
            int totalLotes = (nivelAtual.size() + TAMANHO_LOTE_CONSOLIDACAO - 1)
                    / TAMANHO_LOTE_CONSOLIDACAO;

            for (int inicio = 0, lote = 1; inicio < nivelAtual.size(); inicio += TAMANHO_LOTE_CONSOLIDACAO, lote++) {
                int fim = Math.min(inicio + TAMANHO_LOTE_CONSOLIDACAO, nivelAtual.size());
                progress.update(AnaliseStatus.CONSOLIDANDO, 55,
                        "Consolidando resultados",
                        "Consolidando lote " + lote + " de " + totalLotes + " (nível " + nivel + ").");
                proximoNivel.add(consolidationAgent.consolidar(nivelAtual.subList(inicio, fim)));
            }
            nivelAtual = proximoNivel;
            nivel++;
        }
        return nivelAtual.get(0);
    }

    private int progressoExtracao(int atual, int total) {
        if (total <= 1) return 50;
        return 35 + (int) Math.round(17.0 * atual / total);
    }

    private String amostrar(String texto, int tamanhoMaximo) {
        if (texto.length() <= tamanhoMaximo) return texto;
        return texto.substring(0, tamanhoMaximo)
                + "\n[...conteúdo truncado para controle de tamanho...]";
    }
}
