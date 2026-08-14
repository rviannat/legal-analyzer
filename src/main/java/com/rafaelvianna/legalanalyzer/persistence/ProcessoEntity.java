package com.rafaelvianna.legalanalyzer.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processos")
public class ProcessoEntity {
    @Id
    @Column(length = 36, nullable = false)
    private String id;
    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;
    @Column(name = "numero_cnj", length = 30)
    private String numeroCnj;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Status status;
    @Column(nullable = false)
    private int progresso;
    @Column(length = 120)
    private String etapa;
    @Column(length = 2000)
    private String mensagem;
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "arquivo_pdf", columnDefinition = "bytea", nullable = false)
    private byte[] arquivoPdf;
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "relatorio_pdf", columnDefinition = "bytea")
    private byte[] relatorioPdf;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
    protected ProcessoEntity() {}
    public ProcessoEntity(String id, String nomeArquivo, byte[] arquivoPdf) { this.id=id; this.nomeArquivo=nomeArquivo; this.arquivoPdf=arquivoPdf; this.status=Status.RECEBIDO; this.progresso=0; this.criadoEm=Instant.now(); this.atualizadoEm=this.criadoEm; }
    public void atualizar(String numeroCnj, Status status, int progresso, String etapa, String mensagem) { this.numeroCnj=numeroCnj; this.status=status; this.progresso=progresso; this.etapa=etapa; this.mensagem=mensagem; this.atualizadoEm=Instant.now(); }
    public void concluirRelatorio(byte[] pdf) { this.relatorioPdf=pdf; this.atualizadoEm=Instant.now(); }
    public String getId(){return id;} public byte[] getArquivoPdf(){return arquivoPdf;} public byte[] getRelatorioPdf(){return relatorioPdf;}
    public enum Status { RECEBIDO, EXTRAINDO_PDF, ANALISANDO_PARTES, CONCLUIDO, ERRO }
}
