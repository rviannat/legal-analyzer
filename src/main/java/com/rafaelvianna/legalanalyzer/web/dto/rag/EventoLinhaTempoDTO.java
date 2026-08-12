package com.rafaelvianna.legalanalyzer.web.dto.rag;

/**
 * Uma linha da linha do tempo do briefing.
 *
 * @param data        data do evento como consta no processo
 * @param evento      o que aconteceu
 * @param fase        fase processual, quando identificada
 * @param ondeConferir página do documento em que o evento aparece, quando localizável
 */
public record EventoLinhaTempoDTO(
        String data,
        String evento,
        String fase,
        String ondeConferir
) {
}
