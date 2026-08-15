package com.rafaelvianna.legalanalyzer.async;

import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Estado persistível da análise longa, com equipe atual, telemetria e estimativa. */
public final class AnaliseEspecializadaJob {
    private static final int TOTAL_AGENTES_EQUIPE_2 = 8;
    private static final int TOTAL_AGENTES_EQUIPE_3 = 8;
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
    private volatile String equipeAtual = "EQUIPE_2";
    private volatile AnaliseEspecializadaResponse resultado;
    private final List<Map<String, Object>> logs = new ArrayList<>();

    public AnaliseEspecializadaJob(String id, String analiseBaseId, String nomeArquivo) {
        this.id = id; this.analiseBaseId = analiseBaseId; this.nomeArquivo = nomeArquivo;
        this.criadoEm = Instant.now(); this.atualizadoEm = criadoEm;
        this.status = AnaliseEspecializadaStatus.RECEBIDO; this.progresso = 0;
        this.etapa = "Equipe 2 — Recebido"; this.mensagem = "Equipe 2 na fila de processamento.";
        adicionarLog("SYSTEM", 0, "QUEUE", "RECEBIDO", "Equipe 2 criada após a conclusão da Equipe 1.", List.of("Equipe 1"), "");
    }

    public synchronized void iniciarEquipe3() {
        this.equipeAtual = "EQUIPE_3_DATAJUD";
        this.progresso = 0;
        this.estimativaRestanteSegundos = 0;
        this.etapa = "Equipe 3 — Preparando validação externa";
        this.mensagem = "Equipe 2 concluída. A barra foi reiniciada para a validação externa DataJud/Jus.";
        this.atualizadoEm = Instant.now();
        adicionarLog("SYSTEM", 0, "TEAM_START", "INICIANDO", this.mensagem, List.of("Equipe 1", "Equipe 2"), "Equipe 3 iniciada somente após a Equipe 2.");
    }

    public synchronized void atualizar(AnaliseEspecializadaStatus status, int progresso, String etapa, String mensagem) {
        String agente = identificarAgente(etapa, mensagem);
        int numero = numeroAgente(agente);
        String acao = identificarAcao(etapa, mensagem);
        List<String> contexto = contextoDoAgente(numero);
        atualizarInterno(status, progresso, etapa, mensagem, agente, numero, acao, contexto, "");
    }

    public synchronized void atualizarEquipe3(AnaliseEspecializadaStatus status, int progresso, String agente, int agenteNumero,
                                               String acao, String mensagem, List<String> contexto, String resultadoParcial) {
        this.equipeAtual = "EQUIPE_3_DATAJUD";
        atualizarInterno(status, progresso, "Equipe 3 — " + agente + " — " + acao, mensagem, agente, agenteNumero, acao, contexto, resultadoParcial);
    }

    public synchronized void atualizarDetalhado(AnaliseEspecializadaStatus status, int progresso, String agente,
                                                  int agenteNumero, String acao, String mensagem,
                                                  List<String> contextoRecebido, String resultadoParcial) {
        atualizarInterno(status, progresso, etapaComEquipe(agente + " — " + acao), mensagem, agente, agenteNumero, acao, contextoRecebido, resultadoParcial);
    }

    private String etapaComEquipe(String etapa) { return "Equipe 2 — " + etapa; }

    private void atualizarInterno(AnaliseEspecializadaStatus status, int progresso, String etapa, String mensagem,
                                  String agente, int agenteNumero, String acao, List<String> contextoRecebido,
                                  String resultadoParcial) {
        this.status = status; this.progresso = Math.max(0, Math.min(100, progresso));
        this.etapa = etapa; this.mensagem = mensagem; this.atualizadoEm = Instant.now();
        if (this.progresso > 0) {
            long decorrido = Math.max(1, Duration.between(criadoEm, atualizadoEm).toSeconds());
            this.estimativaRestanteSegundos = Math.max(0, Math.round((decorrido * (100.0 - this.progresso)) / this.progresso));
        }
        if (this.progresso >= 100) this.estimativaRestanteSegundos = 0;
        adicionarLog(agente, agenteNumero, acao, status.name(), mensagem, contextoRecebido, resultadoParcial);
    }

