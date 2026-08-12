package com.rafaelvianna.legalanalyzer.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Resultado bruto retornado pelo agente de resumo (tarefa 8). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumoResult(String resumoProcesso) {
}
