package com.rafaelvianna.legalanalyzer.web.dto.specialized;

import java.util.List;

/**
 * Opções da análise especializada, disparada depois que a análise base do
 * processo já foi concluída.
 *
 * @param rascunhos        tipos de rascunho que o Drafting Agent deve produzir (opcional)
 * @param pesquisaJuridica se o Legal Research Agent deve ser acionado
 * @param consultaPesquisa pergunta de pesquisa; se vazia, é derivada do caso
 * @param forcarProcesso   força o Process Agent, mesmo que a classificação não indique processo
 * @param forcarContrato   força o Contract Agent, mesmo que a classificação não indique contrato
 * @param contextoAdicional contexto do advogado (parte que representamos, objetivo, tese preferida)
 * @param parteRepresentada parte cujo interesse deve orientar risco/estratégia/rascunhos
 */
public record AnaliseEspecializadaRequest(
        List<TipoRascunho> rascunhos,
        boolean pesquisaJuridica,
        String consultaPesquisa,
        boolean forcarProcesso,
        boolean forcarContrato,
        String contextoAdicional,
        String parteRepresentada
) {
    public List<TipoRascunho> rascunhosSolicitados() {
        return rascunhos == null ? List.of() : rascunhos.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    public String contextoOuVazio() {
        return contextoAdicional == null ? "" : contextoAdicional;
    }

    public String parteRepresentadaOuNaoInformada() {
        return parteRepresentada == null || parteRepresentada.isBlank()
                ? "não informada" : parteRepresentada;
    }

    public static AnaliseEspecializadaRequest padrao() {
        return new AnaliseEspecializadaRequest(List.of(), false, null, false, false, null, null);
    }
}
