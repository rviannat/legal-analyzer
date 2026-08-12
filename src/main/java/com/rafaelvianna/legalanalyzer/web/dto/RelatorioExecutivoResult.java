package com.rafaelvianna.legalanalyzer.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Resultado bruto retornado pelo agente de relatório executivo (tarefa 12). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RelatorioExecutivoResult(RelatorioExecutivoDTO relatorioExecutivo) {
}
