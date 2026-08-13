package com.rafaelvianna.legalanalyzer.datajud;

/** Evento da linha do tempo híbrida PDF + fonte oficial. */
public record DataJudTimelineEvento(
        String data,
        String descricao,
        String fase,
        String fonte,
        String correspondencia,
        boolean oficial
) {}
