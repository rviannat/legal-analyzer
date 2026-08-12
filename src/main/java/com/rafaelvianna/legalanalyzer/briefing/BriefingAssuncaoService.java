package com.rafaelvianna.legalanalyzer.briefing;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.async.AnaliseJob;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import com.rafaelvianna.legalanalyzer.rag.IndiceProcesso;
import com.rafaelvianna.legalanalyzer.rag.Passagem;
import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.ParteDTO;
import com.rafaelvianna.legalanalyzer.web.dto.rag.BriefingAssuncaoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.rag.EventoLinhaTempoDTO;
import com.rafaelvianna.legalanalyzer.web.dto.rag.EvidenciaRastreadaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.rag.PerguntaAdvogadoDTO;
import com.rafaelvianna.legalanalyzer.web.dto.rag.PontoAtencaoDTO;
import com.rafaelvianna.legalanalyzer.web.dto.rag.SituacaoProcessoDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Monta o briefing de assunção do caso.
 *
 * Filosofia: o briefing é montado de forma determinística a partir do que os
 * agentes já apuraram (partes, cronologia, decisões, prazos, inconsistências,
 * matriz de evidências). O modelo de linguagem escreve apenas a "situação" —
 * o resumo executivo de uma página. Assim, tabelas, ponteiros de página e
 * lacunas não dependem da criatividade do modelo.
 */
@Service
public class BriefingAssuncaoService {

