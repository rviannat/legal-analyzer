package com.rafaelvianna.legalanalyzer.web.dto.rag;

/**
 * Pergunta do advogado sobre o caso.
 *
 * @param pergunta  pergunta em linguagem natural
 * @param sessaoId  conversa a continuar; se nulo, uma nova sessão é criada
 */
public record ChatPerguntaRequest(String pergunta, String sessaoId) {
}
