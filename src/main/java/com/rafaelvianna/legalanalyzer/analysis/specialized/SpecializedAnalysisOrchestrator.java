package com.rafaelvianna.legalanalyzer.analysis.specialized;

import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.ContractAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.DeadlineAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.DocumentAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.DraftingAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.EvidenceAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.LegalResearchAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.ProcessAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.SeniorLawyerAgent;
import com.rafaelvianna.legalanalyzer.async.AnaliseEspecializadaStatus;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.MetadataDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AgendaPrazosDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseContratualDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaRequest;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseProcessualDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.ClassificacaoDocumentalDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.MatrizEvidenciasDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.ParecerSeniorDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.PesquisaJuridicaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.RascunhoDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.TipoRascunho;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orquestra a análise especializada, executada por opção do advogado depois
 * que a análise base do processo foi concluída.
 *
 * Ordem de execução:
 * <ol>
 *   <li><b>Document Agent</b> classifica o material e define o roteamento;</li>
 *   <li><b>Process Agent</b> e/ou <b>Contract Agent</b> conforme a classificação
 *       (ou conforme os flags {@code forcarProcesso}/{@code forcarContrato});</li>
 *   <li><b>Deadline Agent</b> monta a agenda de prazos e eventos;</li>
 *   <li><b>Evidence Agent</b> cruza alegações e documentos;</li>
 *   <li><b>Legal Research Agent</b> pesquisa só em fontes autorizadas (opcional);</li>
 *   <li><b>Drafting Agent</b> gera os rascunhos solicitados (opcional);</li>
 *   <li><b>Senior Lawyer Agent</b> consolida tudo no resultado final.</li>
 * </ol>
 *
 * Falha de um agente especialista não derruba a análise: o erro é registrado
 * em {@code avisos} e o Senior Lawyer trata o item como lacuna.
 */
