package com.rafaelvianna.legalanalyzer.web.dto;

import com.rafaelvianna.legalanalyzer.analysis.external.ExternalValidationTeam;

import java.util.List;

/**
 * Resposta final da API, agregando os resultados da análise documental e da
 * validação processual externa da Equipe 3.
 */
public record AnaliseProcessoResponse(
        MetadataDTO metadata,
        List<ParteDTO> partes,
        List<EventoCronologiaDTO> cronologia,
        List<PedidoDTO> pedidos,
        List<DecisaoDTO> decisoes,
        List<PrazoDTO> prazos,
        List<DocumentoImportanteDTO> documentosImportantes,
        String resumoProcesso,
        List<InconsistenciaDTO> inconsistencias,
        List<GrupoEvidenciaDTO> gruposEvidencia,
        List<String> perguntasInvestigacao,
        RelatorioExecutivoDTO relatorioExecutivo,
        ExternalValidationTeam.ExternalValidationResult validacaoExterna
) {
}
