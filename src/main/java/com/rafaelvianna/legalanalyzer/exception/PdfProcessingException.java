package com.rafaelvianna.legalanalyzer.exception;

/**
 * Lançada quando o PDF enviado não pode ser lido, está corrompido,
 * protegido por senha, vazio ou não é um PDF válido.
 */
public class PdfProcessingException extends RuntimeException {

    public PdfProcessingException(String message) {
        super(message);
    }

    public PdfProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
