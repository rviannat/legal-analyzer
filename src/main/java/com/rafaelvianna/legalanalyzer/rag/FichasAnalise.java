package com.rafaelvianna.legalanalyzer.rag;

import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Converte o resultado dos agentes em passagens de texto ("fichas"), para que
 * o chat possa responder a partir de fatos já apurados — e não apenas do texto
 * cru do PDF.
 *
 * Cada ficha é curta e autossuficiente, com um rótulo que diz de onde a
 * informação veio (ex.: "Análise — cronologia"), porque esse rótulo é o que
 * o advogado vê na citação.
 */
final class FichasAnalise {

    private FichasAnalise() {
    }

    static List<Passagem> montar(AnaliseProcessoResponse base, AnaliseEspecializadaResponse especializada) {
        List<Passagem> fichas = new ArrayList<>();
        if (base != null) {
            adicionar(fichas, "Análise — resumo do processo", texto(base.resumoProcesso()));

            adicionarLista(fichas, "Análise — partes", base.partes(), p ->
                    "%s — %s. Qualificação: %s. %s".formatted(
                            texto(p.nome()), texto(p.papel()), texto(p.qualificacao()), texto(p.observacoes())));

            adicionarLista(fichas, "Análise — cronologia", base.cronologia(), e ->
                    "%s: %s (fase: %s)".formatted(texto(e.data()), texto(e.descricaoEvento()), texto(e.fase())));

            adicionarLista(fichas, "Análise — pedidos", base.pedidos(), p ->
                    "Pedido de %s: %s. Fundamento: %s. Status: %s".formatted(
                            texto(p.parteRequerente()), texto(p.descricaoPedido()),
                            texto(p.fundamentoLegal()), texto(p.status())));

            adicionarLista(fichas, "Análise — decisões", base.decisoes(), d ->
                    "%s — %s por %s: %s. Efeitos: %s".formatted(
                            texto(d.data()), texto(d.tipoDecisao()), texto(d.autoridade()),
                            texto(d.resumoDecisao()), texto(d.efeitos())));

            adicionarLista(fichas, "Análise — prazos", base.prazos(), p ->
                    "%s — %s (criticidade %s, responsável: %s)".formatted(
                            texto(p.data()), texto(p.descricaoPrazo()),
                            texto(p.criticidade()), texto(p.parteResponsavel())));

            adicionarLista(fichas, "Análise — documentos importantes", base.documentosImportantes(), d ->
                    "%s (%s, %s) — relevância %s".formatted(
                            texto(d.nomeDocumento()), texto(d.tipo()),
                            texto(d.dataDocumento()), texto(d.relevancia())));

            adicionarLista(fichas, "Análise — pontos de atenção", base.inconsistencias(), i ->
                    "%s. Elementos em conflito: %s. Gravidade: %s. Recomendação: %s".formatted(
                            texto(i.descricao()), textoLista(i.elementosConflitantes()),
                            texto(i.gravidade()), texto(i.recomendacao())));

            adicionarLista(fichas, "Análise — evidências", base.gruposEvidencia(), g ->
                    "%s: %s. Relevância probatória: %s. %s".formatted(
                            texto(g.categoria()), String.join("; ", g.documentos() == null ? List.of() : g.documentos()),
                            texto(g.relevanciaProbatoria()), texto(g.observacoes())));

            if (base.relatorioExecutivo() != null) {
                var rel = base.relatorioExecutivo();
                adicionar(fichas, "Análise — relatório executivo", """
                        %s
                        Visão geral: %s
                        Pontos críticos: %s
                        Recomendações: %s
                        Próximos passos: %s
                        Conclusão: %s""".formatted(
                        texto(rel.titulo()), texto(rel.visaoGeral()),
                        String.join("; ", rel.pontosCriticos() == null ? List.of() : rel.pontosCriticos()),
                        String.join("; ", rel.recomendacoes() == null ? List.of() : rel.recomendacoes()),
                        String.join("; ", rel.proximosPassos() == null ? List.of() : rel.proximosPassos()),
                        texto(rel.conclusao())));
            }
        }

        if (especializada != null) {
            var proc = especializada.analiseProcessual();
            if (proc != null && proc.processoIdentificado()) {
                adicionar(fichas, "Especializada — análise processual", """
                        Fase atual: %s. Risco geral: %s.
                        Tese do autor: %s
                        Tese do réu: %s
                        Pontos controvertidos: %s
                        Forças: %s
                        Fragilidades: %s
                        Estratégia sugerida: %s
                        Prognóstico: %s""".formatted(
                        texto(proc.faseAtual()), texto(proc.riscoGeral()), texto(proc.teseAutor()), texto(proc.teseReu()),
                        String.join("; ", nuloParaVazio(proc.pontosControvertidos())),
                        String.join("; ", nuloParaVazio(proc.forcas())),
                        String.join("; ", nuloParaVazio(proc.fragilidades())),
                        String.join("; ", nuloParaVazio(proc.estrategiaSugerida())),
                        texto(proc.prognostico())));
            }

            var contrato = especializada.analiseContratual();
            if (contrato != null && contrato.contratoIdentificado()) {
                adicionar(fichas, "Especializada — contrato", "Objeto: %s. Partes: %s".formatted(
                        texto(contrato.objetoContrato()), texto(contrato.partesContratantes())));
                adicionarLista(fichas, "Especializada — cláusulas de risco", contrato.clausulasRisco(), c ->
                        "Cláusula %s (%s): %s. Trecho: \"%s\". Impacto: %s. Recomendação: %s".formatted(
                                texto(c.clausula()), texto(c.gravidade()), texto(c.risco()),
                                texto(c.trechoCitado()), texto(c.impacto()), texto(c.recomendacao())));
                adicionarLista(fichas, "Especializada — multas", contrato.multas(), m ->
                        "Cláusula %s: %s — %s (responsável: %s, %s)".formatted(
                                texto(m.clausula()), texto(m.hipoteseIncidencia()), texto(m.valorOuPercentual()),
                                texto(m.parteResponsavel()), texto(m.cumulatividade())));
                adicionarLista(fichas, "Especializada — obrigações", contrato.obrigacoes(), o ->
                        "%s deve %s (prazo: %s, cláusula %s). Descumprimento: %s".formatted(
                                texto(o.parteObrigada()), texto(o.descricao()), texto(o.prazoCumprimento()),
                                texto(o.clausula()), texto(o.consequenciaDescumprimento())));
            }

            if (especializada.agendaPrazos() != null) {
                adicionarLista(fichas, "Especializada — prazos detalhados",
                        especializada.agendaPrazos().prazos(), p ->
                        "%s: de %s a %s (%s, %s). Fundamento: %s. Criticidade %s. Responsável: %s".formatted(
                                texto(p.descricao()), texto(p.dataInicio()), texto(p.dataFinal()),
                                texto(p.prazoEmDias()), texto(p.tipoContagem()), texto(p.fundamento()),
                                texto(p.criticidade()), texto(p.parteResponsavel())));
                adicionarLista(fichas, "Especializada — agenda de eventos",
                        especializada.agendaPrazos().eventos(), e ->
                        "%s — %s (%s). Comparecimento obrigatório: %s. %s".formatted(
                                texto(e.data()), texto(e.evento()), texto(e.tipo()),
                                texto(e.comparecimentoObrigatorio()), texto(e.observacoes())));
            }

            if (especializada.matrizEvidencias() != null) {
                adicionarLista(fichas, "Especializada — matriz de evidências", 
                        especializada.matrizEvidencias().alegacoes(), a -> {
                            String docs = a.documentosSuporte() == null || a.documentosSuporte().isEmpty()
                                    ? "nenhum documento associado"
                                    : a.documentosSuporte().stream()
                                            .map(d -> "%s (%s, força %s)".formatted(
                                                    texto(d.nomeDocumento()), texto(d.localizacao()),
                                                    texto(d.forcaProbatoria())))
                                            .reduce((x, y) -> x + "; " + y).orElse("");
                            return "Alegação de %s: %s → %s. Grau de sustentação: %s".formatted(
                                    texto(a.parteQueAlega()), texto(a.alegacao()), docs, texto(a.grauSustentacao()));
                        });
            }

            var pesquisa = especializada.pesquisaJuridica();
            if (pesquisa != null && pesquisa.pesquisaRealizada()) {
                adicionarLista(fichas, "Especializada — pesquisa jurídica (fonte autorizada)",
                        pesquisa.referencias(), r ->
                        "%s (%s) — %s. Trecho: \"%s\". Fonte: %s | %s".formatted(
                                texto(r.identificacao()), texto(r.tipo()), texto(r.fonte()),
                                texto(r.trechoRelevante()), texto(r.fonte()), texto(r.url())));
            }

            var parecer = especializada.parecerSenior();
            if (parecer != null) {
                adicionar(fichas, "Especializada — parecer do advogado sênior", """
                        %s
                        Síntese: %s
                        Conclusões: %s
                        Riscos principais: %s
                        Recomendações: %s
                        Próximos passos: %s
                        Pendências para o advogado: %s""".formatted(
                        texto(parecer.titulo()), texto(parecer.sinteseExecutiva()),
                        String.join("; ", nuloParaVazio(parecer.conclusoes())),
                        String.join("; ", nuloParaVazio(parecer.riscosPrincipais())),
                        String.join("; ", nuloParaVazio(parecer.recomendacoes())),
                        String.join("; ", nuloParaVazio(parecer.proximosPassos())),
                        String.join("; ", nuloParaVazio(parecer.pendenciasParaOAdvogado()))));
            }
        }

        return fichas;
    }

