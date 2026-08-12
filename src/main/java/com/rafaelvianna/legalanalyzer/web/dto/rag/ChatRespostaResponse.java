package com.rafaelvianna.legalanalyzer.web.dto.rag;

import java.util.List;

/**
 * Resposta do chat sobre o processo.
 *
 * @param sessaoId          identificador da conversa (reenviar para manter o contexto)
 * @param pergunta          pergunta feita
 * @param resposta          resposta ancorada no material do caso
 * @param citacoes          fontes efetivamente usadas, com página quando aplicável
 * @param fundamentada      false quando o material não permite responder
 * @param modoRecuperacao   "semantica+lexica" ou "lexica"
 * @param perguntasSugeridas próximos passos de investigação sugeridos
 * @param aviso             lembrete de que a resposta é apoio e exige conferência
 */
public record ChatRespostaResponse(
        String sessaoId,
        String pergunta,
        String resposta,
        List<CitacaoDTO> citacoes,
        boolean fundamentada,
        String modoRecuperacao,
        List<String> perguntasSugeridas,
        String aviso
) {
}
