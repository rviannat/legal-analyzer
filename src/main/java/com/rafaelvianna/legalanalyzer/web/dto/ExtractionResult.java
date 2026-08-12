package com.rafaelvianna.legalanalyzer.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Resultado estruturado da extração "bruta" (tarefas 2 a 7: partes,
 * cronologia, pedidos, decisões, prazos e documentos importantes).
 * Usado tanto para o resultado de cada trecho (chunk) quanto para o
 * resultado consolidado do processo inteiro.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractionResult(
        List<ParteDTO> partes,
        List<EventoCronologiaDTO> eventosCronologia,
        List<PedidoDTO> pedidos,
        List<DecisaoDTO> decisoes,
        List<PrazoDTO> prazos,
        List<DocumentoImportanteDTO> documentosImportantes
) {
}
