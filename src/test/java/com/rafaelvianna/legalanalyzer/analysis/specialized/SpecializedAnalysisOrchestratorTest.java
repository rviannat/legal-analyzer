package com.rafaelvianna.legalanalyzer.analysis.specialized;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.research.LegalSourceProvider;
import com.rafaelvianna.legalanalyzer.analysis.research.TrechoFonte;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.ContractAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.DeadlineAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.DocumentAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.DraftingAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.EvidenceAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.LegalResearchAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.ProcessAgent;
import com.rafaelvianna.legalanalyzer.analysis.specialized.agents.SeniorLawyerAgent;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.MetadataDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaRequest;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.TipoRascunho;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o roteamento e as travas de segurança da análise especializada com um
 * {@link AiClient} falso — sem depender de um modelo real.
 */
class SpecializedAnalysisOrchestratorTest {

    private static final String CLASSIFICACAO_PROCESSO = """
            {"naturezaPrincipal":"processo judicial","confianca":"alta",
             "documentos":[{"nomeDocumento":"Petição inicial","categoria":"petição inicial","subtipo":"",
                            "dataDocumento":"2024-01-10","confianca":"alta","justificativa":"endereçamento ao juízo"}],
             "indicios":["Excelentíssimo Senhor Doutor Juiz"],"observacoes":""}
            """;

    private static final String CLASSIFICACAO_CONTRATO = """
            {"naturezaPrincipal":"contrato","confianca":"alta","documentos":[],
             "indicios":["cláusula de vigência"],"observacoes":""}
            """;

    @Test
    @DisplayName("classificado como processo: roda o Process Agent e não o Contract Agent")
    void roteiaParaProcesso() {
        FakeAiClient ai = new FakeAiClient(CLASSIFICACAO_PROCESSO);
        AnaliseEspecializadaResponse resposta = orquestrador(ai, pesquisaDesabilitada())
                .analisar("base-1", "processo.pdf", "texto do processo", analiseBase(),
                        AnaliseEspecializadaRequest.padrao(), SpecializedProgressListener.noop());

        assertThat(resposta.agentesExecutados())
                .contains("Document Agent", "Process Agent", "Deadline Agent", "Evidence Agent", "Senior Lawyer Agent")
                .doesNotContain("Contract Agent");
        assertThat(resposta.analiseProcessual().processoIdentificado()).isTrue();
        assertThat(resposta.analiseContratual().contratoIdentificado()).isFalse();
        assertThat(resposta.analiseBaseId()).isEqualTo("base-1");
    }

    @Test
    @DisplayName("classificado como contrato: roda o Contract Agent e não o Process Agent")
    void roteiaParaContrato() {
        FakeAiClient ai = new FakeAiClient(CLASSIFICACAO_CONTRATO);
        AnaliseEspecializadaResponse resposta = orquestrador(ai, pesquisaDesabilitada())
                .analisar("base-2", "contrato.pdf", "texto do contrato", analiseBase(),
                        AnaliseEspecializadaRequest.padrao(), SpecializedProgressListener.noop());

        assertThat(resposta.agentesExecutados()).contains("Contract Agent").doesNotContain("Process Agent");
        assertThat(resposta.analiseContratual().contratoIdentificado()).isTrue();
        assertThat(resposta.analiseContratual().clausulasRisco()).isNotEmpty();
    }

    @Test
    @DisplayName("forcarContrato executa os dois agentes de análise")
    void forcarContratoExecutaOsDois() {
        FakeAiClient ai = new FakeAiClient(CLASSIFICACAO_PROCESSO);
        var request = new AnaliseEspecializadaRequest(List.of(), false, null, false, true, "defender o réu", "Réu Ltda");

        AnaliseEspecializadaResponse resposta = orquestrador(ai, pesquisaDesabilitada())
                .analisar("base-3", "misto.pdf", "texto", analiseBase(), request, SpecializedProgressListener.noop());

        assertThat(resposta.agentesExecutados()).contains("Process Agent", "Contract Agent");
    }

    @Test
    @DisplayName("pesquisa desabilitada não gera nenhuma referência jurídica")
    void pesquisaDesabilitadaNaoCita() {
        FakeAiClient ai = new FakeAiClient(CLASSIFICACAO_PROCESSO);
        var request = new AnaliseEspecializadaRequest(List.of(), true, "ônus da prova", false, false, null, null);

        AnaliseEspecializadaResponse resposta = orquestrador(ai, pesquisaDesabilitada())
                .analisar("base-4", "processo.pdf", "texto", analiseBase(), request, SpecializedProgressListener.noop());

        assertThat(resposta.pesquisaJuridica().pesquisaRealizada()).isFalse();
        assertThat(resposta.pesquisaJuridica().referencias()).isEmpty();
        assertThat(resposta.avisos()).anyMatch(a -> a.contains("Pesquisa jurídica não produziu referências"));
    }

