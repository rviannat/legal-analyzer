package com.rafaelvianna.legalanalyzer.chat;

import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.async.AnaliseJob;
import com.rafaelvianna.legalanalyzer.async.AnaliseJobService;
import com.rafaelvianna.legalanalyzer.async.AnaliseStatus;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import com.rafaelvianna.legalanalyzer.exception.PdfProcessingException;
import com.rafaelvianna.legalanalyzer.rag.IndiceProcesso;
import com.rafaelvianna.legalanalyzer.rag.PassagemRecuperada;
import com.rafaelvianna.legalanalyzer.rag.ProcessoIndexService;
import com.rafaelvianna.legalanalyzer.web.dto.rag.ChatPerguntaRequest;
import com.rafaelvianna.legalanalyzer.web.dto.rag.ChatRespostaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat do advogado com o processo, ancorado no índice do caso.
 *
 * O que diferencia este chat de um "converse com seu PDF":
 * <ul>
 *   <li>o contexto vem de passagens recuperadas do caso, com página;</li>
 *   <li>o modelo é obrigado a citar o marcador de cada trecho usado;</li>
 *   <li>marcadores inventados são removidos e, se nenhum sobrar, a resposta é
 *       rebaixada para "não consta no material analisado" — o sistema prefere
 *       admitir a lacuna a arriscar uma citação falsa.</li>
 * </ul>
 */
@Service
public class ChatProcessoService {

    private static final Logger log = LoggerFactory.getLogger(ChatProcessoService.class);

    private static final String AVISO =
            "Resposta de apoio gerada automaticamente a partir do material analisado. "
            + "Confira cada citação nos autos antes de utilizá-la em ato processual.";

    private static final String SEM_FUNDAMENTO =
            "Não consta no material analisado.";

    private final AnaliseJobService analiseJobService;
    private final ProcessoIndexService indexService;
    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;
    private final AppProperties properties;
    private final Map<String, ChatSessao> sessoes = new ConcurrentHashMap<>();

    public ChatProcessoService(AnaliseJobService analiseJobService,
                               ProcessoIndexService indexService,
                               AiClient aiClient,
                               AiJsonSupport jsonSupport,
                               AppProperties properties) {
        this.analiseJobService = analiseJobService;
        this.indexService = indexService;
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
        this.properties = properties;
    }

    public ChatRespostaResponse responder(String analiseId, ChatPerguntaRequest request) {
        if (request == null || request.pergunta() == null || request.pergunta().isBlank()) {
            throw new PdfProcessingException("Informe a pergunta no campo \"pergunta\".");
        }
        AnaliseJob job = analiseJobService.buscar(analiseId);
        if (job.status() != AnaliseStatus.CONCLUIDO || job.resultado() == null) {
            throw new PdfProcessingException(
                    "O chat fica disponível quando a análise for concluída (status atual: " + job.status() + ").");
        }

        IndiceProcesso indice = indexService.indice(analiseId);
        if (indice.estaVazio()) {
            throw new PdfProcessingException(
                    "O índice deste caso não está disponível. Reenvie o documento para reindexação.");
        }

        AppProperties.Rag cfg = properties.rag() == null ? AppProperties.Rag.padrao() : properties.rag();
        ChatSessao sessao = sessao(request.sessaoId(), analiseId);
        String pergunta = request.pergunta().trim();

        List<PassagemRecuperada> recuperadas = indice.buscar(
                pergunta,
                indexService.vetorizarConsulta(pergunta),
                cfg.maxPassagensPorResposta(),
                cfg.scoreMinimo());

        String modo = indice.semantico() ? "semantica+lexica" : "lexica";

        if (recuperadas.isEmpty()) {
            String resposta = SEM_FUNDAMENTO + " Nenhum trecho do processo corresponde à pergunta — "
                    + "tente termos que apareçam no documento (nome da parte, número de cláusula, data).";
            sessao.registrar(pergunta, resposta);
            return new ChatRespostaResponse(sessao.id(), pergunta, resposta, List.of(), false, modo,
                    List.of(), AVISO);
        }

        // Marcadores T1..Tn: é por eles que a citação do modelo é verificada.
        Map<String, PassagemRecuperada> porMarcador = new LinkedHashMap<>();
        StringBuilder contexto = new StringBuilder();
        for (int i = 0; i < recuperadas.size(); i++) {
            String marcador = "T" + (i + 1);
            PassagemRecuperada item = recuperadas.get(i);
            porMarcador.put(marcador, item);
            contexto.append("[").append(marcador).append("] ")
                    .append(item.passagem().citacao()).append('\n')
                    .append(item.passagem().texto()).append("\n\n");
        }

        RespostaChatIa bruta;
        try {
            String saida = aiClient.complete(
                    ChatPromptTemplates.SISTEMA,
                    ChatPromptTemplates.usuario(job.numeroProcesso(),
                            sessao.historicoFormatado(cfg.maxMensagensHistorico()),
                            contexto.toString(), pergunta));
            bruta = jsonSupport.parse(saida, RespostaChatIa.class);
        } catch (Exception e) {
            log.warn("Falha ao responder no chat do caso {}: {}", analiseId, e.getMessage());
            throw new PdfProcessingException("Não foi possível gerar a resposta: " + e.getMessage(), e);
        }

        return validarEMontar(sessao, pergunta, bruta, porMarcador, modo);
    }

