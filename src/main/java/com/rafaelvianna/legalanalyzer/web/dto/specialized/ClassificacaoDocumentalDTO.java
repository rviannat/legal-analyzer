package com.rafaelvianna.legalanalyzer.web.dto.specialized;

import java.util.List;

/**
 * Resultado do Document Agent: classificação automática do material.
 * O campo {@code naturezaPrincipal} orienta o roteamento entre o
 * Process Agent (processo judicial) e o Contract Agent (contrato).
 */
public record ClassificacaoDocumentalDTO(
        String naturezaPrincipal,
        String confianca,
        List<DocumentoClassificadoDTO> documentos,
        List<String> indicios,
        String observacoes
) {
    public boolean pareceContrato() {
        return naturezaPrincipal != null && naturezaPrincipal.toLowerCase().contains("contrat");
    }

    public boolean pareceProcesso() {
        return naturezaPrincipal != null
                && (naturezaPrincipal.toLowerCase().contains("processo")
                    || naturezaPrincipal.toLowerCase().contains("judicial"));
    }
}
