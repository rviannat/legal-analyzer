package com.rafaelvianna.legalanalyzer.web.dto.rag;

/**
 * O rastro "Alegação A → Documento 17 → página 42".
 *
 * A página só é preenchida quando o nome do documento foi efetivamente
 * localizado no texto do PDF. Se não foi, {@code pagina} vem nula e
 * {@code status} explica o motivo — melhor uma lacuna explícita do que um
 * ponteiro inventado.
 *
 * @param alegacao        alegação sustentada (ou não) pelo documento
 * @param parteQueAlega   quem alega
 * @param documento       documento indicado como suporte
 * @param pagina          página onde o documento é mencionado no PDF
 * @param comoSustenta    de que forma o documento sustenta a alegação
 * @param forcaProbatoria avaliação da força da prova
 * @param status          LOCALIZADO, NAO_LOCALIZADO_NO_PDF ou SEM_DOCUMENTO
 */
public record EvidenciaRastreadaDTO(
        String alegacao,
        String parteQueAlega,
        String documento,
        Integer pagina,
        String comoSustenta,
        String forcaProbatoria,
        String status
) {
}
