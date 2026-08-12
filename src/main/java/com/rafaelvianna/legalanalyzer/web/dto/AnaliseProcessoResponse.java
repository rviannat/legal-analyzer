package com.rafaelvianna.legalanalyzer.web.dto;

import java.util.List;

/**
 * Resposta final da API, agregando os resultados das 12 capacidades
 * solicitadas:
 * 1. leitura dos documentos (implícita na extração de texto)
 * 2. identificação das partes            -> partes
 * 3. identificação da cronologia         -> cronologia
 * 4. identificação dos pedidos           -> pedidos
 * 5. identificação das decisões          -> decisoes
 * 6. identificação de prazos/datas       -> prazos
 * 7. identificação de documentos         -> documentosImportantes
 * 8. resumo do processo                  -> resumoProcesso
 * 9. inconsistências                     -> inconsistencias
 * 10. organização de evidências          -> gruposEvidencia
 * 11. perguntas para o advogado investigar -> perguntasInvestigacao
 * 12. relatório executivo                -> relatorioExecutivo
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
