package com.rafaelvianna.legalanalyzer.exception;

/**
 * Lançada quando há falha de comunicação com o provedor de IA ou quando
 * a resposta recebida não pode ser interpretada como JSON válido.
 */
public class AiClientException extends RuntimeException {

    public AiClientException(String message) {
        super(message);
    }

    public AiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
