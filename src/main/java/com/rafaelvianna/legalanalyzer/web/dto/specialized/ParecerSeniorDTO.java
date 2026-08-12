package com.rafaelvianna.legalanalyzer.web.dto.specialized;

import java.util.List;

/** Resultado final do Senior Lawyer Agent (agente orquestrador). */
public record ParecerSeniorDTO(
        String titulo,
        String sinteseExecutiva,
        List<String> conclusoes,
        List<String> riscosPrincipais,
        List<String> recomendacoes,
        List<String> proximosPassos,
        List<String> pendenciasParaOAdvogado,
        String divergenciasEntreAgentes,
        String ressalvas
) {
}
