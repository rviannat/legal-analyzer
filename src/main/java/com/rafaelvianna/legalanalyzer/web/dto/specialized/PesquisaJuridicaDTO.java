package com.rafaelvianna.legalanalyzer.web.dto.specialized;

import java.util.List;

/**
 * Resultado do Legal Research Agent. Só contém referências recuperadas de
 * fontes previamente autorizadas (allowlist de domínios); qualquer citação
 * fora dessa lista é descartada antes de chegar aqui.
 */
public record PesquisaJuridicaDTO(
        boolean pesquisaRealizada,
        String consulta,
        String sintese,
        List<ReferenciaJuridicaDTO> referencias,
        List<String> lacunas,
        List<String> fontesConsultadas,
        String aviso
) {
    public static PesquisaJuridicaDTO desabilitada(String motivo) {
        return new PesquisaJuridicaDTO(false, "", "", List.of(), List.of(), List.of(), motivo);
    }
}
