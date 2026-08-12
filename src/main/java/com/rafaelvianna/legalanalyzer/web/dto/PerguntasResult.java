package com.rafaelvianna.legalanalyzer.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Resultado bruto retornado pelo agente de perguntas de investigação (tarefa 11). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PerguntasResult(List<String> perguntasInvestigacao) {
}
