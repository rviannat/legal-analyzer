package com.rafaelvianna.legalanalyzer.web.dto;

import java.util.List;

/**
 * Resposta final da análise documental da Equipe 1 (extração, consolidação,
 * resumo, inconsistências, evidências e relatório executivo).
 *
 * A validação processual oficial (DataJud/Equipe 3) não faz parte desta
 * resposta: ela roda como etapa separada e opcional, orquestrada por
 * AnaliseEspecializadaJobService, para não acoplar a análise documental a uma
 * fonte externa que pode estar indisponível ou não ter o processo indexado.
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
        RelatorioExecutivoDTO relatorioExecutivo
) {
}