package com.rafaelvianna.legalanalyzer.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que a recuperação funciona mesmo sem embeddings (modo léxico) e que
 * o rastreio de página — base da citação "documento, página N" — é confiável.
 */
class IndiceProcessoTest {

    private static final Passagem PAGINA_1 = new Passagem("p1#1", Passagem.Tipo.TEXTO_PROCESSO,
            "Documento — página 1", 1,
            "Contrato de prestação de serviços celebrado entre Empresa X e Empresa Y em 10/01/2026.", null);

    private static final Passagem PAGINA_42 = new Passagem("p42#1", Passagem.Tipo.TEXTO_PROCESSO,
            "Documento — página 42", 42,
            "Documento 17 - comprovante de pagamento da terceira parcela, no valor de R$ 45.000,00.", null);

    private static final Passagem FICHA = new Passagem("ficha:cronologia#1", Passagem.Tipo.FICHA_ANALISE,
            "Análise — cronologia", null,
            "Análise — cronologia → 05/04/2026: ação de cobrança ajuizada pela Empresa X.", null);

    private IndiceProcesso indice() {
        return new IndiceProcesso(List.of(PAGINA_1, PAGINA_42, FICHA), false);
    }

    @Test
    @DisplayName("sem embeddings, a busca léxica encontra a passagem pelo termo do documento")
    void buscaLexicaFunciona() {
        List<PassagemRecuperada> resultado = indice().buscar("comprovante de pagamento da parcela", null, 5, 0.05);

        assertThat(resultado).isNotEmpty();
        assertThat(resultado.get(0).passagem().pagina()).isEqualTo(42);
        assertThat(resultado.get(0).estrategia()).isEqualTo("lexica");
    }

    @Test
    @DisplayName("a busca ignora acentos e maiúsculas")
    void buscaNormalizaAcentos() {
        List<PassagemRecuperada> resultado = indice().buscar("PRESTACAO DE SERVICOS", null, 5, 0.05);

        assertThat(resultado).isNotEmpty();
        assertThat(resultado.get(0).passagem().pagina()).isEqualTo(1);
    }

    @Test
    @DisplayName("pergunta sem relação com o caso não devolve passagens")
    void perguntaForaDoCasoNaoRecupera() {
        assertThat(indice().buscar("receita de bolo de fubá", null, 5, 0.3)).isEmpty();
    }

    @Test
    @DisplayName("localizarTermo devolve a página em que o documento é citado")
    void localizaPaginaDoDocumento() {
        assertThat(indice().localizarTermo("Documento 17"))
                .isPresent()
                .get()
                .extracting(Passagem::pagina)
                .isEqualTo(42);
    }

    @Test
    @DisplayName("termo inexistente não inventa página")
    void naoInventaPagina() {
        assertThat(indice().localizarTermo("laudo pericial contábil")).isEmpty();
    }

    @Test
    @DisplayName("com embeddings, a similaridade de cosseno ordena os resultados")
    void buscaSemanticaOrdena() {
        Passagem a = new Passagem("a", Passagem.Tipo.TEXTO_PROCESSO, "Documento — página 3", 3,
                "cláusula de rescisão antecipada", new float[]{1f, 0f, 0f});
        Passagem b = new Passagem("b", Passagem.Tipo.TEXTO_PROCESSO, "Documento — página 4", 4,
                "endereço das partes contratantes", new float[]{0f, 1f, 0f});
        IndiceProcesso comVetores = new IndiceProcesso(List.of(a, b), true);

        List<PassagemRecuperada> resultado = comVetores.buscar("qualquer termo", new float[]{0.9f, 0.1f, 0f}, 2, 0.01);

        assertThat(resultado).isNotEmpty();
        assertThat(resultado.get(0).passagem().pagina()).isEqualTo(3);
    }

    @Test
    @DisplayName("citação sempre indica a página quando a passagem vem do PDF")
    void citacaoIncluiPagina() {
        assertThat(PAGINA_42.citacao()).contains("página 42");
        assertThat(FICHA.citacao()).isEqualTo("Análise — cronologia");
    }
}
