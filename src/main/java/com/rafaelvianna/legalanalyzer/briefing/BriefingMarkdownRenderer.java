package com.rafaelvianna.legalanalyzer.briefing;

import com.rafaelvianna.legalanalyzer.web.dto.rag.BriefingAssuncaoResponse;

/**
 * Renderiza o briefing em Markdown, na ordem exata em que o advogado lê:
 * Processo, Partes, Situação, Linha do tempo, Pontos de atenção, Evidências
 * e Perguntas para o advogado.
 *
 * A versão Markdown existe para ser colada em e-mail, pasta do caso ou
 * impressa — sem depender de front-end.
 */
final class BriefingMarkdownRenderer {

    private BriefingMarkdownRenderer() {
    }

    static String render(BriefingAssuncaoResponse b) {
        StringBuilder md = new StringBuilder();

        md.append("# Briefing de assunção do caso\n\n");
        md.append("## Processo\n\n");
        md.append("- **Número:** ").append(b.numeroProcesso()).append('\n');
        md.append("- **Arquivo analisado:** ").append(b.nomeArquivo()).append('\n');
        md.append("- **Gerado em:** ").append(b.geradoEm()).append("\n\n");

        md.append("## Partes\n\n");
        if (b.partes().isEmpty()) {
            md.append("Nenhuma parte identificada no material.\n\n");
        } else {
            md.append("| Papel | Nome | Qualificação |\n|---|---|---|\n");
            b.partes().forEach(p -> md
                    .append("| ").append(celula(p.papel()))
                    .append(" | ").append(celula(p.nome()))
                    .append(" | ").append(celula(p.qualificacao()))
                    .append(" |\n"));
            md.append('\n');
        }

        md.append("## Situação\n\n");
        var s = b.situacao();
        if (s != null) {
            md.append(texto(s.resumoExecutivo())).append("\n\n");
            md.append("- **Onde estamos:** ").append(texto(s.ondeEstamos())).append('\n');
            md.append("- **O que está em jogo:** ").append(texto(s.oQueEstaEmJogo())).append('\n');
            md.append("- **Próxima ação:** ").append(texto(s.proximaAcao())).append("\n\n");
            if (s.destaques() != null && !s.destaques().isEmpty()) {
                md.append("**Destaques:**\n\n");
                s.destaques().forEach(d -> md.append("- ").append(texto(d)).append('\n'));
                md.append('\n');
            }
        }

        md.append("## Linha do tempo\n\n");
        if (b.linhaDoTempo().isEmpty()) {
            md.append("Nenhum evento datado identificado.\n\n");
        } else {
            md.append("| Data | Evento | Onde conferir |\n|---|---|---|\n");
            b.linhaDoTempo().forEach(e -> md
                    .append("| ").append(celula(e.data()))
                    .append(" | ").append(celula(e.evento()))
                    .append(" | ").append(celula(e.ondeConferir()))
                    .append(" |\n"));
            md.append('\n');
        }

        md.append("## Pontos de atenção\n\n");
        if (b.pontosAtencao().isEmpty()) {
            md.append("Nenhuma contradição ou lacuna identificada no material.\n\n");
        } else {
            b.pontosAtencao().forEach(p -> md
                    .append("- **[").append(texto(p.gravidade())).append("] ")
                    .append(texto(p.descricao())).append("** — ")
                    .append("onde conferir: ").append(texto(p.ondeConferir()))
                    .append(". Ação: ").append(texto(p.recomendacao())).append('\n'));
            md.append('\n');
        }

        md.append("## Evidências\n\n");
        if (b.evidencias().isEmpty()) {
            md.append("Nenhuma relação alegação/documento identificada.\n\n");
        } else {
            md.append("| Alegação | Documento | Página | Situação |\n|---|---|---|---|\n");
            b.evidencias().forEach(e -> md
                    .append("| ").append(celula(e.alegacao()))
                    .append(" | ").append(celula(e.documento()))
                    .append(" | ").append(e.pagina() == null ? "—" : String.valueOf(e.pagina()))
                    .append(" | ").append(celula(e.status()))
                    .append(" |\n"));
            md.append('\n');
        }

        md.append("## Perguntas para o advogado\n\n");
        if (b.perguntasParaOAdvogado().isEmpty()) {
            md.append("Nenhuma pendência identificada.\n\n");
        } else {
            b.perguntasParaOAdvogado().forEach(p -> md
                    .append("- ").append(texto(p.pergunta()))
                    .append(" _(").append(texto(p.motivo())).append(")_\n"));
            md.append('\n');
        }

        if (b.avisos() != null && !b.avisos().isEmpty()) {
            md.append("---\n\n### Avisos\n\n");
            b.avisos().forEach(a -> md.append("- ").append(texto(a)).append('\n'));
        }

        return md.toString();
    }

    /** Escapa o pipe para não quebrar as tabelas Markdown. */
    private static String celula(String valor) {
        return texto(valor).replace("|", "\\|").replace("\n", " ");
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? "não identificado" : valor.trim();
    }
}
