package com.rafaelvianna.legalanalyzer.async;

/** Etapas da análise especializada (pipeline dos agentes especialistas). */
public enum AnaliseEspecializadaStatus {
    RECEBIDO,
    CLASSIFICANDO_DOCUMENTOS,
    ANALISANDO_PROCESSO,
    ANALISANDO_CONTRATO,
    MAPEANDO_PRAZOS,
    CRUZANDO_EVIDENCIAS,
    PESQUISANDO_FONTES,
    REDIGINDO_RASCUNHOS,
    PARECER_SENIOR,
    CONCLUIDO,
    ERRO
}
