package com.rafaelvianna.legalanalyzer.analysis.external.agents;

import com.rafaelvianna.legalanalyzer.analysis.external.ExternalAgentResult;
import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import com.rafaelvianna.legalanalyzer.datajud.DataJudMovimento;
import com.rafaelvianna.legalanalyzer.web.dto.EventoCronologiaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.ExtractionResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reunião da Equipe 3.
 *
 * Compara fatos processuais já extraídos do documento com a fonte oficial
 * DataJud. A regra é conservadora: divergência é sinalizada, nunca tratada
 * automaticamente como erro do documento ou da fonte externa.
 */
@Component
public class JusReconciliationAgent {

    public ExternalAgentResult execute(List<ExternalAgentResult> resultados,
                                       ExtractionResult internalContext,
                                       DataJudInfo dataJud) {
        List<Map<String, Object>> divergencias = new ArrayList<>();
        List<Map<String, Object>> confirmacoes = new ArrayList<>();
        List<Map<String, Object>> novosDados = new ArrayList<>();

        if (internalContext == null || dataJud == null) {
            return new ExternalAgentResult(
                    "JusReconciliationAgent", "SEM_CONTEXTO",
                    "Não foi possível reconciliar: contexto interno ou DataJud ausente.",
                    Map.of("divergencias", divergencias, "confirmacoes", confirmacoes, "novosDados", novosDados),
                    Instant.now());
        }

        reconciliarTimeline(internalContext.eventosCronologia(), dataJud.movimentos(),
                divergencias, confirmacoes, novosDados);

        compararMetadados(internalContext, dataJud, divergencias, confirmacoes);

        int totalComparacoes = confirmacoes.size() + divergencias.size();
        int consistencia = totalComparacoes == 0
                ? 0
                : (int) Math.round((confirmacoes.size() * 100.0) / totalComparacoes);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("processo", dataJud.numeroProcesso());
        data.put("tribunal", dataJud.tribunal());
        data.put("consistenciaOperacional", consistencia);
        data.put("confirmacoes", List.copyOf(confirmacoes));
        data.put("divergencias", List.copyOf(divergencias));
        data.put("novosDados", List.copyOf(novosDados));
        data.put("agentesRecebidos", resultados == null ? 0 : resultados.size());
        data.put("fonte", "DataJud");

        String status = divergencias.isEmpty() ? "CONSISTENTE" : "DIVERGENCIAS_ENCONTRADAS";
        String summary = divergencias.isEmpty()
                ? "Nenhuma divergência encontrada entre a linha do tempo analisada e os dados públicos consultados."
                : divergencias.size() + " divergência(s) encontrada(s) entre o documento e o DataJud.";

        return new ExternalAgentResult("JusReconciliationAgent", status, summary, data, Instant.now());
    }

    /** Compatibilidade com o contrato inicial da equipe. */
    public ExternalAgentResult execute(List<ExternalAgentResult> resultados, Object internalContext) {
        return new ExternalAgentResult("JusReconciliationAgent", "AGUARDANDO_DADOS",
                "Use a sobrecarga com ExtractionResult e DataJudInfo para executar a reconciliação.",
                Map.of("agentesRecebidos", resultados == null ? 0 : resultados.size(),
                        "contextoInternoRecebido", internalContext != null), Instant.now());
    }

