package com.rafaelvianna.legalanalyzer.web.dto.specialized;

/** Tipos de rascunho que o Drafting Agent pode produzir (sempre para revisão do advogado). */
public enum TipoRascunho {
    PARECER,
    MANIFESTACAO,
    RELATORIO,
    PETICAO,
    EMAIL_CLIENTE;

    /** Descrição usada nos prompts para orientar o formato esperado. */
    public String descricao() {
        return switch (this) {
            case PARECER -> "parecer jurídico (consulta, análise fundamentada e conclusão)";
            case MANIFESTACAO -> "manifestação processual objetiva a ser protocolada nos autos";
            case RELATORIO -> "relatório técnico interno sobre o caso, para uso do escritório";
            case PETICAO -> "petição (endereçamento, fatos, fundamentos, pedidos e fechamento)";
            case EMAIL_CLIENTE -> "e-mail ao cliente em linguagem clara, sem jargão excessivo";
        };
    }
}
