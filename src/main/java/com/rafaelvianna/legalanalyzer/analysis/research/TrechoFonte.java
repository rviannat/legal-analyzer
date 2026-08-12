package com.rafaelvianna.legalanalyzer.analysis.research;

import java.time.Instant;

/**
 * Trecho de conteúdo efetivamente baixado de uma fonte jurídica autorizada.
 * É a única matéria-prima aceita pelo Legal Research Agent — o modelo não
 * pode citar nada que não tenha vindo daqui.
 */
public record TrechoFonte(
        String fonte,
        String url,
        String conteudo,
        Instant consultadoEm
) {
}
