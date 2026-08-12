package com.rafaelvianna.legalanalyzer.analysis;

import com.rafaelvianna.legalanalyzer.analysis.agents.ConsolidationAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.EvidenciaAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.ExtractionAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.InconsistenciaAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.PerguntasAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.RelatorioExecutivoAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.ResumoAgent;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import com.rafaelvianna.legalanalyzer.async.AnaliseStatus;
import com.rafaelvianna.legalanalyzer.pdf.PdfTextChunker;
import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.ExtractionResult;
import com.rafaelvianna.legalanalyzer.web.dto.GrupoEvidenciaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.InconsistenciaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.MetadataDTO;
import com.rafaelvianna.legalanalyzer.web.dto.RelatorioExecutivoDTO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Orquestra a pipeline completa de agentes de IA sobre o texto extraído
 * de um PDF de processo jurídico, cobrindo as 12 capacidades solicitadas:
 *
 * <ol>
 *   <li>leitura dos documentos (feita antes, na extração de texto do PDF)</li>
 *   <li>identificação das partes</li>
 *   <li>identificação da cronologia</li>
 *   <li>identificação dos pedidos</li>
 *   <li>identificação das decisões</li>
 *   <li>identificação de prazos/datas relevantes</li>
 *   <li>identificação de documentos importantes</li>
 *   <li>resumo do processo</li>
 *   <li>apontamento de inconsistências</li>
 *   <li>organização de evidências</li>
 *   <li>geração de perguntas de investigação para o advogado</li>
 *   <li>produção de um relatório executivo</li>
 * </ol>
 *
 * Estratégia para documentos longos: o texto é dividido em trechos
 * (chunks); cada trecho passa pelo {@link ExtractionAgent} (tarefas 2-7);
 * os resultados parciais são então unificados pelo {@link ConsolidationAgent}
 * quando há mais de um trecho. As demais tarefas (8-12) operam sobre os
 * dados já consolidados, mantendo o consumo de tokens sob controle.
 */
@Service
public class LegalAnalysisOrchestrator {

    /** Tamanho máximo (em caracteres) da amostra de texto original enviada para tarefas que precisam de contexto textual (resumo/inconsistências). */
    private static final int TAMANHO_AMOSTRA_TEXTO = 12_000;

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

    public AnaliseProcessoResponse analisar(String nomeArquivo, String textoCompleto, AnalysisProgressListener progress) {
        progress.update(AnaliseStatus.EXTRAINDO_PDF, 18, "PDF extraído", "Texto extraído com sucesso. Preparando os trechos para a análise.");

        // Passo 1: dividir o texto em trechos processáveis pelos agentes.
        List<String> trechos = chunker.chunk(
                textoCompleto,
                properties.pdf().chunkCharSize(),
                properties.pdf().chunkOverlapChars());

        progress.update(AnaliseStatus.ANALISANDO_PARTES, 35, "Analisando partes e fatos", "Identificando partes, cronologia, pedidos, decisões, prazos e documentos.");

        // Passos 2-7: extrair partes/cronologia/pedidos/decisões/prazos/documentos de cada trecho.
        List<ExtractionResult> resultadosParciais = trechos.stream()
                .map(extractionAgent::extrair)
                .toList();

        progress.update(AnaliseStatus.CONSOLIDANDO, 55, "Consolidando resultados", "Unificando os resultados dos trechos em uma visão única do processo.");

        // Consolidação: unifica os trechos em um único conjunto de dados coerente.
        ExtractionResult dadosConsolidados = resultadosParciais.size() <= 1
                ? resultadosParciais.get(0)
                : consolidationAgent.consolidar(resultadosParciais);

        String amostraTexto = amostrar(textoCompleto, TAMANHO_AMOSTRA_TEXTO);

        // Passo 8: resumo do processo.
        String resumo = resumoAgent.resumir(dadosConsolidados, amostraTexto);

        // Passo 9: inconsistências.
        List<InconsistenciaDTO> inconsistencias = inconsistenciaAgent.identificar(dadosConsolidados, amostraTexto);

        progress.update(AnaliseStatus.ANALISANDO_EVIDENCIAS, 72, "Analisando evidências", "Organizando evidências, inconsistências e perguntas de investigação.");

        // Passo 10: organização de evidências.
        List<GrupoEvidenciaDTO> gruposEvidencia = evidenciaAgent.organizar(dadosConsolidados);

        // Passo 11: perguntas de investigação para o advogado.
        List<String> perguntas = perguntasAgent.gerar(dadosConsolidados, inconsistencias, resumo);

        progress.update(AnaliseStatus.GERANDO_RELATORIO, 90, "Gerando relatório executivo", "Consolidando conclusões, recomendações e próximos passos.");

        // Passo 12: relatório executivo final, sintetizando tudo.
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

    private String amostrar(String texto, int tamanhoMaximo) {
        if (texto.length() <= tamanhoMaximo) {
            return texto;
        }
        return texto.substring(0, tamanhoMaximo) + "\n[...conteúdo truncado para controle de tamanho...]";
    }
}
