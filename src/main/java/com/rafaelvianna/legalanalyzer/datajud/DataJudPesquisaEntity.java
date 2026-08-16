package com.rafaelvianna.legalanalyzer.datajud;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "datajud_pesquisas")
public class DataJudPesquisaEntity {
    @Id @Column(length = 36, nullable = false) private String id;
    @Column(nullable = false, length = 30) private String tipo;
    @Column(length = 255) private String parametro;
    @Column(length = 30) private String tribunal;
    @Column(length = 255) private String assunto;
    @Column(name = "numero_cnj", length = 30) private String numeroCnj;
    @Column(name = "classe_codigo", length = 50) private String classeCodigo;
    @Column(name = "classe_nome", length = 255) private String classeNome;
    @Column(length = 30) private String grau;
    @Column(name = "orgao_julgador", length = 255) private String orgaoJulgador;
    @Column(name = "resultado_encontrado", nullable = false) private boolean resultadoEncontrado;
    @Column(nullable = false) private boolean processado;
    @Column(name = "analise_id", length = 36) private String analiseId;
    @Column(length = 2000) private String mensagem;
    @Column(name = "consultado_em", nullable = false) private Instant consultadoEm;
    @Column(name = "criado_em", nullable = false) private Instant criadoEm;

    protected DataJudPesquisaEntity() {}

    public static DataJudPesquisaEntity from(String tipo, String parametro, String tribunal, String assunto,
                                               DataJudInfo info, String classeCodigo) {
        DataJudPesquisaEntity e = base(tipo, parametro, tribunal, assunto);
        e.numeroCnj = info == null ? null : info.numeroProcesso();
        e.classeCodigo = classeCodigo;
        e.classeNome = info == null ? null : info.classeProcessual();
        e.grau = info == null ? null : info.grau();
        e.orgaoJulgador = info == null ? null : info.orgaoJulgador();
        e.resultadoEncontrado = info != null && info.encontrado();
        e.mensagem = info == null ? null : info.mensagem();
        e.consultadoEm = info == null || info.consultadoEm() == null ? Instant.now() : info.consultadoEm();
        return e;
    }

    public static DataJudPesquisaEntity fromAmostra(String tribunal, String assunto, DataJudAmostra amostra) {
        DataJudPesquisaEntity e = base("ORGAO_ASSUNTO", amostra.numeroProcesso(), tribunal, assunto);
        e.numeroCnj = amostra.numeroProcesso();
        e.classeCodigo = amostra.classeCodigo();
        e.classeNome = amostra.classeNome();
        e.grau = amostra.grau();
        e.orgaoJulgador = amostra.orgaoJulgador();
        e.resultadoEncontrado = true;
        e.mensagem = "Resultado encontrado na pesquisa agregada DataJud.";
        e.consultadoEm = Instant.now();
        return e;
    }

    private static DataJudPesquisaEntity base(String tipo, String parametro, String tribunal, String assunto) {
        DataJudPesquisaEntity e = new DataJudPesquisaEntity();
        e.id = UUID.randomUUID().toString();
        e.tipo = tipo;
        e.parametro = parametro;
        e.tribunal = tribunal;
        e.assunto = assunto;
        e.processado = false;
        e.criadoEm = Instant.now();
        return e;
    }

    public void marcarProcessado(String analiseId) { this.processado = true; this.analiseId = analiseId; }
    public String getId() { return id; }
    public String getTipo() { return tipo; }
    public String getParametro() { return parametro; }
    public String getTribunal() { return tribunal; }
    public String getAssunto() { return assunto; }
    public String getNumeroCnj() { return numeroCnj; }
    public boolean isResultadoEncontrado() { return resultadoEncontrado; }
    public boolean isProcessado() { return processado; }
    public String getAnaliseId() { return analiseId; }
    public Instant getCriadoEm() { return criadoEm; }
}
