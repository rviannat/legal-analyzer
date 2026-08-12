package com.rafaelvianna.legalanalyzer.web.dto.specialized;

import java.util.List;

/** Resultado do Deadline Agent: prazos e eventos importantes extraídos do material. */
public record AgendaPrazosDTO(
        List<PrazoDetalhadoDTO> prazos,
        List<EventoAgendaDTO> eventos,
        List<String> datasAmbiguas,
        String aviso
) {
}
