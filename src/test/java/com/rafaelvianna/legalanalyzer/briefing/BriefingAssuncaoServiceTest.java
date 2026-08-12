package com.rafaelvianna.legalanalyzer.briefing;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.async.AnaliseJob;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import com.rafaelvianna.legalanalyzer.exception.AiClientException;
import com.rafaelvianna.legalanalyzer.pdf.PaginaExtraida;
import com.rafaelvianna.legalanalyzer.rag.IndiceProcesso;
import com.rafaelvianna.legalanalyzer.rag.Passagem;
import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.DecisaoDTO;
import com.rafaelvianna.legalanalyzer.web.dto.DocumentoImportanteDTO;
import com.rafaelvianna.legalanalyzer.web.dto.EventoCronologiaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.GrupoEvidenciaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.InconsistenciaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.MetadataDTO;
import com.rafaelvianna.legalanalyzer.web.dto.ParteDTO;
import com.rafaelvianna.legalanalyzer.web.dto.PedidoDTO;
import com.rafaelvianna.legalanalyzer.web.dto.PrazoDTO;
import com.rafaelvianna.legalanalyzer.web.dto.RelatorioExecutivoDTO;
import com.rafaelvianna.legalanalyzer.web.dto.rag.BriefingAssuncaoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.rag.EvidenciaRastreadaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.rag.PontoAtencaoDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AlegacaoEvidenciaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.DocumentoSuporteDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.MatrizEvidenciasDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O briefing precisa ser montado a partir do que os agentes apuraram, com
 * ponteiros de página verificáveis — e continuar sendo entregue mesmo se o
 * modelo de linguagem falhar.
 */
class BriefingAssuncaoServiceTest {

    private static final String SITUACAO_JSON = """
            {"resumoExecutivo":"Ação de cobrança movida pela Empresa X contra a Empresa Y.",
             "ondeEstamos":"Fase de réplica.",
             "oQueEstaEmJogo":"R$ 45.000,00 e a continuidade do contrato.",
             "proximaAcao":"Apresentar réplica até 20/05/2026.",
             "destaques":["Contestação alega pagamento parcial"]}
            """;

    @Test
    @DisplayName("monta o dossiê completo com partes, linha do tempo e perguntas para o advogado")
    void montaDossieCompleto() {
        BriefingAssuncaoResponse briefing = servico(prompt -> SITUACAO_JSON)
                .gerar(job(), especializada(), indice());

        assertThat(briefing.numeroProcesso()).isEqualTo("0001234-56.2026.8.26.0000");
        assertThat(briefing.partes()).extracting(ParteDTO::papel).contains("Autor", "Réu");
        assertThat(briefing.situacao().resumoExecutivo()).contains("Ação de cobrança");
        assertThat(briefing.perguntasParaOAdvogado())
                .extracting(p -> p.pergunta().toLowerCase())
                .anyMatch(p -> p.contains("notificação extrajudicial"));
    }

    @Test
    @DisplayName("a linha do tempo fica em ordem cronológica, com as decisões incluídas")
    void linhaDoTempoOrdenada() {
        var linha = servico(prompt -> SITUACAO_JSON).gerar(job(), especializada(), indice()).linhaDoTempo();

        assertThat(linha).extracting(e -> e.data())
                .containsSubsequence("10/01/2026", "05/04/2026", "20/05/2026");
    }

