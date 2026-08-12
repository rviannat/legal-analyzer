package com.rafaelvianna.legalanalyzer.chat;

import java.util.List;

/**
 * Formato bruto devolvido pelo modelo no chat, antes da validação das
 * citações. Só vira resposta ao advogado depois que cada marcador é
 * confrontado com as passagens efetivamente recuperadas.
 */
public record RespostaChatIa(
        String resposta,
        List<String> trechosUsados,
        Boolean fundamentada,
        List<String> perguntasSugeridas
) {
}
