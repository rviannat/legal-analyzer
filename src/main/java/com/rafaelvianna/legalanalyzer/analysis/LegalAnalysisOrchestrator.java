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
import java.util.List;

/**
 * Orquestra a pipeline completa de agentes de IA sobre o texto extraído de um PDF.
 *
 * A extração é feita em chunks pequenos e o resultado é consolidado de forma
 * hierárquica. Isso evita enviar um processo grande para o llama3.2:3b de uma só
 * vez e permite atualizar o progresso entre chamadas ao Ollama.
 */
@Service
public class LegalAnalysisOrchestrator {

    private static final int TAMANHO_AMOSTRA_TEXTO = 6_000;
    private static final int TAMANHO_LOTE_CONSOLIDACAO = 3;

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
                "Texto extraído com sucesso. Preparando os trechos para a análise.");

        List<String> trechos = chunker.chunk(
                textoCompleto,
                properties.pdf().chunkCharSize(),
                properties.pdf().chunkOverlapChars());

        if (trechos.isEmpty()) {
            throw new IllegalArgumentException("O PDF não contém texto suficiente para análise.");
        }

        progress.update(AnaliseStatus.ANALISANDO_PARTES, 35,
                "Analisando partes e fatos",
                "Analisando trecho 1 de " + trechos.size() + ".");

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
                "Unificando os resultados dos trechos em lotes pequenos para reduzir o consumo de memória.");

        ExtractionResult dadosConsolidados = consolidarHierarquicamente(resultadosParciais, progress);

        String amostraTexto = amostrar(textoCompleto, TAMANHO_AMOSTRA_TEXTO);

        progress.update(AnaliseStatus.CONSOLIDANDO, 62,
                "Gerando resumo",
                "Produzindo uma visão executiva do processo.");
        String resumo = resumoAgent.resumir(dadosConsolidados, amostraTexto);

        progress.update(AnaliseStatus.CONSOLIDANDO, 67,
                "Verificando inconsistências",
                "Identificando divergências, lacunas e pontos de atenção.");
        List<InconsistenciaDTO> inconsistencias = inconsistenciaAgent.identificar(dadosConsolidados, amostraTexto);

        progress.update(AnaliseStatus.ANALISANDO_EVIDENCIAS, 72,
                "Analisando evidências",
                "Organizando evidências e perguntas de investigação.");
        List<GrupoEvidenciaDTO> gruposEvidencia = evidenciaAgent.organizar(dadosConsolidados);

        progress.update(AnaliseStatus.ANALISANDO_EVIDENCIAS, 80,
                "Gerando perguntas de investigação",
                "Preparando perguntas para revisão do advogado.");
        List<String> perguntas = perguntasAgent.gerar(dadosConsolidados, inconsistencias, resumo);

        progress.update(AnaliseStatus.GERANDO_RELATORIO, 90,
                "Gerando relatório executivo",
                "Consolidando conclusões, recomendações e próximos passos.");
        RelatorioExecutivoDTO relatorioExecutivo = relatorioExecutivoAgent.gerar(
                nomeArquivo, resumo, dadosConsolidados, inconsistencias, perguntas);

        MetadataDTO metadata = new MetadataDTO(
                nomeArquivo,
                textoCompleto.length(),
                trechos.size(),
                properties.ai().model(),
                Instant.now());

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
     * Consolida em lotes de no máximo três resultados e repete até restar um.
     * Assim, a consolidação nunca recebe uma lista potencialmente enorme.
     */
    private ExtractionResult consolidarHierarquicamente(List<ExtractionResult> resultados,
                                                         AnalysisProgressListener progress) {
        if (resultados.size() == 1) {
            return resultados.get(0);
        }

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
        if (total <= 1) {
            return 45;
        }
        // Reserva 35-52% para a extração dos chunks e deixa consolidação começar em 55%.
        return 35 + (int) Math.round(17.0 * atual / total);
    }

    private String amostrar(String texto, int tamanhoMaximo) {
        if (texto.length() <= tamanhoMaximo) {
            return texto;
        }
        return texto.substring(0, tamanhoMaximo)
                + "\n[...conteúdo truncado para controle de tamanho...]";
    }
}
