package com.rafaelvianna.legalanalyzer.web.dto.specialized;

import java.util.List;

/** Uma alegação relacionada aos documentos que podem sustentá-la. */
public record AlegacaoEvidenciaDTO(
        String alegacao,
        String parteQueAlega,
        String onusDaProva,
        List<DocumentoSuporteDTO> documentosSuporte,
        String grauSustentacao,
        String observacoes
) {
}
