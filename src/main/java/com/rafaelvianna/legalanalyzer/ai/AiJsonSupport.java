package com.rafaelvianna.legalanalyzer.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaelvianna.legalanalyzer.exception.AiClientException;
import org.springframework.stereotype.Component;

/**
 * Utilitário para serializar objetos em JSON (para incluir em prompts) e
 * para interpretar de forma tolerante o JSON devolvido pelo modelo de IA
 * — que às vezes vem envolto em blocos de código markdown (```json ... ```)
 * ou com texto adicional antes/depois do JSON.
 */
@Component
public class AiJsonSupport {

    private final ObjectMapper mapper;

    public AiJsonSupport() {
        this.mapper = new ObjectMapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public <T> T parse(String respostaBruta, Class<T> tipo) {
        String json = extrairJson(respostaBruta);
        try {
            return mapper.readValue(json, tipo);
        } catch (JsonProcessingException e) {
            throw new AiClientException(
                    "Não foi possível interpretar a resposta da IA como JSON válido: " + e.getMessage() +
                    "\nResposta recebida (truncada): " + truncar(respostaBruta, 500), e);
        }
    }

    public String toJson(Object objeto) {
        try {
            return mapper.writeValueAsString(objeto);
        } catch (JsonProcessingException e) {
            throw new AiClientException("Falha ao serializar objeto para JSON: " + e.getMessage(), e);
        }
    }

    private String extrairJson(String texto) {
        String limpo = texto.trim();

        if (limpo.startsWith("```")) {
            limpo = limpo.replaceFirst("^```(json)?", "").trim();
            if (limpo.endsWith("```")) {
                limpo = limpo.substring(0, limpo.length() - 3).trim();
            }
        }

        int inicioObjeto = limpo.indexOf('{');
        int inicioArray = limpo.indexOf('[');
        int inicio;
        if (inicioObjeto == -1) {
            inicio = inicioArray;
        } else if (inicioArray == -1) {
            inicio = inicioObjeto;
        } else {
            inicio = Math.min(inicioObjeto, inicioArray);
        }

        int fimObjeto = limpo.lastIndexOf('}');
        int fimArray = limpo.lastIndexOf(']');
        int fim = Math.max(fimObjeto, fimArray);

        if (inicio >= 0 && fim > inicio) {
            return limpo.substring(inicio, fim + 1);
        }
        return limpo;
    }

    private String truncar(String texto, int max) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= max ? texto : texto.substring(0, max) + "...";
    }
}