    @Test
    @DisplayName("referência com URL fora das fontes consultadas é descartada")
    void descartaReferenciaNaoRastreavel() {
        FakeAiClient ai = new FakeAiClient(CLASSIFICACAO_PROCESSO);
        var request = new AnaliseEspecializadaRequest(List.of(), true, "ônus da prova", false, false, null, null);

        AnaliseEspecializadaResponse resposta = orquestrador(ai, pesquisaHabilitada())
                .analisar("base-5", "processo.pdf", "texto", analiseBase(), request, SpecializedProgressListener.noop());

        assertThat(resposta.pesquisaJuridica().pesquisaRealizada()).isTrue();
        // O modelo falso devolve duas referências: uma da fonte autorizada e uma inventada.
        assertThat(resposta.pesquisaJuridica().referencias()).hasSize(1);
        assertThat(resposta.pesquisaJuridica().referencias().get(0).url())
                .isEqualTo("https://www.lexml.gov.br/busca/search?keyword=onus");
        assertThat(resposta.pesquisaJuridica().referencias().get(0).verificada()).isTrue();
        assertThat(resposta.pesquisaJuridica().lacunas())
                .anyMatch(l -> l.contains("descartadas"));
    }

    @Test
    @DisplayName("rascunhos saem com aviso de revisão obrigatória do advogado")
    void rascunhosComAvisoDeRevisao() {
        FakeAiClient ai = new FakeAiClient(CLASSIFICACAO_PROCESSO);
        var request = new AnaliseEspecializadaRequest(
                List.of(TipoRascunho.PARECER, TipoRascunho.EMAIL_CLIENTE), false, null, false, false, null, "Autor S/A");

        AnaliseEspecializadaResponse resposta = orquestrador(ai, pesquisaDesabilitada())
                .analisar("base-6", "processo.pdf", "texto", analiseBase(), request, SpecializedProgressListener.noop());

        assertThat(resposta.rascunhos()).hasSize(2);
        assertThat(resposta.rascunhos()).allSatisfy(r ->
                assertThat(r.avisoRevisao()).contains("REVISÃO E APROVAÇÃO DO ADVOGADO RESPONSÁVEL"));
        assertThat(resposta.agentesExecutados()).contains("Drafting Agent");
    }

    @Test
    @DisplayName("falha de um agente não derruba a análise: vira aviso")
    void falhaDeAgenteViraAviso() {
        FakeAiClient ai = new FakeAiClient(CLASSIFICACAO_PROCESSO);
        ai.falharEm("Process Agent");

        AnaliseEspecializadaResponse resposta = orquestrador(ai, pesquisaDesabilitada())
                .analisar("base-7", "processo.pdf", "texto", analiseBase(),
                        AnaliseEspecializadaRequest.padrao(), SpecializedProgressListener.noop());

        assertThat(resposta.agentesExecutados()).doesNotContain("Process Agent");
        assertThat(resposta.avisos()).anyMatch(a -> a.startsWith("Process Agent não concluiu"));
        assertThat(resposta.parecerSenior()).isNotNull();
    }

    // --- infraestrutura do teste -------------------------------------------------

    private SpecializedAnalysisOrchestrator orquestrador(AiClient ai, LegalSourceProvider provider) {
        AiJsonSupport json = new AiJsonSupport();
        AppProperties properties = new AppProperties(
                new AppProperties.Ai("ollama", "", "llama3.1:8b", "http://localhost:11434/api/chat",
                        4096, 0.2, 30, 16384, true, "30m"),
                new AppProperties.Pdf(1024, 100, 10),
                new AppProperties.Especializada(16000, 5, 8000),
                AppProperties.LegalResearch.desabilitada(),
                AppProperties.Rag.padrao());

        return new SpecializedAnalysisOrchestrator(
                new DocumentAgent(ai, json),
                new ProcessAgent(ai, json),
                new ContractAgent(ai, json),
                new DeadlineAgent(ai, json),
                new EvidenceAgent(ai, json),
                new LegalResearchAgent(ai, json, provider),
                new DraftingAgent(ai, json),
                new SeniorLawyerAgent(ai, json),
                properties);
    }

    private LegalSourceProvider pesquisaDesabilitada() {
        return new LegalSourceProvider() {
            @Override public boolean habilitado() { return false; }
            @Override public List<String> fontesConfiguradas() { return List.of(); }
            @Override public List<TrechoFonte> buscar(String consulta) { return List.of(); }
        };
    }

    private LegalSourceProvider pesquisaHabilitada() {
        return new LegalSourceProvider() {
            @Override public boolean habilitado() { return true; }
            @Override public List<String> fontesConfiguradas() { return List.of("LexML"); }
            @Override public List<TrechoFonte> buscar(String consulta) {
                return List.of(new TrechoFonte("LexML",
                        "https://www.lexml.gov.br/busca/search?keyword=onus",
                        "CPC, art. 373, II — ônus da prova do réu.", Instant.now()));
            }
        };
    }

