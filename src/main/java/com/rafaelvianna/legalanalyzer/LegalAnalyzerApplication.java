package com.rafaelvianna.legalanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Aplicação Spring Boot responsável por receber PDFs de processos jurídicos,
 * orquestrar múltiplos agentes de IA para análise do conteúdo e devolver
 * um relatório estruturado (partes, cronologia, pedidos, decisões, prazos,
 * documentos, resumo, inconsistências, evidências, perguntas de investigação
 * e relatório executivo).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LegalAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegalAnalyzerApplication.class, args);
    }
}
