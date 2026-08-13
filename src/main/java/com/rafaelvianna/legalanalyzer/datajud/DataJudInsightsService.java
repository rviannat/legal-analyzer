package com.rafaelvianna.legalanalyzer.datajud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Camada de inteligência estatística. A fonte agregada é opcional e deve ser oficial/configurada. */
@Service
public class DataJudInsightsService {
    private final AppProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public DataJudInsightsService(AppProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(properties.dataJud().timeoutSecondsOuPadrao())).build();
    }

    public DataJudInsights analisar(DataJudInfo info) {
        if (info == null || info.status() != DataJudStatus.ENCONTRADO) return DataJudInsights.indisponivel(info == null ? vazio() : info, "Processo DataJud ainda não disponível.");
        Integer idade = idadeDias(info.dataAjuizamento());
        if (!properties.dataJud().estatisticasConfiguradas()) {
            return new DataJudInsights(DataJudInsights.Status.PARCIAL, info.tribunal(), info.classeProcessualCodigo(), info.classeProcessual(), info.orgaoJulgadorCodigo(), info.orgaoJulgador(), idade, null, null, null, null, null, null, null, "Idade do processo calculada a partir da data oficial de ajuizamento. Para médias, acordo, perícia e congestionamento é necessária uma fonte estatística agregada oficial configurada.", Instant.now());
        }
        try {
            String url = properties.dataJud().estatisticasUrl();
            String body = mapper.createObjectNode()
                    .put("tribunal", safe(info.tribunal()))
                    .put("classeCodigo", safe(info.classeProcessualCodigo()))
                    .put("orgaoCodigo", safe(info.orgaoJulgadorCodigo()))
                    .put("orgaoNome", safe(info.orgaoJulgador()))
                    .toString();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(properties.dataJud().timeoutSecondsOuPadrao())).header("Authorization", "APIKey " + properties.dataJud().apiKey()).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) throw new IllegalStateException("HTTP " + res.statusCode());
            JsonNode n = mapper.readTree(res.body());
            Integer media = integer(n, "duracaoMediaDias");
            Double acordo = percent(n, "probabilidadeAcordo");
            Double pericia = percent(n, "probabilidadePericia");
            Double congestionamento = percent(n, "congestionamento");
            String nivel = n.path("nivelCongestionamento").asText(null);
            String fonte = n.path("fonte").asText(properties.dataJud().estatisticasUrl());
            return new DataJudInsights(DataJudInsights.Status.DISPONIVEL, info.tribunal(), info.classeProcessualCodigo(), info.classeProcessual(), info.orgaoJulgadorCodigo(), info.orgaoJulgador(), idade, media, media == null || idade == null || media == 0 ? null : Math.min(999, Math.round(idade * 100f / media)), acordo, pericia, congestionamento, nivel, fonte, "Insights estatísticos calculados com a fonte agregada configurada.", Instant.now());
        } catch (Exception e) {
            return new DataJudInsights(DataJudInsights.Status.PARCIAL, info.tribunal(), info.classeProcessualCodigo(), info.classeProcessual(), info.orgaoJulgadorCodigo(), info.orgaoJulgador(), idade, null, null, null, null, null, null, properties.dataJud().estatisticasUrl(), "Fonte estatística indisponível; exibindo apenas idade oficial do processo.", Instant.now());
        }
    }

    private static Integer idadeDias(String data) {
        if (data == null || data.isBlank()) return null;
        try { return Math.max(0, (int) ChronoUnit.DAYS.between(Instant.parse(data), Instant.now())); }
        catch (Exception ignored) { return null; }
    }
    private static Integer integer(JsonNode n, String field) { return n.hasNonNull(field) ? n.get(field).asInt() : null; }
    private static Double percent(JsonNode n, String field) { return n.hasNonNull(field) ? n.get(field).asDouble() : null; }
    private static String safe(String s) { return s == null ? "" : s; }
    private static DataJudInfo vazio() { return DataJudInfo.numeroNaoIdentificado(); }
}
