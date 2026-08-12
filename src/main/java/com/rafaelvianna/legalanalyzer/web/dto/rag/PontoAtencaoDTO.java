package com.rafaelvianna.legalanalyzer.web.dto.rag;

/**
 * Um ponto de atenção para quem está assumindo o caso: contradição entre
 * documento e petição, alegação sem documento, decisão relevante escondida
 * no meio dos autos.
 *
 * @param descricao     o problema, em uma frase
 * @param tipo          CONTRADICAO, ALEGACAO_SEM_DOCUMENTO, DECISAO_RELEVANTE, PRAZO_CRITICO ou LACUNA
 * @param gravidade     alta, media ou baixa
 * @param ondeConferir  página ou ficha da análise onde verificar
 * @param recomendacao  próxima ação sugerida
 */
public record PontoAtencaoDTO(
        String descricao,
        String tipo,
        String gravidade,
        String ondeConferir,
        String recomendacao
) {
}
