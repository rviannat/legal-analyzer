package com.rafaelvianna.legalanalyzer;

import com.rafaelvianna.legalanalyzer.pdf.PdfTextChunker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfTextChunkerTest {

    private final PdfTextChunker chunker = new PdfTextChunker();

    @Test
    void naoDivideTextoMenorQueOTamanhoMaximo() {
        String texto = "Texto curto de processo.";
        List<String> trechos = chunker.chunk(texto, 1000, 100);

        assertEquals(1, trechos.size());
        assertEquals(texto, trechos.get(0));
    }

    @Test
    void divideTextoLongoRespeitandoTamanhoMaximo() {
        String paragrafo = "Este é um parágrafo de teste para simular um processo jurídico longo. ".repeat(50);
        String texto = paragrafo + "\n\n" + paragrafo + "\n\n" + paragrafo;

        List<String> trechos = chunker.chunk(texto, 1000, 100);

        assertTrue(trechos.size() > 1, "O texto longo deveria ser dividido em mais de um trecho.");
        trechos.forEach(t -> assertTrue(t.length() <= 1100, "Cada trecho não deveria exceder muito o limite configurado."));
    }

    @Test
    void retornaListaVaziaParaTextoNuloOuEmBranco() {
        assertTrue(chunker.chunk(null, 1000, 100).isEmpty());
        assertTrue(chunker.chunk("   ", 1000, 100).isEmpty());
    }
}
