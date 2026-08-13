package com.rafaelvianna.legalanalyzer.datajud;

import com.rafaelvianna.legalanalyzer.web.dto.EventoCronologiaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.ParteDTO;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Auditoria entre documento e metadados públicos do DataJud. */
public record DataJudAuditoria(
        DataJudInfo dataJud, boolean capaEnriquecida, List<String> camposEnriquecidos,
        String validacaoPartesStatus, List<ParteDTO> partesExtraidas, List<String> divergencias, String observacao) {

    public static DataJudAuditoria indisponivel(DataJudInfo info, List<ParteDTO> partes) {
        return new DataJudAuditoria(info, false, List.of(), "NAO_DISPONIVEL_NA_API_PUBLICA", partes == null ? List.of() : List.copyOf(partes), List.of(),
                "A API Pública do DataJud resguarda os dados das partes. A validação de nomes exige outra fonte oficial.");
    }

    public static DataJudAuditoria de(DataJudInfo info, List<ParteDTO> partes) {
        if (info == null) return indisponivel(DataJudInfo.naoConfigurado(), partes);
        if (!info.encontrado()) return new DataJudAuditoria(info, false, List.of(), "NAO_CONCLUSIVA", partes == null ? List.of() : List.copyOf(partes), List.of(),
                "Não há registro público do processo no DataJud para realizar o enriquecimento da capa.");
        return new DataJudAuditoria(info, true, List.of("tribunal", "grau", "classe processual", "órgão julgador", "movimentações"),
                "NAO_DISPONIVEL_NA_API_PUBLICA", partes == null ? List.of() : List.copyOf(partes), List.of(),
                "Capa enriquecida com metadados oficiais do DataJud. Os nomes das partes não foram usados como critério de divergência.");
    }

    public static DataJudTimelineAuditoria sincronizarTimeline(DataJudInfo info, String textoPdf, List<EventoCronologiaDTO> eventosPdf) {
        if (info == null || !info.encontrado() || info.movimentos().isEmpty()) return DataJudTimelineAuditoria.indisponivel(info);
        String texto = normalizar(textoPdf); List<EventoCronologiaDTO> pdf = eventosPdf == null ? List.of() : eventosPdf;
        List<DataJudTimelineEvento> hibrida = new ArrayList<>();
        List<DataJudTimelineEvento> ocultas = new ArrayList<>();
        List<DataJudTimelineEvento> alertasPrazos = new ArrayList<>();
        int correspondencias = 0; String publicacao = null; String transito = null;
        for (DataJudMovimento m : info.movimentos()) {
            String data = data(m.dataHora()); boolean match = matchTexto(m, texto) || matchCronologia(m, pdf);
            if (match) correspondencias++;
            String desc = m.nome() == null ? "Movimentação oficial" : m.nome();
            if (m.complemento() != null && !m.complemento().isBlank()) desc += " — " + m.complemento();
            String fase = fase(m.nome());
            DataJudTimelineEvento e = new DataJudTimelineEvento(data, desc, fase, "DATAJUD", match ? "CORRESPONDENTE_NO_PDF" : "NAO_ENCONTRADA_NO_PDF", true);
            hibrida.add(e); if (!match) ocultas.add(e);
            if (geraAlertaPrazo(m)) alertasPrazos.add(e);
            if ("PUBLICAÇÃO".equals(fase)) publicacao = maisRecente(publicacao, data);
            if ("TRÂNSITO EM JULGADO".equals(fase)) transito = maisRecente(transito, data);
        }
        for (EventoCronologiaDTO e : pdf) if (e != null) hibrida.add(new DataJudTimelineEvento(e.data(), e.descricaoEvento(), e.fase(), "PDF", "EXTRAIDO_DO_PDF", false));
        Comparator<DataJudTimelineEvento> ordem = Comparator.comparing(e -> parse(e.data()), Comparator.nullsLast(Comparator.naturalOrder()));
        hibrida.sort(ordem); ocultas.sort(ordem); alertasPrazos.sort(ordem);
        String obs = ocultas.isEmpty() ? "Todas as movimentações públicas encontraram correspondência no PDF ou na cronologia extraída." :
                "Há movimentações oficiais sem correspondente claro no PDF. O alerta indica uma lacuna de correspondência, não prova isoladamente que o documento esteja incompleto.";
        if (!alertasPrazos.isEmpty()) obs += " Foram identificadas " + alertasPrazos.size() + " movimentação(ões) potencialmente geradora(s) de prazo; o sistema não infere automaticamente a quantidade de dias.";
        return new DataJudTimelineAuditoria(info.status(), info.movimentos().size(), pdf.size(), correspondencias, ocultas.size(), info.movimentos(), List.copyOf(hibrida), List.copyOf(ocultas), List.copyOf(alertasPrazos), publicacao, transito, obs);
    }

    private static boolean geraAlertaPrazo(DataJudMovimento m) {
        String s = normalizar((m.nome() == null ? "" : m.nome()) + " " + (m.complemento() == null ? "" : m.complemento()));
        if (s.isBlank()) return false;
        return containsAny(s,
                "intimacao", "intimado", "intime-se", "citacao", "citado", "cite-se",
                "ato ordinatorio", "ato ordinatório", "prazo", "manifestacao", "manifestar-se",
                "ciencia", "ciente", "comunicacao", "notificacao", "notificado",
                "conclusao", "conclusos", "despacho", "determinada a intimacao");
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(normalizar(term))) return true;
        return false;
    }

    private static boolean matchTexto(DataJudMovimento m, String texto) {
        String d = data(m.dataHora()); if (d == null || !texto.contains(normalizar(d))) return false;
        Set<String> tokens = tokens(m.nome()); if (tokens.isEmpty()) return true;
        return tokens.stream().filter(texto::contains).count() >= Math.min(2, tokens.size());
    }

    private static boolean matchCronologia(DataJudMovimento m, List<EventoCronologiaDTO> eventos) {
        String d = data(m.dataHora()); Set<String> tokens = tokens(m.nome());
        for (EventoCronologiaDTO e : eventos) {
            if (e == null || !mesmaData(d, e.data())) continue;
            String x = normalizar((e.descricaoEvento() == null ? "" : e.descricaoEvento()) + " " + (e.fase() == null ? "" : e.fase()));
            if (tokens.isEmpty() || tokens.stream().anyMatch(x::contains)) return true;
        }
        return false;
    }

    private static Set<String> tokens(String s) {
        if (s == null) return Set.of(); Set<String> r = new HashSet<>();
        for (String x : normalizar(s).split("\\s+")) if (x.length() >= 5 && !Set.of("processo", "decisao", "movimentacao", "judicial").contains(x)) r.add(x);
        return r;
    }

    private static String fase(String s) {
        String n = normalizar(s); if (n.contains("publicacao") || n.contains("public")) return "PUBLICAÇÃO";
        if (n.contains("transito em julgado") || n.contains("transito")) return "TRÂNSITO EM JULGADO";
        if (n.contains("sentenca") || n.contains("acordao") || n.contains("decisao")) return "DECISÃO";
        if (n.contains("intimacao") || n.contains("citacao")) return "COMUNICAÇÃO PROCESSUAL";
        return "MOVIMENTAÇÃO OFICIAL";
    }

    private static String data(String value) { if (value == null) return null; try { return OffsetDateTime.parse(value).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")); } catch (Exception e) { return value.length() >= 10 ? value.substring(0, 10) : value; } }
    private static boolean mesmaData(String a, String b) { return a != null && b != null && (a.equals(b) || a.equals(data(b))); }
    private static String normalizar(String s) { return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
    private static LocalDate parse(String s) { try { return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd/MM/yyyy")); } catch (Exception e) { return null; } }
    private static String maisRecente(String a, String b) { if (b == null) return a; if (a == null) return b; LocalDate x=parse(a), y=parse(b); return x == null || (y != null && y.isAfter(x)) ? b : a; }
}
