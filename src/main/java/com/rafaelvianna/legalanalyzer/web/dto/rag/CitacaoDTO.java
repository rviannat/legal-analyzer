package com.rafaelvianna.legalanalyzer.web.dto.rag;

/**
 * Fonte usada em uma resposta do chat. Cada citação aponta para uma passagem
 * que realmente foi recuperada — citações que o modelo inventar são
 * descartadas antes de a resposta chegar ao advogado.
 *
 * @param rotulo    como a fonte é identificada (ex.: "Documento — página 42")
 * @param pagina    página do PDF, quando a fonte é o texto do processo
 * @param origem    TEXTO_PROCESSO ou FICHA_ANALISE
 * @param trecho    recorte do conteúdo citado, para conferência rápida
 */
public record CitacaoDTO(String rotulo, Integer pagina, String origem, String trecho) {
}