@Service
public class SpecializedAnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SpecializedAnalysisOrchestrator.class);

    private final DocumentAgent documentAgent;
    private final ProcessAgent processAgent;
    private final ContractAgent contractAgent;
    private final DeadlineAgent deadlineAgent;
    private final EvidenceAgent evidenceAgent;
    private final LegalResearchAgent legalResearchAgent;
    private final DraftingAgent draftingAgent;
    private final SeniorLawyerAgent seniorLawyerAgent;
    private final AppProperties properties;

    public SpecializedAnalysisOrchestrator(DocumentAgent documentAgent,
                                           ProcessAgent processAgent,
                                           ContractAgent contractAgent,
                                           DeadlineAgent deadlineAgent,
                                           EvidenceAgent evidenceAgent,
                                           LegalResearchAgent legalResearchAgent,
                                           DraftingAgent draftingAgent,
                                           SeniorLawyerAgent seniorLawyerAgent,
                                           AppProperties properties) {
        this.documentAgent = documentAgent;
        this.processAgent = processAgent;
        this.contractAgent = contractAgent;
        this.deadlineAgent = deadlineAgent;
        this.evidenceAgent = evidenceAgent;
        this.legalResearchAgent = legalResearchAgent;
        this.draftingAgent = draftingAgent;
        this.seniorLawyerAgent = seniorLawyerAgent;
        this.properties = properties;
    }

    public boolean pesquisaJuridicaHabilitada() {
        return legalResearchAgent.disponivel();
    }

    public AnaliseEspecializadaResponse analisar(String analiseBaseId,
                                                 String nomeArquivo,
                                                 String textoExtraido,
                                                 AnaliseProcessoResponse analiseBase,
                                                 AnaliseEspecializadaRequest request,
                                                 SpecializedProgressListener listener) {
        AnaliseEspecializadaRequest opcoes = request == null ? AnaliseEspecializadaRequest.padrao() : request;
        List<String> agentesExecutados = new ArrayList<>();
        List<String> avisos = new ArrayList<>();

        String amostraTexto = amostra(textoExtraido);
        String parteRepresentada = opcoes.parteRepresentadaOuNaoInformada();
        String contexto = opcoes.contextoOuVazio();

        // 1. Document Agent — classifica e orienta o roteamento.
        listener.update(AnaliseEspecializadaStatus.CLASSIFICANDO_DOCUMENTOS, 10,
                "Classificando documentos", "Document Agent identificando a natureza do material.");
        ClassificacaoDocumentalDTO classificacao = executar("Document Agent", agentesExecutados, avisos,
                () -> documentAgent.classificar(nomeArquivo, analiseBase.documentosImportantes(), amostraTexto),
                () -> new ClassificacaoDocumentalDTO("não identificado", "baixa", List.of(), List.of(),
                        "Classificação não disponível."));

        boolean rodarProcesso = opcoes.forcarProcesso() || classificacao.pareceProcesso();
        boolean rodarContrato = opcoes.forcarContrato() || classificacao.pareceContrato();
        if (!rodarProcesso && !rodarContrato) {
            // Sem sinal claro: por segurança, trata como processo (fluxo principal da aplicação).
            rodarProcesso = true;
            avisos.add("A classificação não indicou claramente processo nem contrato; a análise processual "
                    + "foi executada por padrão.");
        }

        // 2. Process Agent.
        AnaliseProcessualDTO analiseProcessual;
        if (rodarProcesso) {
            listener.update(AnaliseEspecializadaStatus.ANALISANDO_PROCESSO, 25,
                    "Analisando o processo", "Process Agent avaliando teses, riscos e estratégia.");
            analiseProcessual = executar("Process Agent", agentesExecutados, avisos,
                    () -> processAgent.analisar(analiseBase, parteRepresentada, contexto, amostraTexto),
                    () -> AnaliseProcessualDTO.naoAplicavel("Process Agent não concluiu a análise."));
        } else {
            analiseProcessual = AnaliseProcessualDTO.naoAplicavel(
                    "Material não classificado como processo judicial; use forcarProcesso=true para executar.");
        }

        // 3. Contract Agent.
        AnaliseContratualDTO analiseContratual;
        if (rodarContrato) {
            listener.update(AnaliseEspecializadaStatus.ANALISANDO_CONTRATO, 40,
                    "Analisando o contrato", "Contract Agent mapeando cláusulas de risco, multas e obrigações.");
            analiseContratual = executar("Contract Agent", agentesExecutados, avisos,
                    () -> contractAgent.analisar(parteRepresentada, contexto, amostraTexto),
                    () -> AnaliseContratualDTO.naoAplicavel("Contract Agent não concluiu a análise."));
        } else {
            analiseContratual = AnaliseContratualDTO.naoAplicavel(
                    "Material não classificado como contrato; use forcarContrato=true para executar.");
        }

        // 4. Deadline Agent.
        listener.update(AnaliseEspecializadaStatus.MAPEANDO_PRAZOS, 55,
                "Mapeando prazos", "Deadline Agent extraindo datas e eventos importantes.");
        AgendaPrazosDTO agendaPrazos = executar("Deadline Agent", agentesExecutados, avisos,
                () -> deadlineAgent.montarAgenda(analiseBase, amostraTexto),
                () -> new AgendaPrazosDTO(List.of(), List.of(), List.of(),
                        "Agenda de prazos não disponível: o Deadline Agent falhou."));

        // 5. Evidence Agent.
        listener.update(AnaliseEspecializadaStatus.CRUZANDO_EVIDENCIAS, 65,
                "Cruzando evidências", "Evidence Agent relacionando alegações e documentos.");
        MatrizEvidenciasDTO matrizEvidencias = executar("Evidence Agent", agentesExecutados, avisos,
                () -> evidenceAgent.relacionar(analiseBase, amostraTexto),
                () -> new MatrizEvidenciasDTO(List.of(), List.of(), List.of(),
                        "Matriz de evidências não disponível: o Evidence Agent falhou."));

        // 6. Legal Research Agent — apenas fontes autorizadas e rastreáveis.
        PesquisaJuridicaDTO pesquisaJuridica;
        if (opcoes.pesquisaJuridica()) {
            listener.update(AnaliseEspecializadaStatus.PESQUISANDO_FONTES, 75,
                    "Pesquisando fontes autorizadas", "Legal Research Agent consultando as fontes configuradas.");
            String consulta = opcoes.consultaPesquisa() == null || opcoes.consultaPesquisa().isBlank()
                    ? legalResearchAgent.derivarConsulta(analiseBase.resumoProcesso(), analiseBase.pedidos())
                    : opcoes.consultaPesquisa();
            final String consultaFinal = consulta;
            pesquisaJuridica = executar("Legal Research Agent", agentesExecutados, avisos,
                    () -> legalResearchAgent.pesquisar(consultaFinal),
                    () -> PesquisaJuridicaDTO.desabilitada("Legal Research Agent falhou; nenhuma referência foi citada."));
            if (!pesquisaJuridica.pesquisaRealizada()) {
                avisos.add("Pesquisa jurídica não produziu referências verificadas: " + pesquisaJuridica.aviso());
            }
        } else {
            pesquisaJuridica = PesquisaJuridicaDTO.desabilitada("Pesquisa jurídica não solicitada nesta análise.");
        }

        // 7. Drafting Agent — rascunhos sempre para revisão do advogado.
        List<RascunhoDTO> rascunhos = new ArrayList<>();
        List<TipoRascunho> solicitados = limitarRascunhos(opcoes.rascunhosSolicitados(), avisos);
        if (!solicitados.isEmpty()) {
            listener.update(AnaliseEspecializadaStatus.REDIGINDO_RASCUNHOS, 85,
                    "Redigindo rascunhos", "Drafting Agent produzindo os documentos solicitados.");
            ContextoCaso contextoCaso = new ContextoCaso(nomeArquivo, analiseBase.resumoProcesso(), classificacao,
                    analiseProcessual, analiseContratual, agendaPrazos, matrizEvidencias, pesquisaJuridica);
            int maxChars = properties.especializada() == null ? 8_000
                    : Math.max(2_000, properties.especializada().maxCharsRascunho());
            for (TipoRascunho tipo : solicitados) {
                try {
                    rascunhos.add(draftingAgent.redigir(tipo, contextoCaso, parteRepresentada, contexto, maxChars));
                } catch (RuntimeException e) {
                    log.warn("Drafting Agent falhou para o rascunho {}: {}", tipo, e.getMessage());
                    avisos.add("Rascunho de " + tipo.name() + " não foi gerado: " + mensagem(e));
                }
            }
            if (!rascunhos.isEmpty()) {
                agentesExecutados.add("Drafting Agent");
            }
        }

        // 8. Senior Lawyer Agent — orquestrador, produz o resultado final.
        listener.update(AnaliseEspecializadaStatus.PARECER_SENIOR, 95,
                "Consolidando parecer", "Senior Lawyer Agent revisando o trabalho dos demais agentes.");
        MaterialAgentes material = new MaterialAgentes(analiseBase.resumoProcesso(), classificacao, analiseProcessual,
                analiseContratual, agendaPrazos, matrizEvidencias, pesquisaJuridica,
                rascunhos.stream().map(RascunhoDTO::titulo).toList(), agentesExecutados, avisos);
        ParecerSeniorDTO parecerSenior = executar("Senior Lawyer Agent", agentesExecutados, avisos,
                () -> seniorLawyerAgent.consolidar(nomeArquivo, parteRepresentada, material),
                () -> new ParecerSeniorDTO("Parecer não consolidado", "", List.of(), List.of(), List.of(), List.of(),
                        List.of("O Senior Lawyer Agent falhou; revise manualmente os resultados dos demais agentes."),
                        "não avaliado",
                        "Consolidação final indisponível. Os resultados dos agentes especializados seguem no corpo da resposta."));

        MetadataDTO metadata = new MetadataDTO(
                nomeArquivo,
                textoExtraido == null ? 0 : textoExtraido.length(),
                analiseBase.metadata() == null ? 0 : analiseBase.metadata().quantidadeTrechosProcessados(),
                properties.ai().model(),
                Instant.now());

        return new AnaliseEspecializadaResponse(metadata, analiseBaseId, classificacao, analiseProcessual,
                analiseContratual, agendaPrazos, matrizEvidencias, pesquisaJuridica, List.copyOf(rascunhos),
                parecerSenior, List.copyOf(agentesExecutados), List.copyOf(avisos));
    }

    private List<TipoRascunho> limitarRascunhos(List<TipoRascunho> solicitados, List<String> avisos) {
        int limite = properties.especializada() == null ? 5 : properties.especializada().maxRascunhosOuPadrao();
        if (solicitados.size() <= limite) {
            return solicitados;
        }
        avisos.add("Foram solicitados " + solicitados.size() + " rascunhos; o limite configurado é " + limite
                + ". Os excedentes foram ignorados.");
        return solicitados.subList(0, limite);
    }

    private String amostra(String texto) {
        if (texto == null || texto.isBlank()) {
            return "não identificado";
        }
        int limite = properties.especializada() == null ? 16_000
                : properties.especializada().amostraTextoCharsOuPadrao();
        return texto.length() <= limite ? texto
                : texto.substring(0, limite) + "\n[...texto truncado para a análise especializada...]";
    }

    /** Executa um agente isolando falhas: registra o aviso e devolve o fallback. */
    private <T> T executar(String nomeAgente, List<String> executados, List<String> avisos,
                           java.util.function.Supplier<T> acao, java.util.function.Supplier<T> fallback) {
        try {
            T resultado = acao.get();
            executados.add(nomeAgente);
            return resultado;
        } catch (RuntimeException e) {
            log.warn("{} falhou: {}", nomeAgente, e.getMessage());
            avisos.add(nomeAgente + " não concluiu: " + mensagem(e));
            return fallback.get();
        }
    }

    private String mensagem(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /** Material enviado ao Drafting Agent. */
    private record ContextoCaso(String nomeArquivo, String resumoBase, ClassificacaoDocumentalDTO classificacao,
                                AnaliseProcessualDTO analiseProcessual, AnaliseContratualDTO analiseContratual,
                                AgendaPrazosDTO agendaPrazos, MatrizEvidenciasDTO matrizEvidencias,
                                PesquisaJuridicaDTO pesquisaJuridica) {
    }

    /** Material enviado ao Senior Lawyer Agent. */
    private record MaterialAgentes(String resumoBase, ClassificacaoDocumentalDTO classificacaoDocumental,
                                   AnaliseProcessualDTO analiseProcessual, AnaliseContratualDTO analiseContratual,
                                   AgendaPrazosDTO agendaPrazos, MatrizEvidenciasDTO matrizEvidencias,
                                   PesquisaJuridicaDTO pesquisaJuridica, List<String> rascunhosGerados,
                                   List<String> agentesExecutados, List<String> avisos) {
    }
}
