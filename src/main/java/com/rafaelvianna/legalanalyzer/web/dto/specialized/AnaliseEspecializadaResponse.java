package com.rafaelvianna.legalanalyzer.web.dto.specialized;

import com.rafaelvianna.legalanalyzer.web.dto.MetadataDTO;

import java.util.List;

/**
 * Resultado consolidado da análise especializada. Cada campo corresponde a
 * um agente especializado; {@code parecerSenior} é a saída do Senior Lawyer
 * Agent, que orquestra e sintetiza o trabalho dos demais.
 */
public record AnaliseEspecializadaResponse(
        MetadataDTO metadata,
        String analiseBaseId,
        ClassificacaoDocumentalDTO classificacaoDocumental,
        AnaliseProcessualDTO analiseProcessual,
        AnaliseContratualDTO analiseContratual,
        AgendaPrazosDTO agendaPrazos,
        MatrizEvidenciasDTO matrizEvidencias,
        PesquisaJuridicaDTO pesquisaJuridica,
        List<RascunhoDTO> rascunhos,
        ParecerSeniorDTO parecerSenior,
        List<String> agentesExecutados,
        List<String> avisos
) {
}
