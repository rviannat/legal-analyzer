package com.rafaelvianna.legalanalyzer.analysis;

import com.rafaelvianna.legalanalyzer.analysis.agents.ConsolidationAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.EvidenciaAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.ExtractionAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.InconsistenciaAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.PerguntasAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.RelatorioExecutivoAgent;
import com.rafaelvianna.legalanalyzer.analysis.agents.ResumoAgent;
import com.rafaelvianna.legalanalyzer.analysis.external.ExternalValidationTeam;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import com.rafaelvianna.legalanalyzer.exception.PdfProcessingException;
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

/** Orquestra a análise documental e a validação externa da Equipe 3. */
@Service
public class LegalAnalysisOrchestrator {
    private static final int TAMANHO_AMOSTRA_TEXTO = 12_000;

    private final PdfTextChunker chunker;
    private final ExtractionAgent extractionAgent;
    private final ConsolidationAgent consolidationAgent;
    private final ResumoAgent resumoAgent;
    private final InconsistenciaAgent inconsistenciaAgent;
    private final EvidenciaAgent evidenciaAgent;
    private final PerguntasAgent perguntasAgent;
    private final RelatorioExecutivoAgent relatorioExecutivoAgent;
    private final ExternalValidationTeam externalValidationTeam;
    private final AppProperties properties;

    public LegalAnalysisOrchestrator(PdfTextChunker chunker,
                                     ExtractionAgent extractionAgent,
                                     ConsolidationAgent consolidationAgent,
                                     ResumoAgent resumoAgent,
                                     InconsistenciaAgent inconsistenciaAgent,
                                     EvidenciaAgent evidenciaAgent,
                                     PerguntasAgent perguntasAgent,
                                     RelatorioExecutivoAgent relatorioExecutivoAgent,
                                     ExternalValidationTeam externalValidationTeam,
                                     AppProperties properties) {
        this.chunker = chunker;
        this.extractionAgent = extractionAgent;
        this.consolidationAgent = consolidationAgent;
        this.resumoAgent = resumoAgent;
        this.inconsistenciaAgent = inconsistenciaAgent;
        this.evidenciaAgent = evidenciaAgent;
        this.perguntasAgent = perguntasAgent;
        this.relatorioExecutivoAgent = relatorioExecutivoAgent;
        this.externalValidationTeam = externalValidationTeam;
        this.properties = properties;
    }

    public AnaliseProcessoResponse analisar(String nomeArquivo, String textoCompleto) {
        return analisar(nomeArquivo, textoCompleto, AnalysisProgressListener.noop());
    }

    public AnaliseProcessoResponse analisar(String nomeArquivo, String textoCompleto, AnalysisProgressListener progress) {
        progress.update(AnaliseStatus.EXTRAINDO_PDF, 18, "PDF extraído", "Texto extraído com sucesso. Preparando os trechos para a análise.");

        List<String> trechos = chunker.chunk(textoCompleto, properties.pdf().chunkCharSize(), properties.pdf().chunkOverlapChars());

        progress.update(AnaliseStatus.ANALISANDO_PARTES, 35, "Analisando partes e fatos", "Identificando partes, cronologia, pedidos, decisões, prazos e documentos.");
        List<ExtractionResult> resultadosParciais = new java.util.ArrayList<>(trechos.size());
        for (int i = 0; i < trechos.size(); i++) {
            resultadosParciais.add(extractionAgent.extrair(trechos.get(i)));
            int concluidos = i + 1;
            progress.update(AnaliseStatus.ANALISANDO_PARTES,
                    progressoEntre(35, 55, concluidos, trechos.size()),
                    "Analisando partes e fatos",
                    "Trecho " + concluidos + " de " + trechos.size() + " analisado.");
        }

        progress.update(AnaliseStatus.CONSOLIDANDO, 55, "Consolidando resultados", "Unificando os resultados dos trechos em uma visão única do processo.");
        if (resultadosParciais.isEmpty()) {
            throw new PdfProcessingException("Nenhum trecho analisável foi gerado a partir do texto do PDF. O documento pode ser uma imagem digitalizada sem OCR.");
        }

        ExtractionResult dadosConsolidados = resultadosParciais.size() == 1
                ? resultadosParciais.get(0)
                : consolidationAgent.consolidar(resultadosParciais,
                (fusoesConcluidas, fusoesTotais, detalhe) -> progress.update(
                        AnaliseStatus.CONSOLIDANDO,
                        progressoEntre(55, 66, fusoesConcluidas, fusoesTotais),
                        "Consolidando resultados", detalhe));

        String amostraTexto = amostrar(textoCompleto, TAMANHO_AMOSTRA_TEXTO);

        progress.update(AnaliseStatus.CONSOLIDANDO, 66, "Resumindo o processo", "Gerando o resumo executivo a partir dos dados consolidados.");
        String resumo = resumoAgent.resumir(dadosConsolidados, amostraTexto);

        progress.update(AnaliseStatus.CONSOLIDANDO, 69, "Verificando inconsistências", "Comparando datas, valores e alegações do processo.");
        List<InconsistenciaDTO> inconsistencias = inconsistenciaAgent.identificar(dadosConsolidados, amostraTexto);

        progress.update(AnaliseStatus.ANALISANDO_EVIDENCIAS, 72, "Analisando evidências", "Organizando evidências, inconsistências e perguntas de investigação.");
        List<GrupoEvidenciaDTO> gruposEvidencia = evidenciaAgent.organizar(dadosConsolidados);

        progress.update(AnaliseStatus.ANALISANDO_EVIDENCIAS, 81, "Gerando perguntas de investigação", "Formulando perguntas a partir das inconsistências e do resumo.");
        List<String> perguntas = perguntasAgent.gerar(dadosConsolidados, inconsistencias, resumo);

        // Equipe 3: uma única consulta oficial é distribuída aos seus agentes e
        // a reunião compara o resultado externo com o contexto consolidado.
        progress.update(AnaliseStatus.ANALISANDO_EVIDENCIAS, 86, "Validando no DataJud", "Equipe 3 confrontando a análise documental com dados processuais oficiais.");
        ExternalValidationTeam.ExternalValidationResult validacaoExterna = externalValidationTeam.execute(textoCompleto, dadosConsolidados);

        progress.update(AnaliseStatus.GERANDO_RELATORIO, 90, "Gerando relatório executivo", "Consolidando conclusões, recomendações e validação externa.");
        RelatorioExecutivoDTO relatorioExecutivo = relatorioExecutivoAgent.gerar(
                nomeArquivo, resumo, dadosConsolidados, inconsistencias, perguntas);

        MetadataDTO metadata = new MetadataDTO(nomeArquivo, textoCompleto.length(), trechos.size(), properties.ai().model(), Instant.now());

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
                relatorioExecutivo,
                validacaoExterna);
    }

    private int progressoEntre(int inicio, int fim, int concluidos, int total) {
        if (total <= 0) return fim;
        double fracao = Math.min(1.0, (double) concluidos / total);
        return inicio + (int) Math.round((fim - inicio) * fracao);
    }

    private String amostrar(String texto, int tamanhoMaximo) {
        if (texto.length() <= tamanhoMaximo) return texto;
        return texto.substring(0, tamanhoMaximo) + "\n[...conteúdo truncado para controle de tamanho...]";
    }
}
