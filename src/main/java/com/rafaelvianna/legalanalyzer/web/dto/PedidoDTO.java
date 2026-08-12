package com.rafaelvianna.legalanalyzer.web.dto;

/** Um pedido formulado por alguma das partes. */
public record PedidoDTO(
        String descricaoPedido,
        String parteRequerente,
        String fundamentoLegal,
        String status
) {
}
