package com.rafaelvianna.legalanalyzer.chat;

import com.rafaelvianna.legalanalyzer.rag.Passagem;
import com.rafaelvianna.legalanalyzer.rag.PassagemRecuperada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a trava contra citação falsa: é ela que separa este chat de um
 * assistente que "parece" citar o processo.
 */
class ValidadorAncoragemTest {

    private static final Map<String, PassagemRecuperada> CONTEXTO = Map.of(
            "T1", new PassagemRecuperada(new Passagem("p42#1", Passagem.Tipo.TEXTO_PROCESSO,
                    "Documento — página 42", 42,
                    "Documento 17 - comprovante de pagamento da terceira parcela.", null), 0.9, "lexica"),
            "T2", new PassagemRecuperada(new Passagem("ficha:prazos#1", Passagem.Tipo.FICHA_ANALISE,
                    "Análise — prazos", null,
                    "Análise — prazos → 20/05/2026: prazo para réplica, criticidade alta.", null), 0.8, "lexica"));

    @Test
    @DisplayName("resposta com marcadores válidos é entregue com as citações e a página")
    void respostaAncoradaEhAceita() {
        var bruta = new RespostaChatIa(
                "O pagamento da terceira parcela está comprovado [T1] e há prazo de réplica em 20/05 [T2].",
                List.of("T1", "T2"), true, List.of("Há comprovante das demais parcelas?"));

        var resultado = ValidadorAncoragem.validar(bruta, CONTEXTO);

        assertThat(resultado.fundamentada()).isTrue();
        assertThat(resultado.citacoes()).hasSize(2);
        assertThat(resultado.citacoes()).anySatisfy(c -> {
            assertThat(c.pagina()).isEqualTo(42);
            assertThat(c.rotulo()).contains("página 42");
        });
        assertThat(resultado.marcadoresRemovidos()).isEmpty();
    }

    @Test
    @DisplayName("marcador inexistente é removido do texto e não vira citação")
    void marcadorInventadoEhRemovido() {
        var bruta = new RespostaChatIa(
                "O pagamento está comprovado [T1] e houve perícia contábil [T9].",
                List.of("T1", "T9"), true, List.of());

        var resultado = ValidadorAncoragem.validar(bruta, CONTEXTO);

        assertThat(resultado.fundamentada()).isTrue();
        assertThat(resultado.texto()).doesNotContain("[T9]");
        assertThat(resultado.marcadoresRemovidos()).containsExactly("T9");
        assertThat(resultado.citacoes()).hasSize(1);
    }

    @Test
    @DisplayName("resposta sem nenhum marcador válido é rebaixada para não fundamentada")
    void respostaSemAncoragemEhRejeitada() {
        var bruta = new RespostaChatIa(
                "Segundo o art. 373 do CPC, o ônus é de quem alega [T7].",
                List.of("T7"), true, List.of());

        var resultado = ValidadorAncoragem.validar(bruta, CONTEXTO);

        assertThat(resultado.fundamentada()).isFalse();
        assertThat(resultado.citacoes()).isEmpty();
    }

    @Test
    @DisplayName("quando o modelo admite a lacuna, a resposta não é dada como fundamentada")
    void modeloAdmitindoLacuna() {
        var bruta = new RespostaChatIa("Não consta no material analisado. [T1]",
                List.of("T1"), false, List.of());

        assertThat(ValidadorAncoragem.validar(bruta, CONTEXTO).fundamentada()).isFalse();
    }

    @Test
    @DisplayName("resposta nula ou vazia não é fundamentada")
    void respostaVazia() {
        assertThat(ValidadorAncoragem.validar(null, CONTEXTO).fundamentada()).isFalse();
        assertThat(ValidadorAncoragem.validar(
                new RespostaChatIa("   ", List.of(), true, List.of()), CONTEXTO).fundamentada()).isFalse();
    }
}
