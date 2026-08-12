package com.rafaelvianna.legalanalyzer.web.dto.specialized;

import java.util.List;

/** Rascunho produzido pelo Drafting Agent — sempre sujeito a revisão do advogado. */
public record RascunhoDTO(
        TipoRascunho tipo,
        String titulo,
        String conteudo,
        List<String> pontosDeAtencao,
        List<String> lacunasParaPreencher,
        String avisoRevisao
) {
}
