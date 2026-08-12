package com.rafaelvianna.legalanalyzer.async;

public enum AnaliseStatus {
    RECEBIDO,
    EXTRAINDO_PDF,
    ANALISANDO_PARTES,
    CONSOLIDANDO,
    ANALISANDO_EVIDENCIAS,
    GERANDO_RELATORIO,
    CONCLUIDO,
    ERRO
}
