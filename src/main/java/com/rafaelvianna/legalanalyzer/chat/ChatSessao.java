package com.rafaelvianna.legalanalyzer.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Histórico de uma conversa sobre um caso. Serve para resolver referências
 * ("essa cláusula", "e o prazo dela?") — não para substituir a recuperação:
 * cada resposta é sempre reancorada nas passagens do índice.
 */
public final class ChatSessao {

    /** Uma troca pergunta/resposta. */
    public record Troca(String pergunta, String resposta) {
    }

    private final String id;
    private final String analiseId;
    private final Instant criadaEm = Instant.now();
    private final List<Troca> trocas = new ArrayList<>();

    public ChatSessao(String id, String analiseId) {
        this.id = id;
        this.analiseId = analiseId;
    }

    public synchronized void registrar(String pergunta, String resposta) {
        trocas.add(new Troca(pergunta, resposta));
    }

    /** Últimas trocas, formatadas para o prompt. */
    public synchronized String historicoFormatado(int maxMensagens) {
        if (trocas.isEmpty()) {
            return "";
        }
        int inicio = Math.max(0, trocas.size() - Math.max(1, maxMensagens / 2));
        StringBuilder sb = new StringBuilder();
        for (Troca troca : trocas.subList(inicio, trocas.size())) {
            sb.append("Advogado: ").append(troca.pergunta()).append('\n');
            sb.append("Assistente: ").append(troca.resposta()).append("\n\n");
        }
        return sb.toString().trim();
    }

    public String id() { return id; }
    public String analiseId() { return analiseId; }
    public Instant criadaEm() { return criadaEm; }

    public synchronized List<Troca> trocas() { return List.copyOf(trocas); }
}
