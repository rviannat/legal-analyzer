package com.rafaelvianna.legalanalyzer.async;

import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;

import java.time.Instant;

/** Estado em memória de uma análise especializada em andamento. */
public final class AnaliseEspecializadaJob {
    private final String id;
    private final String analiseBaseId;
    private final String nomeArquivo;
    private final Instant criadoEm;
    private volatile Instant atualizadoEm;
    private volatile AnaliseEspecializadaStatus status;
    private volatile int progresso;
    private volatile String etapa;
    private volatile String mensagem;
    private volatile AnaliseEspecializadaResponse resultado;

    public AnaliseEspecializadaJob(String id, String analiseBaseId, String nomeArquivo) {
        this.id = id;
        this.analiseBaseId = analiseBaseId;
        this.nomeArquivo = nomeArquivo;
        this.criadoEm = Instant.now();
        this.atualizadoEm = criadoEm;
        this.status = AnaliseEspecializadaStatus.RECEBIDO;
        this.progresso = 0;
        this.etapa = "Recebido";
        this.mensagem = "Análise especializada na fila de processamento.";
    }

    public void atualizar(AnaliseEspecializadaStatus status, int progresso, String etapa, String mensagem) {
        this.status = status;
        this.progresso = progresso;
        this.etapa = etapa;
        this.mensagem = mensagem;
        this.atualizadoEm = Instant.now();
    }

    public void concluir(AnaliseEspecializadaResponse resultado) {
        this.resultado = resultado;
        atualizar(AnaliseEspecializadaStatus.CONCLUIDO, 100, "Parecer consolidado",
                "Análise especializada concluída. Todos os resultados dependem de revisão do advogado.");
    }

    public void falhar(String mensagem) {
        atualizar(AnaliseEspecializadaStatus.ERRO, progresso, "Falha no processamento", mensagem);
    }

    public String id() { return id; }
    public String analiseBaseId() { return analiseBaseId; }
    public String nomeArquivo() { return nomeArquivo; }
    public Instant criadoEm() { return criadoEm; }
    public Instant atualizadoEm() { return atualizadoEm; }
    public AnaliseEspecializadaStatus status() { return status; }
    public int progresso() { return progresso; }
    public String etapa() { return etapa; }
    public String mensagem() { return mensagem; }
    public AnaliseEspecializadaResponse resultado() { return resultado; }
}