    private static final Logger log = LoggerFactory.getLogger(BriefingAssuncaoService.class);

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("pt", "BR"));

    /** Perguntas de partida que valem para praticamente qualquer caso contratual/cível. */
    private static final List<String> PERGUNTAS_BASE = List.of(
            "O contrato original (via assinada) está disponível?",
            "Houve notificação extrajudicial antes do ajuizamento?",
            "Existe comprovante de pagamento das parcelas discutidas?",
            "Existe comunicação entre as partes posterior ao evento (e-mail, WhatsApp, ata)?");

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;
    private final AppProperties properties;

    public BriefingAssuncaoService(AiClient aiClient, AiJsonSupport jsonSupport, AppProperties properties) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
        this.properties = properties;
    }

    public BriefingAssuncaoResponse gerar(AnaliseJob job,
                                          AnaliseEspecializadaResponse especializada,
                                          IndiceProcesso indice) {
        AnaliseProcessoResponse base = job.resultado();
        List<String> avisos = new ArrayList<>();

        if (especializada == null) {
            avisos.add("Análise especializada não executada: a matriz de evidências e a agenda de prazos "
                    + "detalhada não estão disponíveis. Execute a análise especializada para um briefing completo.");
        }
        if (indice == null || indice.estaVazio()) {
            avisos.add("Índice do caso indisponível: as indicações de página não puderam ser calculadas.");
        }
        avisos.add("Documento de apoio interno gerado automaticamente. Todas as informações, prazos e "
                + "indicações de página devem ser conferidas pelo advogado nos autos antes de qualquer ato.");

        List<ParteDTO> partes = base.partes() == null ? List.of() : base.partes();
        SituacaoProcessoDTO situacao = escreverSituacao(job, base, especializada, avisos);
        List<EventoLinhaTempoDTO> linhaDoTempo = montarLinhaDoTempo(base, indice);
        List<PontoAtencaoDTO> pontosAtencao = montarPontosAtencao(base, especializada, indice);
        List<EvidenciaRastreadaDTO> evidencias = montarEvidencias(base, especializada, indice);
        List<PerguntaAdvogadoDTO> perguntas = montarPerguntas(base, especializada);

        BriefingAssuncaoResponse briefing = new BriefingAssuncaoResponse(
                job.id(),
                job.numeroProcesso(),
                job.nomeArquivo(),
                DATA_HORA.format(java.time.LocalDateTime.now()),
                partes,
                situacao,
                linhaDoTempo,
                pontosAtencao,
                evidencias,
                perguntas,
                List.copyOf(avisos),
                null);

        return new BriefingAssuncaoResponse(
                briefing.analiseId(), briefing.numeroProcesso(), briefing.nomeArquivo(), briefing.geradoEm(),
                briefing.partes(), briefing.situacao(), briefing.linhaDoTempo(), briefing.pontosAtencao(),
                briefing.evidencias(), briefing.perguntasParaOAdvogado(), briefing.avisos(),
                BriefingMarkdownRenderer.render(briefing));
    }

    // --- situação (única parte escrita pelo modelo) -------------------------

    private SituacaoProcessoDTO escreverSituacao(AnaliseJob job,
                                                 AnaliseProcessoResponse base,
                                                 AnaliseEspecializadaResponse especializada,
                                                 List<String> avisos) {
        String fichas = jsonSupport.toJson(new MaterialSituacao(base, especializada));
        String amostra = amostra(job.textoExtraido());
        try {
            String resposta = aiClient.complete(
                    BriefingPromptTemplates.SISTEMA_SITUACAO,
                    BriefingPromptTemplates.usuarioSituacao(
                            job.nomeArquivo(), job.numeroProcesso(), fichas, amostra));
            SituacaoProcessoDTO situacao = jsonSupport.parse(resposta, SituacaoProcessoDTO.class);
            if (situacao != null && situacao.resumoExecutivo() != null && !situacao.resumoExecutivo().isBlank()) {
                return situacao;
            }
            avisos.add("O resumo executivo veio vazio do modelo; foi usado o resumo da análise base.");
        } catch (Exception e) {
            log.warn("Falha ao gerar a situação do briefing: {}", e.getMessage());
            avisos.add("Não foi possível gerar o resumo executivo com o modelo (" + e.getMessage()
                    + "). O briefing traz o resumo da análise base.");
        }
        return situacaoDeReserva(base);
    }

    /** Sem o modelo, o briefing ainda é entregue com o que a análise base apurou. */
    private SituacaoProcessoDTO situacaoDeReserva(AnaliseProcessoResponse base) {
        String resumo = base.resumoProcesso() == null || base.resumoProcesso().isBlank()
                ? "Resumo não disponível."
                : base.resumoProcesso();
        List<String> destaques = new ArrayList<>();
        if (base.relatorioExecutivo() != null && base.relatorioExecutivo().pontosCriticos() != null) {
            destaques.addAll(base.relatorioExecutivo().pontosCriticos());
        }
        String proximaAcao = base.relatorioExecutivo() != null
                && base.relatorioExecutivo().proximosPassos() != null
                && !base.relatorioExecutivo().proximosPassos().isEmpty()
                ? base.relatorioExecutivo().proximosPassos().get(0)
                : "não consta no material";
        return new SituacaoProcessoDTO(resumo, "não consta no material", "não consta no material",
                proximaAcao, List.copyOf(destaques));
    }

    private String amostra(String texto) {
        if (texto == null) {
            return "";
        }
        int limite = properties.especializada() == null
                ? 16_000 : properties.especializada().amostraTextoCharsOuPadrao();
        return texto.length() <= limite ? texto : texto.substring(0, limite);
    }

    // --- linha do tempo ----------------------------------------------------

    private List<EventoLinhaTempoDTO> montarLinhaDoTempo(AnaliseProcessoResponse base, IndiceProcesso indice) {
        List<EventoLinhaTempoDTO> eventos = new ArrayList<>();

        if (base.cronologia() != null) {
            for (var evento : base.cronologia()) {
                eventos.add(new EventoLinhaTempoDTO(
                        valor(evento.data()), valor(evento.descricaoEvento()), valor(evento.fase()),
                        ondeConferir(indice, evento.data(), evento.descricaoEvento())));
            }
        }
        // Decisões entram na linha do tempo: quem assume o caso precisa vê-las em ordem.
        if (base.decisoes() != null) {
            for (var decisao : base.decisoes()) {
                eventos.add(new EventoLinhaTempoDTO(
                        valor(decisao.data()),
                        "%s: %s".formatted(valor(decisao.tipoDecisao()), valor(decisao.resumoDecisao())),
                        "decisão",
                        ondeConferir(indice, decisao.data(), decisao.tipoDecisao())));
            }
        }

        eventos.sort(Comparator.comparing(e -> ordenavel(e.data())));
        return List.copyOf(eventos);
    }

    /**
     * Chave de ordenação: datas reconhecidas viram ISO; o que não é data vai
     * para o fim, sem sumir da tabela.
     */
    static String ordenavel(String data) {
        LocalDate parsed = interpretarData(data);
        return parsed == null ? "9999-99-99" : parsed.toString();
    }

    static LocalDate interpretarData(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        String limpo = data.trim();
        var m = java.util.regex.Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})").matcher(limpo);
        if (m.find()) {
            return tentar(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        }
        m = java.util.regex.Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})").matcher(limpo);
        if (m.find()) {
            return tentar(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        }
        return null;
    }

    private static LocalDate tentar(int ano, int mes, int dia) {
        try {
            return LocalDate.of(ano, mes, dia);
        } catch (Exception e) {
            return null;
        }
    }

    /** Procura no índice a página onde o termo aparece literalmente. */
    private String ondeConferir(IndiceProcesso indice, String... termos) {
        if (indice == null || indice.estaVazio()) {
            return "conferir nos autos";
        }
        for (String termo : termos) {
            Optional<Passagem> achado = indice.localizarTermo(termo);
            if (achado.isPresent() && achado.get().pagina() != null) {
                return "página " + achado.get().pagina();
            }
        }
        return "conferir nos autos";
    }

    // --- pontos de atenção -------------------------------------------------

    private List<PontoAtencaoDTO> montarPontosAtencao(AnaliseProcessoResponse base,
                                                      AnaliseEspecializadaResponse especializada,
                                                      IndiceProcesso indice) {
        List<PontoAtencaoDTO> pontos = new ArrayList<>();

        if (base.inconsistencias() != null) {
            for (var inc : base.inconsistencias()) {
                pontos.add(new PontoAtencaoDTO(
                        valor(inc.descricao()), "CONTRADICAO", valor(inc.gravidade()),
                        ondeConferir(indice, inc.elementosConflitantes()), valor(inc.recomendacao())));
            }
        }

        if (base.prazos() != null) {
            for (var prazo : base.prazos()) {
                if (prazo.criticidade() != null && prazo.criticidade().toLowerCase(Locale.ROOT).contains("alta")) {
                    pontos.add(new PontoAtencaoDTO(
                            "Prazo crítico em %s: %s".formatted(valor(prazo.data()), valor(prazo.descricaoPrazo())),
                            "PRAZO_CRITICO", "alta",
                            ondeConferir(indice, prazo.data(), prazo.descricaoPrazo()),
                            "Confirmar a contagem do prazo nos autos e no sistema do tribunal."));
                }
            }
        }

        if (base.decisoes() != null) {
            for (var decisao : base.decisoes()) {
                if (decisao.efeitos() != null && !decisao.efeitos().isBlank()) {
                    pontos.add(new PontoAtencaoDTO(
                            "Existe decisão relevante (%s, %s): %s".formatted(
                                    valor(decisao.tipoDecisao()), valor(decisao.data()), valor(decisao.resumoDecisao())),
                            "DECISAO_RELEVANTE", "alta",
                            ondeConferir(indice, decisao.data(), decisao.tipoDecisao()),
                            "Verificar efeitos: " + valor(decisao.efeitos())));
                }
            }
        }

        if (especializada != null && especializada.matrizEvidencias() != null) {
            var matriz = especializada.matrizEvidencias();
            if (matriz.alegacoes() != null) {
                for (var alegacao : matriz.alegacoes()) {
                    boolean semDocumento = alegacao.documentosSuporte() == null
                            || alegacao.documentosSuporte().isEmpty();
                    if (semDocumento) {
                        pontos.add(new PontoAtencaoDTO(
                                "Alegação sem documento associado: \"%s\" (%s)".formatted(
                                        valor(alegacao.alegacao()), valor(alegacao.parteQueAlega())),
                                "ALEGACAO_SEM_DOCUMENTO", "alta",
                                "matriz de evidências",
                                "Ônus da prova: %s. Localizar prova ou requerer produção.".formatted(
                                        valor(alegacao.onusDaProva()))));
                    }
                }
            }
            if (matriz.lacunasProbatorias() != null) {
                for (String lacuna : matriz.lacunasProbatorias()) {
                    pontos.add(new PontoAtencaoDTO(valor(lacuna), "LACUNA", "media",
                            "matriz de evidências", "Avaliar produção de prova complementar."));
                }
            }
        }

        if (especializada != null && especializada.analiseContratual() != null
                && especializada.analiseContratual().contratoIdentificado()
                && especializada.analiseContratual().clausulasRisco() != null) {
            for (var clausula : especializada.analiseContratual().clausulasRisco()) {
                pontos.add(new PontoAtencaoDTO(
                        "Cláusula de risco %s: %s".formatted(valor(clausula.clausula()), valor(clausula.risco())),
                        "CONTRADICAO".equals(clausula.gravidade()) ? "CONTRADICAO" : "CLAUSULA_DE_RISCO",
                        valor(clausula.gravidade()),
                        ondeConferir(indice, clausula.trechoCitado(), clausula.clausula()),
                        valor(clausula.recomendacao())));
            }
        }

        // Alta gravidade primeiro: é o que o advogado precisa ver antes de tudo.
        pontos.sort(Comparator.comparingInt(p -> switch (p.gravidade() == null
                ? "" : p.gravidade().toLowerCase(Locale.ROOT)) {
            case "alta", "critica", "crítica" -> 0;
            case "media", "média" -> 1;
            default -> 2;
        }));
        return List.copyOf(pontos);
    }

    // --- evidências rastreadas ---------------------------------------------

    private List<EvidenciaRastreadaDTO> montarEvidencias(AnaliseProcessoResponse base,
                                                         AnaliseEspecializadaResponse especializada,
                                                         IndiceProcesso indice) {
        List<EvidenciaRastreadaDTO> evidencias = new ArrayList<>();

        if (especializada != null && especializada.matrizEvidencias() != null
                && especializada.matrizEvidencias().alegacoes() != null) {
            for (var alegacao : especializada.matrizEvidencias().alegacoes()) {
                if (alegacao.documentosSuporte() == null || alegacao.documentosSuporte().isEmpty()) {
                    evidencias.add(new EvidenciaRastreadaDTO(
                            valor(alegacao.alegacao()), valor(alegacao.parteQueAlega()),
                            "nenhum documento associado", null,
                            "Sem documento indicado no material analisado.",
                            valor(alegacao.grauSustentacao()), "SEM_DOCUMENTO"));
                    continue;
                }
                for (var documento : alegacao.documentosSuporte()) {
                    Integer pagina = localizarPagina(indice, documento.nomeDocumento(), documento.localizacao());
                    evidencias.add(new EvidenciaRastreadaDTO(
                            valor(alegacao.alegacao()), valor(alegacao.parteQueAlega()),
                            valor(documento.nomeDocumento()), pagina,
                            valor(documento.comoSustenta()), valor(documento.forcaProbatoria()),
                            pagina == null ? "NAO_LOCALIZADO_NO_PDF" : "LOCALIZADO"));
                }
            }
            return List.copyOf(evidencias);
        }

        // Sem análise especializada, usa os grupos de evidência da análise base.
        if (base.gruposEvidencia() != null) {
            for (var grupo : base.gruposEvidencia()) {
                if (grupo.documentos() == null) {
                    continue;
                }
                for (String documento : grupo.documentos()) {
                    Integer pagina = localizarPagina(indice, documento);
                    evidencias.add(new EvidenciaRastreadaDTO(
                            valor(grupo.categoria()), "não identificado", valor(documento), pagina,
                            valor(grupo.observacoes()), valor(grupo.relevanciaProbatoria()),
                            pagina == null ? "NAO_LOCALIZADO_NO_PDF" : "LOCALIZADO"));
                }
            }
        }
        return List.copyOf(evidencias);
    }

    private Integer localizarPagina(IndiceProcesso indice, String... termos) {
        if (indice == null || indice.estaVazio()) {
            return null;
        }
        for (String termo : termos) {
            Optional<Passagem> achado = indice.localizarTermo(termo);
            if (achado.isPresent()) {
                return achado.get().pagina();
            }
        }
        return null;
    }

    // --- perguntas para o advogado -----------------------------------------

    private List<PerguntaAdvogadoDTO> montarPerguntas(AnaliseProcessoResponse base,
                                                      AnaliseEspecializadaResponse especializada) {
        List<PerguntaAdvogadoDTO> perguntas = new ArrayList<>();
        Set<String> jaIncluidas = new LinkedHashSet<>();

        if (base.perguntasInvestigacao() != null) {
            for (String pergunta : base.perguntasInvestigacao()) {
                adicionar(perguntas, jaIncluidas, pergunta,
                        "Lacuna identificada na análise do processo.", "alta");
            }
        }
        if (especializada != null && especializada.parecerSenior() != null
                && especializada.parecerSenior().pendenciasParaOAdvogado() != null) {
            for (String pendencia : especializada.parecerSenior().pendenciasParaOAdvogado()) {
                adicionar(perguntas, jaIncluidas, pendencia,
                        "Pendência apontada pelo parecer consolidado.", "alta");
            }
        }
        if (especializada != null && especializada.matrizEvidencias() != null
                && especializada.matrizEvidencias().provasSugeridas() != null) {
            for (String prova : especializada.matrizEvidencias().provasSugeridas()) {
                adicionar(perguntas, jaIncluidas, prova,
                        "Prova sugerida para sustentar alegação sem documento.", "media");
            }
        }
        for (String pergunta : PERGUNTAS_BASE) {
            adicionar(perguntas, jaIncluidas, pergunta,
                    "Verificação padrão de documentação na assunção do caso.", "media");
        }
        return List.copyOf(perguntas);
    }

    private void adicionar(List<PerguntaAdvogadoDTO> perguntas, Set<String> jaIncluidas,
                           String pergunta, String motivo, String prioridade) {
        if (pergunta == null || pergunta.isBlank()) {
            return;
        }
        String chave = IndiceProcesso.normalizar(pergunta).replaceAll("[^a-z0-9]+", " ").trim();
        if (jaIncluidas.add(chave)) {
            perguntas.add(new PerguntaAdvogadoDTO(pergunta.trim(), motivo, prioridade));
        }
    }

    private static String valor(String texto) {
        return texto == null || texto.isBlank() ? "não identificado" : texto.trim();
    }

    /** Recorte enviado ao modelo para escrever a situação. */
    private record MaterialSituacao(AnaliseProcessoResponse analiseBase,
                                    AnaliseEspecializadaResponse analiseEspecializada) {
    }
}