    private static <T> void adicionarLista(List<Passagem> fichas, String rotulo, List<T> itens,
                                           Function<T, String> formatador) {
        if (itens == null || itens.isEmpty()) {
            return;
        }
        int indice = 1;
        for (T item : itens) {
            if (item == null) {
                continue;
            }
            adicionar(fichas, rotulo, formatador.apply(item), indice++);
        }
    }

    private static void adicionar(List<Passagem> fichas, String rotulo, String texto) {
        adicionar(fichas, rotulo, texto, 1);
    }

    private static void adicionar(List<Passagem> fichas, String rotulo, String texto, int indice) {
        if (texto == null || texto.isBlank() || texto.equals("não identificado")) {
            return;
        }
        String id = "ficha:" + IndiceProcesso.normalizar(rotulo).replaceAll("[^a-z0-9]+", "-") + "#" + indice;
        fichas.add(new Passagem(id, Passagem.Tipo.FICHA_ANALISE, rotulo, null, rotulo + " → " + texto.trim(), null));
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? "não identificado" : valor.trim();
    }

    private static String textoLista(List<String> valores) {
        if (valores == null || valores.isEmpty()) {
            return "não identificado";
        }
        return valores.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .reduce((a, b) -> a + "; " + b)
                .orElse("não identificado");
    }

    private static List<String> nuloParaVazio(List<String> lista) {
        return lista == null ? List.of() : lista;
    }
}
