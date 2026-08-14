package com.rafaelvianna.legalanalyzer.async;

import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Estado persistível da análise especializada, com telemetria detalhada dos agentes. */
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
        this.id = id; this.analiseBaseId = analiseBaseId; this.nomeArquivo = nomeArquivo;
        this.criadoEm = Instant.now(); this.atualizadoEm = criadoEm;
        this.status = AnaliseEspecializadaStatus.RECEBIDO; this.progresso = 0;
        this.etapa = "Recebido"; this.mensagem = "Análise especializada na fila de processamento.";
        adicionarLog("SYSTEM", 0, 0, 8, "QUEUE", "RECEBIDO", "Fila de análise criada.", List.of("Análise Base"), "");
    }

    public synchronized void atualizar(AnaliseEspecializadaStatus status, int progresso, String etapa, String mensagem) {
        atualizarInterno(status, progresso, etapa, mensagem, "SYSTEM", 0, 8, "STATUS", List.of("Análise Base"), "");
    }

    public synchronized void atualizarDetalhado(AnaliseEspecializadaStatus status, int progresso, String agente,
                                                   int agenteNumero, int totalAgentes, String acao, String mensagem,
                                                   List<String> contextoRecebido, String resultadoParcial) {
        atualizarInterno(status, progresso, agente + " — " + acao, mensagem, agente, agenteNumero, totalAgentes,
                acao, contextoRecebido, resultadoParcial);
    }

    private void atualizarInterno(AnaliseEspecializadaStatus status, int progresso, String etapa, String mensagem,
                                  String agente, int agenteNumero, int totalAgentes, String acao,
                                  List<String> contextoRecebido, String resultadoParcial) {
        this.status = status; this.progresso = Math.max(0, Math.min(100, progresso));
        this.etapa = etapa; this.mensagem = mensagem; this.atualizadoEm = Instant.now();
        if (this.progresso > 0) {
            long decorrido = Math.max(1, Duration.between(criadoEm, atualizadoEm).toSeconds());
            this.estimativaRestanteSegundos = Math.max(0, Math.round((decorrido * (100.0 - this.progresso)) / this.progresso));
        }
        if (this.progresso >= 100) this.estimativaRestanteSegundos = 0;
        adicionarLog(agente, agenteNumero, totalAgentes, acao, status.name(), mensagem, contextoRecebido, resultadoParcial);
    }

    public void concluir(AnaliseEspecializadaResponse resultado) {
        this.resultado = resultado;
        atualizar(AnaliseEspecializadaStatus.CONCLUIDO, 100, "Parecer consolidado", "Análise especializada concluída. Todos os resultados dependem de revisão do advogado.");
    }

    public void falhar(String mensagem) { atualizar(AnaliseEspecializadaStatus.ERRO, progresso, "Falha no processamento", mensagem); }

    private void adicionarLog(String agente, int agenteNumero, int totalAgentes, String acao, String status,
                              String mensagem, List<String> contextoRecebido, String resultadoParcial) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("timestamp", Instant.now().toString()); item.put("agente", agente); item.put("agenteNumero", agenteNumero);
        item.put("totalAgentes", totalAgentes); item.put("status", status); item.put("progresso", progresso);
        item.put("acao", acao); item.put("etapa", etapa); item.put("mensagem", mensagem);
        item.put("contextoRecebido", contextoRecebido == null ? List.of() : List.copyOf(contextoRecebido));
        item.put("resultadoParcial", resultadoParcial == null ? "" : resultadoParcial);
        item.put("estimativaRestanteSegundos", estimativaRestanteSegundos);
        logs.add(item);
    }

    public String id() { return id; } public String analiseBaseId() { return analiseBaseId; }
    public String nomeArquivo() { return nomeArquivo; } public Instant criadoEm() { return criadoEm; }
    public Instant atualizadoEm() { return atualizadoEm; } public AnaliseEspecializadaStatus status() { return status; }
    public int progresso() { return progresso; } public String etapa() { return etapa; } public String mensagem() { return mensagem; }
    public long estimativaRestanteSegundos() { return estimativaRestanteSegundos; }
    public AnaliseEspecializadaResponse resultado() { return resultado; }
    public synchronized List<Map<String, Object>> logs() { return List.copyOf(logs); }
}