    @Test
    @DisplayName("evidência aponta a página do documento localizado no PDF")
    void evidenciaApontaPagina() {
        var evidencias = servico(prompt -> SITUACAO_JSON).gerar(job(), especializada(), indice()).evidencias();

        assertThat(evidencias)
                .filteredOn(e -> e.documento().contains("Documento 17"))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.pagina()).isEqualTo(42);
                    assertThat(e.status()).isEqualTo("LOCALIZADO");
                });
    }

    @Test
    @DisplayName("alegação sem documento vira ponto de atenção, não some do briefing")
    void alegacaoSemDocumentoViraPontoDeAtencao() {
        var briefing = servico(prompt -> SITUACAO_JSON).gerar(job(), especializada(), indice());

        assertThat(briefing.pontosAtencao())
                .extracting(PontoAtencaoDTO::tipo)
                .contains("ALEGACAO_SEM_DOCUMENTO", "CONTRADICAO");
        assertThat(briefing.evidencias())
                .extracting(EvidenciaRastreadaDTO::status)
                .contains("SEM_DOCUMENTO");
    }

    @Test
    @DisplayName("documento não localizado no PDF fica sem página, em vez de receber uma inventada")
    void naoInventaPagina() {
        var evidencias = servico(prompt -> SITUACAO_JSON).gerar(job(), especializada(), indice()).evidencias();

        assertThat(evidencias)
                .filteredOn(e -> e.documento().contains("Laudo"))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.pagina()).isNull();
                    assertThat(e.status()).isEqualTo("NAO_LOCALIZADO_NO_PDF");
                });
    }

    @Test
    @DisplayName("se o modelo falhar, o briefing ainda é entregue com o resumo da análise base")
    void briefingSobreviveAFalhaDoModelo() {
        var briefing = servico(prompt -> {
            throw new AiClientException("modelo indisponível");
        }).gerar(job(), especializada(), indice());

        assertThat(briefing.situacao().resumoExecutivo()).contains("cobrança de parcelas");
        assertThat(briefing.avisos()).anyMatch(a -> a.contains("Não foi possível gerar o resumo executivo"));
        assertThat(briefing.linhaDoTempo()).isNotEmpty();
    }

    @Test
    @DisplayName("sem análise especializada, o briefing avisa o que está faltando")
    void avisaQuandoFaltaEspecializada() {
        var briefing = servico(prompt -> SITUACAO_JSON).gerar(job(), null, indice());

        assertThat(briefing.avisos()).anyMatch(a -> a.contains("Análise especializada não executada"));
        assertThat(briefing.evidencias()).isNotEmpty(); // cai para os grupos de evidência da análise base
    }

    @Test
    @DisplayName("o Markdown traz todas as seções esperadas pelo advogado")
    void markdownCompleto() {
        String md = servico(prompt -> SITUACAO_JSON).gerar(job(), especializada(), indice()).markdown();

        assertThat(md)
                .contains("# Briefing de assunção do caso")
                .contains("## Processo")
                .contains("## Partes")
                .contains("## Situação")
                .contains("## Linha do tempo")
                .contains("## Pontos de atenção")
                .contains("## Evidências")
                .contains("## Perguntas para o advogado");
    }

    // --- montagem do cenário -----------------------------------------------

    private BriefingAssuncaoService servico(FakeAi ai) {
        AppProperties properties = new AppProperties(
                new AppProperties.Ai("ollama", "", "llama3.1:8b", "http://localhost:11434/api/chat",
                        4096, 0.2, 30, 16384, true, "30m"),
                new AppProperties.Pdf(1024, 100, 10),
                new AppProperties.Especializada(16000, 5, 8000),
                AppProperties.LegalResearch.desabilitada(),
                AppProperties.Rag.padrao());
        return new BriefingAssuncaoService(ai, new AiJsonSupport(), properties);
    }

    private AnaliseJob job() {
        AnaliseJob job = new AnaliseJob("analise-1", "processo.pdf");
        job.paginas(List.of(
                new PaginaExtraida(1, "Processo nº 0001234-56.2026.8.26.0000 - Empresa X contra Empresa Y."),
                new PaginaExtraida(42, "Documento 17 - comprovante de pagamento da terceira parcela.")));
        job.textoExtraido("Processo nº 0001234-56.2026.8.26.0000. Documento 17 - comprovante de pagamento.");
        job.numeroProcesso("0001234-56.2026.8.26.0000");
        job.concluir(analiseBase());
        return job;
    }

    private IndiceProcesso indice() {
        return new IndiceProcesso(List.of(
                new Passagem("p1#1", Passagem.Tipo.TEXTO_PROCESSO, "Documento — página 1", 1,
                        "Processo nº 0001234-56.2026.8.26.0000 - Empresa X contra Empresa Y, "
                        + "contrato celebrado em 10/01/2026.", null),
                new Passagem("p42#1", Passagem.Tipo.TEXTO_PROCESSO, "Documento — página 42", 42,
                        "Documento 17 - comprovante de pagamento da terceira parcela.", null)), false);
    }

    private AnaliseProcessoResponse analiseBase() {
        return new AnaliseProcessoResponse(
                new MetadataDTO("processo.pdf", 1000, 2, "llama3.1:8b", java.time.Instant.parse("2026-08-12T19:00:00Z")),
                List.of(new ParteDTO("Empresa X", "Autor", "pessoa jurídica", ""),
                        new ParteDTO("Empresa Y", "Réu", "pessoa jurídica", "")),
                List.of(new EventoCronologiaDTO("10/01/2026", "Contrato celebrado", "pré-processual"),
                        new EventoCronologiaDTO("05/04/2026", "Ação ajuizada", "conhecimento")),
                List.of(new PedidoDTO("Cobrança de R$ 45.000,00", "Empresa X", "art. 476 do CC", "pendente")),
                List.of(new DecisaoDTO("20/05/2026", "Despacho", "Determina réplica", "Juízo da 3ª Vara",
                        "Abre prazo de 15 dias")),
                List.of(new PrazoDTO("20/05/2026", "Prazo para réplica", "alta", "Empresa X")),
                List.of(new DocumentoImportanteDTO("Documento 17", "comprovante", "22/03/2026", "alta")),
                "Ação de cobrança de parcelas contratuais inadimplidas.",
                List.of(new InconsistenciaDTO("O Documento 17 contradiz a informação da petição inicial",
                        "Documento 17 x petição inicial", "alta", "Confrontar valores com o extrato bancário")),
                List.of(new GrupoEvidenciaDTO("Documentos de pagamento", List.of("Documento 17"),
                        "alta", "Comprova pagamento parcial")),
                List.of("O contrato original assinado está disponível?"),
                new RelatorioExecutivoDTO("Relatório", "Visão geral do caso",
                        List.of("Pagamento parcial não impugnado"), List.of("Requerer extrato bancário"),
                        List.of("Apresentar réplica"), "Risco moderado"));
    }

    private AnaliseEspecializadaResponse especializada() {
        MatrizEvidenciasDTO matriz = new MatrizEvidenciasDTO(
                List.of(
                        new AlegacaoEvidenciaDTO("Pagamento da terceira parcela", "Empresa Y", "réu",
                                List.of(new DocumentoSuporteDTO("Documento 17", "fls. 42",
                                        "Comprova o pagamento", "alta")),
                                "sustentada", ""),
                        new AlegacaoEvidenciaDTO("Prejuízo por atraso na entrega", "Empresa X", "autor",
                                List.of(), "não sustentada", ""),
                        new AlegacaoEvidenciaDTO("Vício na prestação do serviço", "Empresa Y", "réu",
                                List.of(new DocumentoSuporteDTO("Laudo técnico particular", "não localizado",
                                        "Descreveria o vício", "media")),
                                "parcialmente sustentada", "")),
                List.of("Não há prova do prejuízo alegado pela autora"),
                List.of("Requerer perícia contábil"),
                "");

        return new AnaliseEspecializadaResponse(
                new MetadataDTO("processo.pdf", 1000, 2, "llama3.1:8b", java.time.Instant.parse("2026-08-12T19:10:00Z")),
                "analise-1", null, null, null, null, matriz, null, List.of(), null,
                List.of("Evidence Agent"), List.of());
    }

    /** AiClient falso: o teste não depende de um modelo em execução. */
    @FunctionalInterface
    private interface FakeAi extends AiClient {
        String responder(String userPrompt);

        @Override
        default String complete(String systemPrompt, String userPrompt) {
            return responder(userPrompt);
        }
    }
}