    private AnaliseProcessoResponse analiseBase() {
        return new AnaliseProcessoResponse(
                new MetadataDTO("processo.pdf", 1000, 2, "llama3.1:8b", Instant.now()),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                "Ação de cobrança movida por Autor S/A contra Réu Ltda.",
                List.of(), List.of(), List.of(), null);
    }

    /** AiClient falso: escolhe a resposta pelo prompt de sistema recebido. */
    private static final class FakeAiClient implements AiClient {
        private final String classificacao;
        private final List<String> falhas = new ArrayList<>();

        private FakeAiClient(String classificacao) {
            this.classificacao = classificacao;
        }

        void falharEm(String agente) {
            falhas.add(agente);
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            if (systemPrompt.contains("Document Agent")) {
                verificarFalha("Document Agent");
                return classificacao;
            }
            if (systemPrompt.contains("Process Agent")) {
                verificarFalha("Process Agent");
                return """
                        {"processoIdentificado":true,"faseAtual":"conhecimento","riscoGeral":"médio",
                         "teseAutor":"cobrança de valores","teseReu":"inexigibilidade",
                         "pontosControvertidos":["existência da dívida"],"forcas":["contrato assinado"],
                         "fragilidades":["ausência de comprovante"],"estrategiaSugerida":["juntar extratos"],
                         "prognostico":"depende de prova documental","observacoes":""}
                        """;
            }
            if (systemPrompt.contains("Contract Agent")) {
                verificarFalha("Contract Agent");
                return """
                        {"contratoIdentificado":true,"objetoContrato":"prestação de serviços",
                         "partesContratantes":"Autor S/A e Réu Ltda",
                         "clausulasRisco":[{"clausula":"7.2","trechoCitado":"multa de 30%","risco":"multa elevada",
                                            "gravidade":"alta","impacto":"custo","recomendacao":"renegociar"}],
                         "obrigacoes":[],"multas":[],"prazos":[],"condicoes":[],"inconsistencias":[],"observacoes":""}
                        """;
            }
            if (systemPrompt.contains("Deadline Agent")) {
                verificarFalha("Deadline Agent");
                return """
                        {"prazos":[{"descricao":"contestação","dataInicio":"2024-02-01","dataFinal":"2024-02-16",
                                    "prazoEmDias":"15","tipoContagem":"dias úteis","fundamento":"CPC art. 335",
                                    "criticidade":"alta","parteResponsavel":"Réu Ltda"}],
                         "eventos":[],"datasAmbiguas":[],"aviso":""}
                        """;
            }
            if (systemPrompt.contains("Evidence Agent")) {
                verificarFalha("Evidence Agent");
                return """
                        {"alegacoes":[{"alegacao":"dívida existe","parteQueAlega":"Autor S/A","onusDaProva":"autor",
                                       "documentosSuporte":[],"grauSustentacao":"não sustentada","observacoes":""}],
                         "lacunasProbatorias":["falta comprovante de entrega"],
                         "provasSugeridas":["solicitar notas fiscais"],"observacoes":""}
                        """;
            }
            if (systemPrompt.contains("Legal Research Agent")) {
                verificarFalha("Legal Research Agent");
                if (userPrompt.startsWith("Formule UMA consulta")) {
                    return "{\"consulta\":\"ônus da prova cobrança CPC\"}";
                }
                return """
                        {"sintese":"O ônus da prova cabe a quem alega.",
                         "referencias":[
                           {"tipo":"legislação","identificacao":"CPC, art. 373, II","fonte":"LexML",
                            "url":"https://www.lexml.gov.br/busca/search?keyword=onus",
                            "trechoRelevante":"CPC, art. 373, II — ônus da prova do réu."},
                           {"tipo":"jurisprudência","identificacao":"REsp inventado","fonte":"memória do modelo",
                            "url":"https://exemplo-invalido.com/resp","trechoRelevante":"citação não rastreável"}],
                         "lacunas":[]}
                        """;
            }
            if (systemPrompt.contains("Drafting Agent")) {
                verificarFalha("Drafting Agent");
                return """
                        {"titulo":"Rascunho de teste","conteudo":"Texto do rascunho [COMPLETAR].",
                         "pontosDeAtencao":["revisar fundamentos"],"lacunasParaPreencher":["número do processo"]}
                        """;
            }
            verificarFalha("Senior Lawyer Agent");
            return """
                    {"titulo":"Parecer consolidado","sinteseExecutiva":"Caso com risco médio.",
                     "conclusoes":["prova documental é decisiva"],"riscosPrincipais":["multa contratual - alta"],
                     "recomendacoes":["juntar extratos"],"proximosPassos":["contatar o cliente"],
                     "pendenciasParaOAdvogado":["conferir prazo no tribunal"],
                     "divergenciasEntreAgentes":"nenhuma","ressalvas":""}
                    """;
        }

        private void verificarFalha(String agente) {
            if (falhas.contains(agente)) {
                throw new IllegalStateException("falha simulada em " + agente);
            }
        }
    }
}
