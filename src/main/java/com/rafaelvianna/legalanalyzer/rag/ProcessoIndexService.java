package com.rafaelvianna.legalanalyzer.rag;

import com.rafaelvianna.legalanalyzer.config.AppProperties;
import com.rafaelvianna.legalanalyzer.pdf.PaginaExtraida;
import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Constrói e guarda o índice de recuperação (RAG) de cada caso.
 *
 * O índice combina duas fontes, e essa combinação é o que diferencia o
 * resultado de um "chat sobre PDF":
 * <ol>
 *   <li><b>texto do PDF por página</b> — permite citar onde conferir;</li>
 *   <li><b>fichas da análise</b> — partes, cronologia, pedidos, decisões,
 *       prazos, inconsistências, evidências e, quando existir, o resultado
 *       dos agentes especializados. São fatos já apurados e revisáveis.</li>
 * </ol>
 */
@Service
public class ProcessoIndexService {

    private static final Logger log = LoggerFactory.getLogger(ProcessoIndexService.class);

    /** Numeração única CNJ: NNNNNNN-DD.AAAA.J.TR.OOOO */
    private static final Pattern NUMERO_CNJ =
            Pattern.compile("\\b\\d{7}-\\d{2}\\.\\d{4}\\.\\d\\.\\d{2}\\.\\d{4}\\b");

    private final EmbeddingClient embeddingClient;
    private final AppProperties properties;
    private final Map<String, IndiceProcesso> indices = new ConcurrentHashMap<>();

    public ProcessoIndexService(EmbeddingClient embeddingClient, AppProperties properties) {
        this.embeddingClient = embeddingClient;
        this.properties = properties;
    }

    public IndiceProcesso indice(String analiseId) {
        return indices.getOrDefault(analiseId, IndiceProcesso.vazio());
    }

    public boolean indexado(String analiseId) {
        return indices.containsKey(analiseId);
    }

    public void remover(String analiseId) {
        indices.remove(analiseId);
    }

    /** Embedding da pergunta do advogado (nulo no modo léxico). */
    public float[] vetorizarConsulta(String consulta) {
        return embeddingClient.embed(consulta);
    }

    /**
     * Constrói o índice do caso. Chamado ao final da análise base e novamente
     * quando a análise especializada termina, para incorporar as novas fichas.
     */
    public IndiceProcesso indexar(String analiseId,
                                  List<PaginaExtraida> paginas,
                                  AnaliseProcessoResponse analiseBase,
                                  AnaliseEspecializadaResponse especializada) {
        AppProperties.Rag cfg = properties.rag() == null ? AppProperties.Rag.padrao() : properties.rag();

        List<Passagem> passagens = new ArrayList<>();
        passagens.addAll(passagensDoTexto(paginas, cfg));
        passagens.addAll(FichasAnalise.montar(analiseBase, especializada));

        if (passagens.size() > cfg.maxPassagensIndexadas()) {
            log.warn("Índice do caso {} truncado de {} para {} passagens (legal-analyzer.rag.max-passagens-indexadas).",
                    analiseId, passagens.size(), cfg.maxPassagensIndexadas());
            passagens = new ArrayList<>(passagens.subList(0, cfg.maxPassagensIndexadas()));
        }

        boolean semantico = false;
        if (embeddingClient.disponivel()) {
            List<float[]> vetores = embeddingClient.embedLote(passagens.stream().map(Passagem::texto).toList());
            List<Passagem> comVetor = new ArrayList<>(passagens.size());
            int gerados = 0;
            for (int i = 0; i < passagens.size(); i++) {
                float[] vetor = i < vetores.size() ? vetores.get(i) : null;
                if (vetor != null) {
                    gerados++;
                }
                comVetor.add(passagens.get(i).comVetor(vetor));
            }
            passagens = comVetor;
            semantico = gerados > 0;
            log.info("Índice do caso {}: {} passagens, {} com embedding.", analiseId, passagens.size(), gerados);
        } else {
            log.info("Índice do caso {}: {} passagens em modo léxico (embeddings desabilitados).",
                    analiseId, passagens.size());
        }

        IndiceProcesso indice = new IndiceProcesso(passagens, semantico);
        indices.put(analiseId, indice);
        return indice;
    }

    /** Divide cada página em passagens, preservando o número da página. */
    private List<Passagem> passagensDoTexto(List<PaginaExtraida> paginas, AppProperties.Rag cfg) {
        if (paginas == null || paginas.isEmpty()) {
            return List.of();
        }
        List<Passagem> passagens = new ArrayList<>();
        for (PaginaExtraida pagina : paginas) {
            List<String> partes = dividir(pagina.texto(), cfg.tamanhoPassagemChars(), cfg.sobreposicaoPassagemChars());
            for (int i = 0; i < partes.size(); i++) {
                String texto = partes.get(i).trim();
                if (texto.length() < 40) {
                    continue; // cabeçalho/rodapé isolado: só polui a busca
                }
                passagens.add(new Passagem(
                        "p" + pagina.numero() + "#" + (i + 1),
                        Passagem.Tipo.TEXTO_PROCESSO,
                        "Documento — página " + pagina.numero(),
                        pagina.numero(),
                        texto,
                        null));
            }
        }
        return passagens;
    }

    private List<String> dividir(String texto, int tamanho, int sobreposicao) {
        List<String> partes = new ArrayList<>();
        if (texto == null || texto.isBlank()) {
            return partes;
        }
        if (texto.length() <= tamanho) {
            partes.add(texto);
            return partes;
        }
        int inicio = 0;
        while (inicio < texto.length()) {
            int fim = Math.min(inicio + tamanho, texto.length());
            if (fim < texto.length()) {
                int quebra = texto.lastIndexOf('\n', fim);
                if (quebra <= inicio) {
                    quebra = texto.lastIndexOf(' ', fim);
                }
                if (quebra > inicio) {
                    fim = quebra;
                }
            }
            partes.add(texto.substring(inicio, fim));
            if (fim >= texto.length()) {
                break;
            }
            inicio = Math.max(fim - sobreposicao, inicio + 1);
        }
        return partes;
    }

    /**
     * Localiza a numeração única CNJ no texto do processo. Só devolve o que
     * está literalmente no documento — nunca um número inferido.
     */
    public static String numeroProcesso(List<PaginaExtraida> paginas, String textoCompleto) {
        if (paginas != null) {
            for (PaginaExtraida pagina : paginas) {
                Matcher m = NUMERO_CNJ.matcher(pagina.texto());
                if (m.find()) {
                    return m.group();
                }
            }
        }
        if (textoCompleto != null) {
            Matcher m = NUMERO_CNJ.matcher(textoCompleto);
            if (m.find()) {
                return m.group();
            }
        }
        return "não identificado";
    }
}
