package com.rafaelvianna.legalanalyzer.async;

import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;

import java.time.Instant;

public final class AnaliseJob {
    private final String id;
    private final String nomeArquivo;
    private final Instant criadoEm;
    private volatile Instant atualizadoEm;
    private volatile AnaliseStatus status;
    private volatile int progresso;
    private volatile String etapa;
    private volatile String mensagem;
    private volatile AnaliseProcessoResponse resultado;

    public AnaliseJob(String id, String nomeArquivo) {
        this.id = id;
        this.nomeArquivo = nomeArquivo;
        this.criadoEm = Instant.now();
        this.atualizadoEm = criadoEm;
        this.status = AnaliseStatus.RECEBIDO;
        this.progresso = 0;
        this.etapa = "Recebido";
        this.mensagem = "Arquivo recebido e colocado na fila de processamento.";
    }

    public void atualizar(AnaliseStatus status, int progresso, String etapa, String mensagem) {
        this.status = status;
        this.progresso = progresso;
        this.etapa = etapa;
        this.mensagem = mensagem;
        this.atualizadoEm = Instant.now();
    }

    public void concluir(AnaliseProcessoResponse resultado) {
        this.resultado = resultado;
        atualizar(AnaliseStatus.CONCLUIDO, 100, "Relatório pronto", "Análise concluída com sucesso.");
    }

    public void falhar(String mensagem) {
        atualizar(AnaliseStatus.ERRO, progresso, "Falha no processamento", mensagem);
    }

    public String id() { return id; }
    public String nomeArquivo() { return nomeArquivo; }
    public Instant criadoEm() { return criadoEm; }
    public Instant atualizadoEm() { return atualizadoEm; }
    public AnaliseStatus status() { return status; }
    public int progresso() { return progresso; }
    public String etapa() { return etapa; }
    public String mensagem() { return mensagem; }
    public AnaliseProcessoResponse resultado() { return resultado; }
}
