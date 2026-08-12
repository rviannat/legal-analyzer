package com.rafaelvianna.legalanalyzer.web.dto.rag;

import java.util.List;

/**
 * A "situação" do briefing: o resumo executivo de uma página, escrito para
 * um advogado que acabou de assumir o caso — não um resumo do PDF.
 *
 * @param resumoExecutivo texto corrido de ~1 página
 * @param ondeEstamos     fase atual e o que acabou de acontecer
 * @param oQueEstaEmJogo  risco/valor envolvido em termos práticos
 * @param proximaAcao     a próxima providência mais urgente
 * @param destaques       pontos que o advogado precisa saber já na primeira leitura
 */
public record SituacaoProcessoDTO(
        String resumoExecutivo,
        String ondeEstamos,
        String oQueEstaEmJogo,
        String proximaAcao,
        List<String> destaques
) {
}
