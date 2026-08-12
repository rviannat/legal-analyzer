package com.rafaelvianna.legalanalyzer.web.dto;

/** Uma decisão judicial (sentença, despacho, decisão interlocutória, acórdão etc.). */
public record DecisaoDTO(
        String data,
        String tipoDecisao,
        String resumoDecisao,
        String autoridade,
        String efeitos
) {
}
