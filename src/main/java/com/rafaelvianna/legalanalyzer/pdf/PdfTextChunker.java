package com.rafaelvianna.legalanalyzer.pdf;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Divide textos longos em trechos (chunks) menores, respeitando (quando
 * possível) quebras de parágrafo, para que cada trecho possa ser enviado
 * separadamente aos agentes de IA sem estourar limites de contexto.
 * Usa sobreposição (overlap) entre trechos para reduzir a chance de perder
 * contexto em eventos que ficam na fronteira entre dois trechos.
 */
@Component
public class PdfTextChunker {

    public List<String> chunk(String texto, int tamanhoMaximoCaracteres, int sobreposicaoCaracteres) {
        List<String> trechos = new ArrayList<>();

        if (texto == null || texto.isBlank()) {
            return trechos;
        }

        if (texto.length() <= tamanhoMaximoCaracteres) {
            trechos.add(texto);
            return trechos;
        }

        int inicio = 0;
        int comprimento = texto.length();

        while (inicio < comprimento) {
            int fim = Math.min(inicio + tamanhoMaximoCaracteres, comprimento);

            if (fim < comprimento) {
                int pontoDeQuebra = encontrarQuebraDeParagrafo(texto, inicio, fim);
                if (pontoDeQuebra > inicio) {
                    fim = pontoDeQuebra;
                }
            }

            trechos.add(texto.substring(inicio, fim));

            if (fim >= comprimento) {
                break;
            }

            int proximoInicio = fim - sobreposicaoCaracteres;
            inicio = Math.max(proximoInicio, inicio + 1);
        }

        return trechos;
    }

    private int encontrarQuebraDeParagrafo(String texto, int inicio, int fim) {
        int idx = texto.lastIndexOf("\n\n", fim);
        if (idx > inicio) {
            return idx + 2;
        }
        idx = texto.lastIndexOf('\n', fim);
        if (idx > inicio) {
            return idx + 1;
        }
        idx = texto.lastIndexOf('.', fim);
        if (idx > inicio) {
            return idx + 1;
        }
        return fim;
    }
}
