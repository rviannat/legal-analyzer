package com.rafaelvianna.legalanalyzer.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Resultado bruto retornado pelo agente de organização de evidências (tarefa 10). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvidenciaResult(List<GrupoEvidenciaDTO> gruposEvidencia) {
}
