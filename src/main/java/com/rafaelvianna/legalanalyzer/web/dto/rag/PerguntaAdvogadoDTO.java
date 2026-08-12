package com.rafaelvianna.legalanalyzer.web.dto.rag;

/**
 * Pergunta que o sistema devolve ao advogado — o que falta saber para
 * trabalhar o caso, e por quê.
 *
 * @param pergunta   pergunta objetiva
 * @param motivo     por que ela importa (lacuna que ela fecha)
 * @param prioridade alta, media ou baixa
 */
public record PerguntaAdvogadoDTO(
        String pergunta,
        String motivo,
        String prioridade
) {
}
