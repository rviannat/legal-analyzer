package com.rafaelvianna.legalanalyzer.web.dto;

import java.util.List;

/** Um grupo lógico de evidências/documentos organizados por categoria. */
public record GrupoEvidenciaDTO(
        String categoria,
        List<String> documentos,
        String relevanciaProbatoria,
        String observacoes
) {
}