    private void reconciliarTimeline(List<EventoCronologiaDTO> internas,
                                     List<DataJudMovimento> externas,
                                     List<Map<String, Object>> divergencias,
                                     List<Map<String, Object>> confirmacoes,
                                     List<Map<String, Object>> novosDados) {
        List<EventoCronologiaDTO> safeInternas = internas == null ? List.of() : internas;
        List<DataJudMovimento> safeExternas = externas == null ? List.of() : externas;

        for (EventoCronologiaDTO interno : safeInternas) {
            String dataInterna = normalizarData(interno.data());
            String descricaoInterna = normalizar(interno.descricaoEvento());
            if (dataInterna == null || descricaoInterna.isBlank()) continue;

            DataJudMovimento correspondente = safeExternas.stream()
                    .filter(e -> normalizarData(e.dataHora()).equals(dataInterna))
                    .filter(e -> similar(descricaoInterna, normalizar(e.nome() + " " + nvl(e.complemento()))))
                    .findFirst().orElse(null);

            if (correspondente != null) {
                confirmacoes.add(Map.of(
                        "tipo", "MOVIMENTACAO",
                        "data", dataInterna,
                        "interno", interno.descricaoEvento(),
                        "externo", nvl(correspondente.nome()),
                        "fonte", "DataJud"));
                continue;
            }

            boolean mesmaData = safeExternas.stream()
                    .anyMatch(e -> normalizarData(e.dataHora()).equals(dataInterna));
            if (mesmaData) {
                divergencias.add(divergencia("ALTA", "MOVIMENTACAO", dataInterna,
                        interno.descricaoEvento(), "Existe evento DataJud na mesma data, mas a descrição diverge."));
            } else {
                divergencias.add(divergencia("MEDIA", "MOVIMENTACAO", dataInterna,
                        interno.descricaoEvento(), "Evento identificado no documento não foi localizado na amostra DataJud."));
            }
        }

        for (DataJudMovimento externo : safeExternas) {
            String dataExterna = normalizarData(externo.dataHora());
            String descricaoExterna = normalizar(externo.nome() + " " + nvl(externo.complemento()));
            if (dataExterna == null || descricaoExterna.isBlank()) continue;

            boolean existeInterno = safeInternas.stream()
                    .filter(i -> dataExterna.equals(normalizarData(i.data())))
                    .anyMatch(i -> similar(descricaoExterna, normalizar(i.descricaoEvento())));

            if (!existeInterno) {
                novosDados.add(Map.of(
                        "tipo", "MOVIMENTACAO",
                        "data", dataExterna,
                        "descricao", nvl(externo.nome()),
                        "fonte", "DataJud",
                        "observacao", "Evento público não identificado no documento analisado."));
            }
        }
    }

    private void compararMetadados(ExtractionResult interno,
                                   DataJudInfo externo,
                                   List<Map<String, Object>> divergencias,
                                   List<Map<String, Object>> confirmacoes) {
        String cnj = externo.numeroProcesso();
        if (cnj != null && !cnj.isBlank()) {
            confirmacoes.add(Map.of("tipo", "IDENTIFICACAO", "campo", "numeroProcesso",
                    "valor", cnj, "fonte", "DataJud"));
        }

        if (externo.classeProcessual() != null && !externo.classeProcessual().isBlank()) {
            boolean mencionada = concatDocumentos(interno).contains(normalizar(externo.classeProcessual()));
            if (!mencionada) {
                divergencias.add(divergencia("BAIXA", "METADADO", "classeProcessual",
                        externo.classeProcessual(), "Classe oficial não apareceu no contexto textual estruturado."));
            } else {
                confirmacoes.add(Map.of("tipo", "METADADO", "campo", "classeProcessual",
                        "valor", externo.classeProcessual(), "fonte", "DataJud"));
            }
        }
    }

    private String concatDocumentos(ExtractionResult r) {
        StringBuilder b = new StringBuilder();
        if (r.documentosImportantes() != null) r.documentosImportantes().forEach(d -> b.append(' ').append(d));
        if (r.eventosCronologia() != null) r.eventosCronologia().forEach(e -> b.append(' ').append(e));
        return normalizar(b.toString());
    }

    private Map<String, Object> divergencia(String severidade, String tipo, String referencia,
                                             String valor, String motivo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("severidade", severidade);
        m.put("tipo", tipo);
        m.put("referencia", referencia);
        m.put("valorInterno", valor);
        m.put("motivo", motivo);
        m.put("fonteExterna", "DataJud");
        return m;
    }

    private boolean similar(String a, String b) {
        if (a.isBlank() || b.isBlank()) return false;
        if (a.equals(b) || a.contains(b) || b.contains(a)) return true;
        String[] tokens = a.split("\\s+");
        long relevantes = java.util.Arrays.stream(tokens).filter(t -> t.length() > 4).count();
        long comuns = java.util.Arrays.stream(tokens).filter(t -> t.length() > 4 && b.contains(t)).count();
        return relevantes > 0 && ((double) comuns / relevantes) >= 0.35;
    }

    private String normalizarData(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        try {
            return LocalDate.parse(v.substring(0, Math.min(10, v.length()))).toString();
        } catch (Exception ignored) {
            try {
                return Instant.parse(v).toString().substring(0, 10);
            } catch (Exception ignoredAgain) {
                return v.length() >= 10 ? v.substring(0, 10) : v;
            }
        }
    }

    private String normalizar(String value) {
        if (value == null) return "";
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private String nvl(String value) { return value == null ? "" : value; }
}
