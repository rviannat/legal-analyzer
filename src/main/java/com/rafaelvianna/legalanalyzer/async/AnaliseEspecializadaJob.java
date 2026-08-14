package com.rafaelvianna.legalanalyzer.async;

import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Estado da análise especializada, incluindo telemetria útil para acompanhamento do trabalho dos 8 agentes. */
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
    private volatile long estimativaRestanteSegundos;
    private volatile AnaliseEspecializadaResponse resultado;
    private final List<Map<String, Object>> logs = new ArrayList<>();

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
        this.estimativaRestanteSegundos = 0;
        adicionarLog("RECEBIDO", 0, etapa, mensagem);
    }

    public synchronized void atualizar(AnaliseEspecializadaStatus status, int progresso, String etapa, String mensagem) {
        this.status = status;
        this.progresso = Math.max(0, Math.min(100, progresso));
        this.etapa = etapa;
        this.mensagem = mensagem;
        this.atualizadoEm = Instant.now();
        if (this.progresso > 0) {
            long decorrido = Math.max(1, Duration.between(criadoEm, atualizadoEm).toSeconds());
            this.estimativaRestanteSegundos = Math.max(0, Math.round((decorrido * (100.0 - this.progresso)) / this.progresso));
        }
        if (this.progresso >= 100) this.estimativaRestanteSegundos = 0;
        adicionarLog(status.name(), this.progresso, etapa, mensagem);
    }

    public void concluir(AnaliseEspecializadaResponse resultado) {
        this.resultado = resultado;
        atualizar(AnaliseEspecializadaStatus.CONCLUIDO, 100, "Parecer consolidado",
                "Análise especializada concluída. Todos os resultados dependem de revisão do advogado.");
    }

    public void falhar(String mensagem) {
        atualizar(AnaliseEspecializadaStatus.ERRO, progresso, "Falha no processamento", mensagem);
    }

    private void adicionarLog(String status, int progresso, String etapa, String mensagem) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("timestamp", Instant.now().toString());
        item.put("status", status);
        item.put("progresso", progresso);
        item.put("etapa", etapa);
        item.put("mensagem", mensagem);
        item.put("estimativaRestanteSegundos", estimativaRestanteSegundos);
        logs.add(item);
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
    public long estimativaRestanteSegundos() { return estimativaRestanteSegundos; }
    public AnaliseEspecializadaResponse resultado() { return resultado; }
    public synchronized List<Map<String, Object>> logs() { return List.copyOf(logs); }
}