    /**
     * Monta a resposta final a partir do resultado da validação de ancoragem.
     * Sem citação verificável, a resposta é substituída pela admissão de lacuna.
     */
    private ChatRespostaResponse validarEMontar(ChatSessao sessao,
                                                String pergunta,
                                                RespostaChatIa bruta,
                                                Map<String, PassagemRecuperada> porMarcador,
                                                String modo) {
        ValidadorAncoragem.Resultado validado = ValidadorAncoragem.validar(bruta, porMarcador);

        if (!validado.marcadoresRemovidos().isEmpty()) {
            log.debug("Citações inexistentes removidas da resposta: {}", validado.marcadoresRemovidos());
        }

        if (!validado.fundamentada()) {
            boolean modeloAdmitiuLacuna = bruta != null && Boolean.FALSE.equals(bruta.fundamentada());
            String motivo = modeloAdmitiuLacuna
                    ? " O material analisado não responde a essa pergunta."
                    : " A resposta gerada não indicou trecho verificável do processo, então foi descartada.";
            String resposta = SEM_FUNDAMENTO + motivo
                    + " Verifique diretamente nos autos ou solicite o documento correspondente ao cliente.";
            sessao.registrar(pergunta, resposta);
            return new ChatRespostaResponse(sessao.id(), pergunta, resposta, List.of(), false, modo,
                    sugestoes(bruta), AVISO);
        }

        sessao.registrar(pergunta, validado.texto());
        return new ChatRespostaResponse(sessao.id(), pergunta, validado.texto(), validado.citacoes(),
                true, modo, sugestoes(bruta), AVISO);
    }

    private List<String> sugestoes(RespostaChatIa bruta) {
        if (bruta == null || bruta.perguntasSugeridas() == null) {
            return List.of();
        }
        return bruta.perguntasSugeridas().stream()
                .filter(p -> p != null && !p.isBlank())
                .limit(3)
                .toList();
    }

    private String recortar(String texto) {
        String limpo = texto.replace('\n', ' ').trim();
        return limpo.length() <= 320 ? limpo : limpo.substring(0, 320) + "...";
    }

    private ChatSessao sessao(String sessaoId, String analiseId) {
        if (sessaoId != null && !sessaoId.isBlank()) {
            ChatSessao existente = sessoes.get(sessaoId);
            if (existente != null && existente.analiseId().equals(analiseId)) {
                return existente;
            }
        }
        String id = UUID.randomUUID().toString();
        ChatSessao nova = new ChatSessao(id, analiseId);
        sessoes.put(id, nova);
        return nova;
    }

    /** Histórico de uma conversa, para a interface reconstruir a tela. */
    public List<ChatSessao.Troca> historico(String sessaoId) {
        ChatSessao sessao = sessoes.get(sessaoId);
        if (sessao == null) {
            throw new PdfProcessingException("Conversa não encontrada: " + sessaoId);
        }
        return sessao.trocas();
    }
}
