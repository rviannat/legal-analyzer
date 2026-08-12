package com.rafaelvianna.legalanalyzer.exception;

/**
 * Lançada quando o arquivo enviado excede o tamanho máximo configurado.
 */
public class DocumentTooLargeException extends RuntimeException {

    public DocumentTooLargeException(String message) {
        super(message);
    }
}
