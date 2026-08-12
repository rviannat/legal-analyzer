package com.rafaelvianna.legalanalyzer.web.dto.rag;

import com.rafaelvianna.legalanalyzer.web.dto.ParteDTO;

import java.util.List;

/**
 * Briefing de assunção do caso.
 *
 * Responde à pergunta "explique este processo para um advogado que acabou de
 * assumir o caso", e não "resuma este PDF". A estrutura é fixa e previsível:
 * processo, partes, situação, linha do tempo, pontos de atenção, evidências
 * rastreadas e perguntas para o advogado.
 *
 * @param analiseId              análise base que originou o briefing
 * @param numeroProcesso         numeração única encontrada no documento
 * @param nomeArquivo            arquivo analisado
 * @param geradoEm               data/hora da geração
 * @param partes                 partes e seus papéis
 * @param situacao               resumo executivo de uma página
 * @param linhaDoTempo           eventos em ordem, com onde conferir
 * @param pontosAtencao          contradições, lacunas e decisões relevantes
 * @param evidencias             rastro alegação → documento → página
 * @param perguntasParaOAdvogado o que falta para trabalhar o caso
 * @param avisos                 limitações do briefing (ex.: análise especializada não executada)
 * @param markdown               o mesmo briefing renderizado para leitura/impressão
 */
public record BriefingAssuncaoResponse(
        String analiseId,
        String numeroProcesso,
        String nomeArquivo,
        String geradoEm,
        List<ParteDTO> partes,
        SituacaoProcessoDTO situacao,
        List<EventoLinhaTempoDTO> linhaDoTempo,
        List<PontoAtencaoDTO> pontosAtencao,
        List<EvidenciaRastreadaDTO> evidencias,
        List<PerguntaAdvogadoDTO> perguntasParaOAdvogado,
        List<String> avisos,
        String markdown
) {
}
