package com.rafaelvianna.legalanalyzer.async;

import com.rafaelvianna.legalanalyzer.analysis.specialized.SpecializedAnalysisOrchestrator;
import com.rafaelvianna.legalanalyzer.exception.PdfProcessingException;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaRequest;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.OpcaoAnaliseEspecializadaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.TipoRascunho;
import com.rafaelvianna.legalanalyzer.rag.ProcessoIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Dispara e acompanha a análise especializada. Ela só pode ser iniciada
 * depois que a análise base do mesmo documento estiver concluída — o texto
 * extraído e o resultado base são reaproveitados, sem novo upload do PDF.
 */
@Service
public class AnaliseEspecializadaJobService {

    private static final Logger log = LoggerFactory.getLogger(AnaliseEspecializadaJobService.class);

    /** Nomes dos agentes, na ordem em que participam do fluxo. */
    public static final List<String> AGENTES = List.of(
            "Document Agent", "Process Agent", "Contract Agent", "Deadline Agent",
            "Evidence Agent", "Legal Research Agent", "Drafting Agent", "Senior Lawyer Agent");

    private final AnaliseJobService analiseJobService;
    private final SpecializedAnalysisOrchestrator orchestrator;
    private final ProcessoIndexService indexService;
    private final Executor executor;
    private final Map<String, AnaliseEspecializadaJob> jobs = new ConcurrentHashMap<>();

    public AnaliseEspecializadaJobService(AnaliseJobService analiseJobService,
                                          SpecializedAnalysisOrchestrator orchestrator,
                                          ProcessoIndexService indexService,
                                          Executor legalAnalysisExecutor) {
        this.analiseJobService = analiseJobService;
        this.orchestrator = orchestrator;
        this.indexService = indexService;
        this.executor = legalAnalysisExecutor;
    }

    /** Opção apresentada na resposta da análise base quando ela já está concluída. */
    public OpcaoAnaliseEspecializadaDTO opcao(AnaliseJob analiseBase) {
        if (analiseBase.status() != AnaliseStatus.CONCLUIDO || analiseBase.resultado() == null) {
            return OpcaoAnaliseEspecializadaDTO.indisponivel(
                    "A análise especializada fica disponível quando a análise base for concluída.");
        }
        boolean pesquisaHabilitada = orchestrator.pesquisaJuridicaHabilitada();
        String observacao = pesquisaHabilitada
                ? "Todos os resultados são rascunhos/pareceres de apoio e dependem de revisão do advogado."
                : "Pesquisa jurídica desabilitada: sem fontes autorizadas configuradas, nenhuma legislação ou "
                  + "jurisprudência será citada. Os demais agentes continuam disponíveis.";

        return new OpcaoAnaliseEspecializadaDTO(
                true,
                "/api/v1/processos/analises/" + analiseBase.id() + "/especializada",
                AGENTES,
                Arrays.asList(TipoRascunho.values()),
                pesquisaHabilitada,
                observacao);
    }

    public AnaliseEspecializadaJobResponse iniciar(String analiseBaseId, AnaliseEspecializadaRequest request) {
        AnaliseJob analiseBase = analiseJobService.buscar(analiseBaseId);
        if (analiseBase.status() != AnaliseStatus.CONCLUIDO || analiseBase.resultado() == null) {
            throw new PdfProcessingException(
                    "A análise base ainda não foi concluída (status atual: " + analiseBase.status()
                    + "). Aguarde a conclusão antes de solicitar a análise especializada.");
        }

        String id = UUID.randomUUID().toString();
        AnaliseEspecializadaJob job = new AnaliseEspecializadaJob(id, analiseBaseId, analiseBase.nomeArquivo());
        jobs.put(id, job);

        AnaliseEspecializadaRequest opcoes = request == null ? AnaliseEspecializadaRequest.padrao() : request;
        executor.execute(() -> processar(job, analiseBase, opcoes));
        return AnaliseEspecializadaJobResponse.status(job);
    }

    /**
     * Último resultado especializado concluído para uma análise base.
     *
     * Usado pelo briefing e pelo chat: se os agentes especializados já rodaram,
     * o briefing sai completo (matriz de evidências, prazos detalhados,
     * parecer); se não, sai com aviso do que está faltando.
     */
    public AnaliseEspecializadaResponse ultimoResultadoDaBase(String analiseBaseId) {
        return jobs.values().stream()
                .filter(j -> analiseBaseId.equals(j.analiseBaseId()))
                .filter(j -> j.status() == AnaliseEspecializadaStatus.CONCLUIDO && j.resultado() != null)
                .max(Comparator.comparing(AnaliseEspecializadaJob::atualizadoEm))
                .map(AnaliseEspecializadaJob::resultado)
                .orElse(null);
    }

    public AnaliseEspecializadaJobResponse consultar(String id) {
        AnaliseEspecializadaJob job = jobs.get(id);
        if (job == null) {
            throw new PdfProcessingException("Análise especializada não encontrada: " + id);
        }
        return AnaliseEspecializadaJobResponse.status(job);
    }

    private void processar(AnaliseEspecializadaJob job, AnaliseJob analiseBase, AnaliseEspecializadaRequest opcoes) {
        try {
            var resultado = orchestrator.analisar(
                    analiseBase.id(),
                    analiseBase.nomeArquivo(),
                    analiseBase.textoExtraido(),
                    analiseBase.resultado(),
                    opcoes,
                    job::atualizar);

            // Reindexa o caso incorporando as fichas dos agentes especializados:
            // a partir daqui o chat pode citar cláusulas de risco, prazos
            // detalhados e a matriz de evidências, não só o texto do PDF.
            try {
                indexService.indexar(analiseBase.id(), analiseBase.paginas(),
                        analiseBase.resultado(), resultado);
            } catch (Exception e) {
                log.warn("Falha ao reindexar o caso {} com a análise especializada: {}",
                        analiseBase.id(), e.getMessage());
            }

            job.concluir(resultado);
        } catch (Exception e) {
            job.falhar(e.getMessage() == null
                    ? "Erro inesperado durante a análise especializada." : e.getMessage());
        }
    }
}
