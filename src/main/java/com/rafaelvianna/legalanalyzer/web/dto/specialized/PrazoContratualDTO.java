package com.rafaelvianna.legalanalyzer.web.dto.specialized;

/** Prazo ou termo previsto no contrato (vigência, renovação, denúncia, pagamento...). */
public record PrazoContratualDTO(
        String clausula,
        String descricao,
        String dataOuTermo,
        String tipo
) {
}
