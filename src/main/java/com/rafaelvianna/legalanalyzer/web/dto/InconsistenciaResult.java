package com.rafaelvianna.legalanalyzer.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Resultado bruto retornado pelo agente de inconsistências (tarefa 9). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InconsistenciaResult(List<InconsistenciaDTO> inconsistencias) {
}
