package com.rafaelvianna.legalanalyzer.web.dto;

import java.util.List;

/** Relatório executivo final, pronto para leitura por um advogado sênior/sócio. */
public record RelatorioExecutivoDTO(
        String titulo,
        String visaoGeral,
        List<String> pontosCriticos,
        List<String> recomendacoes,
        List<String> proximosPassos,
        String conclusao
) {
}
