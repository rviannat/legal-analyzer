package com.rafaelvianna.legalanalyzer.datajud;

import java.time.Instant;

/** Movimentação pública retornada pelo DataJud. */
public record DataJudMovimento(
        String dataHora,
        String nome,
        String complemento
) {
    public String textoComparacao() {
        return String.join(" ",
                dataHora == null ? "" : dataHora,
                nome == null ? "" : nome,
                complemento == null ? "" : complemento).trim();
    }

    public Instant dataInstant() {
        if (dataHora == null || dataHora.isBlank()) return null;
        try {
            return Instant.parse(dataHora);
        } catch (Exception ignored) {
            return null;
        }
    }
}
