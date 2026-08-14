package com.rafaelvianna.legalanalyzer.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "analises_especializadas")
public class AnaliseEspecializadaEntity {
    @Id @Column(length = 36, nullable = false) private String id;
    @Column(name = "analise_base_id", length = 36, nullable = false) private String analiseBaseId;
    @Column(name = "nome_arquivo", nullable = false) private String nomeArquivo;
    @Column(nullable = false, length = 40) private String status;
    @Column(nullable = false) private int progresso;
    @Column(length = 160) private String etapa;
    @Column(length = 2000) private String mensagem;
    @Column(name = "resultado_json", columnDefinition = "text") private String resultadoJson;
    @Basic(fetch = FetchType.LAZY) @Column(name = "relatorio_pdf", columnDefinition = "bytea") private byte[] relatorioPdf;
    @Column(name = "logs_json", columnDefinition = "text") private String logsJson;
    @Column(name = "criado_em", nullable = false) private Instant criadoEm;
    @Column(name = "atualizado_em", nullable = false) private Instant atualizadoEm;

    protected AnaliseEspecializadaEntity() {}

    public AnaliseEspecializadaEntity(String id, String analiseBaseId, String nomeArquivo) {
        this.id = id;
        this.analiseBaseId = analiseBaseId;
        this.nomeArquivo = nomeArquivo;
        this.status = "RECEBIDO";
        this.progresso = 0;
        this.etapa = "Recebido";
        this.mensagem = "Análise especializada colocada na fila de processamento.";
        this.criadoEm = Instant.now();
        this.atualizadoEm = this.criadoEm;
        this.logsJson = "[]";
    }

    public void atualizar(String status, int progresso, String etapa, String mensagem, String logsJson) {
        this.status = status;
        this.progresso = progresso;
        this.etapa = etapa;
        this.mensagem = mensagem;
        this.logsJson = logsJson;
        this.atualizadoEm = Instant.now();
    }

    public void concluir(String resultadoJson, byte[] relatorioPdf, String logsJson) {
        this.resultadoJson = resultadoJson;
        this.relatorioPdf = relatorioPdf;
        atualizar("CONCLUIDO", 100, "Parecer consolidado", "Análise especializada concluída e relatório salvo.", logsJson);
    }

    public String getId() { return id; }
    public String getAnaliseBaseId() { return analiseBaseId; }
    public String getNomeArquivo() { return nomeArquivo; }
    public String getStatus() { return status; }
    public int getProgresso() { return progresso; }
    public String getEtapa() { return etapa; }
    public String getMensagem() { return mensagem; }
    public String getResultadoJson() { return resultadoJson; }
    public byte[] getRelatorioPdf() { return relatorioPdf; }
    public String getLogsJson() { return logsJson; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
}