    public void concluir(AnaliseEspecializadaResponse resultado) {
        this.resultado = resultado;
        atualizar(AnaliseEspecializadaStatus.CONCLUIDO, 100, "Equipe 3 — Validação finalizada",
                "Equipes 1, 2 e 3 concluíram o processamento. Relatório consolidado e pronto para revisão.");
    }

    public void falhar(String mensagem) { atualizar(AnaliseEspecializadaStatus.ERRO, progresso, equipeAtual + " — Falha no processamento", mensagem); }

    private void adicionarLog(String agente, int agenteNumero, String acao, String status, String mensagem,
                              List<String> contextoRecebido, String resultadoParcial) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("timestamp", Instant.now().toString());
        item.put("equipe", equipeAtual);
        item.put("agente", agente); item.put("agenteNumero", agenteNumero);
        item.put("totalAgentes", equipeAtual.equals("EQUIPE_3_DATAJUD") ? TOTAL_AGENTES_EQUIPE_3 : TOTAL_AGENTES_EQUIPE_2);
        item.put("status", status); item.put("progresso", progresso); item.put("acao", acao); item.put("etapa", etapa);
        item.put("mensagem", mensagem); item.put("contextoRecebido", contextoRecebido == null ? List.of() : List.copyOf(contextoRecebido));
        item.put("resultadoParcial", resultadoParcial == null ? "" : resultadoParcial);
        item.put("estimativaRestanteSegundos", estimativaRestanteSegundos);
        logs.add(item);
    }

    private String identificarAgente(String etapa, String mensagem) {
        String texto = (etapa + " " + mensagem);
        for (String agente : List.of("Document Agent", "Process Agent", "Contract Agent", "Deadline Agent", "Evidence Agent", "Legal Research Agent", "Drafting Agent", "Senior Lawyer Agent")) if (texto.contains(agente)) return agente;
        return "SYSTEM";
    }

    private int numeroAgente(String agente) {
        return switch (agente) {
            case "Document Agent" -> 1; case "Process Agent" -> 2; case "Contract Agent" -> 3; case "Deadline Agent" -> 4;
            case "Evidence Agent" -> 5; case "Legal Research Agent" -> 6; case "Drafting Agent" -> 7; case "Senior Lawyer Agent" -> 8; default -> 0;
        };
    }

    private String identificarAcao(String etapa, String mensagem) {
        String texto = (etapa + " " + mensagem).toLowerCase();
        if (texto.contains("classific")) return "CLASSIFYING_DOCUMENTS";
        if (texto.contains("processo")) return "ANALYZING_PROCESS";
        if (texto.contains("contrato")) return "ANALYZING_CONTRACT";
        if (texto.contains("prazo")) return "MAPPING_DEADLINES";
        if (texto.contains("evidên")) return "CROSS_REFERENCING_EVIDENCE";
        if (texto.contains("pesquis")) return "LEGAL_RESEARCH";
        if (texto.contains("rascun")) return "DRAFTING";
        if (texto.contains("parecer") || texto.contains("consolid")) return "SENIOR_REVIEW";
        return "PROCESSING";
    }

    private List<String> contextoDoAgente(int numero) {
        List<String> contexto = new ArrayList<>(); contexto.add("Equipe 1 — Análise Base");
        String[] nomes = {"Document Agent", "Process Agent", "Contract Agent", "Deadline Agent", "Evidence Agent", "Legal Research Agent", "Drafting Agent"};
        for (int i = 0; i < Math.min(numero - 1, nomes.length); i++) contexto.add(nomes[i]);
        return contexto;
    }

    public String id() { return id; } public String analiseBaseId() { return analiseBaseId; }
    public String nomeArquivo() { return nomeArquivo; } public Instant criadoEm() { return criadoEm; }
    public Instant atualizadoEm() { return atualizadoEm; } public AnaliseEspecializadaStatus status() { return status; }
    public int progresso() { return progresso; } public String etapa() { return etapa; } public String mensagem() { return mensagem; }
    public long estimativaRestanteSegundos() { return estimativaRestanteSegundos; }
    public String equipeAtual() { return equipeAtual; }
    public AnaliseEspecializadaResponse resultado() { return resultado; }
    public synchronized List<Map<String, Object>> logs() { return List.copyOf(logs); }
}
