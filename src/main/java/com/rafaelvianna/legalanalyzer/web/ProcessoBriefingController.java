package com.rafaelvianna.legalanalyzer.web;

import com.rafaelvianna.legalanalyzer.async.AnaliseEspecializadaJobService;
import com.rafaelvianna.legalanalyzer.async.AnaliseJob;
import com.rafaelvianna.legalanalyzer.async.AnaliseJobService;
import com.rafaelvianna.legalanalyzer.async.AnaliseStatus;
import com.rafaelvianna.legalanalyzer.briefing.BriefingAssuncaoService;
import com.rafaelvianna.legalanalyzer.chat.ChatProcessoService;
import com.rafaelvianna.legalanalyzer.chat.ChatSessao;
import com.rafaelvianna.legalanalyzer.exception.PdfProcessingException;
import com.rafaelvianna.legalanalyzer.rag.IndiceProcesso;
import com.rafaelvianna.legalanalyzer.rag.ProcessoIndexService;
import com.rafaelvianna.legalanalyzer.web.dto.rag.BriefingAssuncaoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.rag.ChatPerguntaRequest;
import com.rafaelvianna.legalanalyzer.web.dto.rag.ChatRespostaResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Endpoints do briefing de assunção do caso e do chat com o processo.
 *
 * O briefing é o entregável principal: em vez de "resuma este PDF", ele
 * responde "explique este processo para um advogado que acabou de assumir o
 * caso". O chat é o complemento, para as perguntas que sobram depois da
 * leitura do briefing.
 */
@RestController
@RequestMapping("/api/v1/processos")
public class ProcessoBriefingController {

    private final AnaliseJobService jobService;
    private final AnaliseEspecializadaJobService especializadaService;
    private final BriefingAssuncaoService briefingService;
    private final ChatProcessoService chatService;
    private final ProcessoIndexService indexService;

    public ProcessoBriefingController(AnaliseJobService jobService,
                                      AnaliseEspecializadaJobService especializadaService,
                                      BriefingAssuncaoService briefingService,
                                      ChatProcessoService chatService,
                                      ProcessoIndexService indexService) {
        this.jobService = jobService;
        this.especializadaService = especializadaService;
        this.briefingService = briefingService;
        this.chatService = chatService;
        this.indexService = indexService;
    }

    /** Briefing de assunção do caso, em JSON estruturado (inclui a versão Markdown). */
    @GetMapping("/analises/{id}/briefing")
    public ResponseEntity<BriefingAssuncaoResponse> briefing(@PathVariable String id) {
        return ResponseEntity.ok(gerar(id));
    }

    /** O mesmo briefing em Markdown, pronto para colar na pasta do caso ou imprimir. */
    @GetMapping(value = "/analises/{id}/briefing.md", produces = "text/markdown; charset=UTF-8")
    public ResponseEntity<String> briefingMarkdown(@PathVariable String id) {
        return ResponseEntity.ok(gerar(id).markdown());
    }

    /**
     * Pergunta do advogado sobre o processo. A resposta só é entregue se
     * estiver ancorada em trechos do material, com documento e página.
     */
    @PostMapping(value = "/analises/{id}/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatRespostaResponse> chat(@PathVariable String id,
                                                     @RequestBody ChatPerguntaRequest request) {
        return ResponseEntity.ok(chatService.responder(id, request));
    }

    /** Histórico de uma conversa, para a interface recarregar a tela. */
    @GetMapping("/chats/{sessaoId}")
    public ResponseEntity<List<ChatSessao.Troca>> historico(@PathVariable String sessaoId) {
        return ResponseEntity.ok(chatService.historico(sessaoId));
    }

    /** Diagnóstico do índice do caso: quantas passagens e se a busca semântica está ativa. */
    @GetMapping("/analises/{id}/indice")
    public ResponseEntity<Map<String, Object>> indice(@PathVariable String id) {
        IndiceProcesso indice = indexService.indice(id);
        return ResponseEntity.ok(Map.of(
                "analiseId", id,
                "passagensIndexadas", indice.tamanho(),
                "paginasIndexadas", indice.totalPaginasIndexadas(),
                "buscaSemantica", indice.semantico(),
                "modoRecuperacao", indice.semantico() ? "semantica+lexica" : "lexica"));
    }

    private BriefingAssuncaoResponse gerar(String id) {
        AnaliseJob job = jobService.buscar(id);
        if (job.status() != AnaliseStatus.CONCLUIDO || job.resultado() == null) {
            throw new PdfProcessingException(
                    "O briefing fica disponível quando a análise for concluída (status atual: "
                    + job.status() + ").");
        }
        return briefingService.gerar(
                job,
                especializadaService.ultimoResultadoDaBase(id),
                indexService.indice(id));
    }
}
