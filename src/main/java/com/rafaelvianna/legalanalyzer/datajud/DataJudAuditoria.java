package com.rafaelvianna.legalanalyzer.datajud;

import com.rafaelvianna.legalanalyzer.web.dto.ParteDTO;

import java.util.List;

/**
 * Auditoria de consistência entre o documento analisado e os metadados públicos do DataJud.
 *
 * A API Pública do DataJud não disponibiliza os nomes das partes. Por isso, a validação
 * de nomes de autor/réu é explicitamente inconclusiva nesta camada, evitando qualquer
 * falso positivo de fraude.
 */
public record DataJudAuditoria(
        DataJudInfo dataJud,
        boolean capaEnriquecida,
        List<String> camposEnriquecidos,
        String validacaoPartesStatus,
        List<ParteDTO> partesExtraidas,
        List<String> divergencias,
        String observacao
) {
    public static DataJudAuditoria indisponivel(DataJudInfo info, List<ParteDTO> partes) {
        return new DataJudAuditoria(info, false, List.of(), "NAO_DISPONIVEL_NA_API_PUBLICA",
                partes == null ? List.of() : List.copyOf(partes), List.of(),
                "A API Pública do DataJud resguarda os dados das partes. A validação de nomes de autor/réu exige uma fonte oficial que publique esses dados; nenhum nome foi tratado como confirmado ou divergente pelo DataJud.");
    }

    public static DataJudAuditoria de(DataJudInfo info, List<ParteDTO> partes) {
        if (info == null) return indisponivel(DataJudInfo.naoConfigurado(), partes);
        if (!info.encontrado()) return new DataJudAuditoria(info, false, List.of(), "NAO_CONCLUSIVA",
                partes == null ? List.of() : List.copyOf(partes), List.of(),
                "Não há registro público do processo no DataJud para realizar o enriquecimento da capa.");
        return new DataJudAuditoria(info, true,
                List.of("tribunal", "grau", "classe processual", "órgão julgador", "movimentações"),
                "NAO_DISPONIVEL_NA_API_PUBLICA",
                partes == null ? List.of() : List.copyOf(partes), List.of(),
                "Capa enriquecida com metadados oficiais do DataJud. Os nomes das partes são resguardados pela API Pública e, portanto, não foram usados como critério de divergência.");
    }
}
