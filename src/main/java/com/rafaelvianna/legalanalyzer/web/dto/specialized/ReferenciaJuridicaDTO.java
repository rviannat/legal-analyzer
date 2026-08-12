package com.rafaelvianna.legalanalyzer.web.dto.specialized;

/**
 * Referência de legislação/jurisprudência efetivamente recuperada de uma
 * fonte autorizada. {@code url} e {@code fonte} tornam a citação rastreável;
 * {@code verificada} indica que o conteúdo foi baixado da fonte (e não
 * apenas mencionado pelo modelo).
 */
public record ReferenciaJuridicaDTO(
        String tipo,
        String identificacao,
        String fonte,
        String url,
        String trechoRelevante,
        String consultadoEm,
        boolean verificada
) {
}
