package com.rafaelvianna.legalanalyzer.datajud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class DataJudService {
    private static final Logger log = LoggerFactory.getLogger(DataJudService.class);
    private static final Map<String, String> TRIBUNAIS_ESTADUAIS = Map.ofEntries(
            Map.entry("01", "tjac"), Map.entry("02", "tjal"), Map.entry("03", "tjap"), Map.entry("04", "tjam"),
            Map.entry("05", "tjba"), Map.entry("06", "tjce"), Map.entry("07", "tjdft"), Map.entry("08", "tjes"),
            Map.entry("09", "tjgo"), Map.entry("10", "tjma"), Map.entry("11", "tjmt"), Map.entry("12", "tjms"),
            Map.entry("13", "tjmg"), Map.entry("14", "tjpa"), Map.entry("15", "tjpb"), Map.entry("16", "tjpr"),
            Map.entry("17", "tjpe"), Map.entry("18", "tjpi"), Map.entry("19", "tjrj"), Map.entry("20", "tjrn"),
            Map.entry("21", "tjrs"), Map.entry("22", "tjro"), Map.entry("23", "tjrr"), Map.entry("24", "tjsc"),
            Map.entry("25", "tjse"), Map.entry("26", "tjsp"), Map.entry("27", "tjto"));

    private final AppProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public DataJudService(AppProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(properties.dataJud().timeoutSecondsOuPadrao())).build();
    }

    public DataJudInfo consultar(String numeroProcesso) {
        if (!properties.dataJud().configurado()) return DataJudInfo.naoConfigurado();
        String digits = numeroProcesso == null ? "" : numeroProcesso.replaceAll("\\D", "");
        if (digits.length() != 20) return DataJudInfo.numeroNaoIdentificado();

        String tribunal = resolverTribunal(digits);
        if (tribunal == null) return new DataJudInfo(DataJudStatus.INDISPONIVEL, numeroProcesso, null, null, false, null, null, null, null, null,
                "Tribunal não mapeado para a numeração CNJ identificada.", Instant.now());

        String endpoint = properties.dataJud().baseUrlOuPadrao() + "/api_publica_" + tribunal + "/_search";
        try {
            String body = mapper.createObjectNode().set("query", mapper.createObjectNode().set("match", mapper.createObjectNode().put("numeroProcesso", digits))).toString();
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(properties.dataJud().timeoutSecondsOuPadrao()))
                    .header("Authorization", "APIKey " + properties.dataJud().apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new DataJudInfo(DataJudStatus.INDISPONIVEL, numeroProcesso, tribunal, endpoint, false, null, null, null, null, null,
                        "DataJud respondeu HTTP " + response.statusCode() + ".", Instant.now());
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode hits = root.path("hits").path("hits");
            if (!hits.isArray() || hits.isEmpty()) {
                return new DataJudInfo(DataJudStatus.NAO_ENCONTRADO, numeroProcesso, tribunal, endpoint, false, 0, null, null, null, null,
                        "Processo não localizado na base pública do DataJud. Processos em segredo de justiça não são disponibilizados pela API pública.", Instant.now());
            }
            JsonNode source = hits.get(0).path("_source");
            JsonNode movimentos = source.path("movimentos");
            String ultima = null;
            if (movimentos.isArray() && !movimentos.isEmpty()) {
                JsonNode latest = movimentos.get(0);
                for (JsonNode m : movimentos) if (m.path("dataHora").asText("").compareTo(latest.path("dataHora").asText("")) > 0) latest = m;
                ultima = latest.path("dataHora").asText(null) + " — " + latest.path("nome").asText("Movimentação");
            }
            return new DataJudInfo(DataJudStatus.ENCONTRADO, numeroProcesso, tribunal, endpoint, true,
                    movimentos.isArray() ? movimentos.size() : 0, ultima,
                    source.path("classe").path("nome").asText(null), source.path("orgaoJulgador").path("nome").asText(null),
                    source.path("grau").asText(null), "Processo localizado na base pública do DataJud/CNJ.", Instant.now());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return indisponivel(numeroProcesso, tribunal, endpoint, "Consulta DataJud interrompida.");
        } catch (Exception e) {
            log.warn("Falha na consulta DataJud para {}: {}", numeroProcesso, e.getMessage());
            return indisponivel(numeroProcesso, tribunal, endpoint, "DataJud indisponível no momento: " + e.getMessage());
        }
    }

    private DataJudInfo indisponivel(String numero, String tribunal, String endpoint, String mensagem) {
        return new DataJudInfo(DataJudStatus.INDISPONIVEL, numero, tribunal, endpoint, false, null, null, null, null, null, mensagem, Instant.now());
    }

    static String resolverTribunal(String digits) {
        String justica = digits.substring(13, 14);
        String codigo = digits.substring(14, 16);
        if ("8".equals(justica)) return TRIBUNAIS_ESTADUAIS.get(codigo);
        if ("4".equals(justica) && codigo.matches("0[1-6]")) return "trf" + Integer.parseInt(codigo);
        if ("5".equals(justica) && Integer.parseInt(codigo) >= 1 && Integer.parseInt(codigo) <= 24) return "trt" + Integer.parseInt(codigo);
        if ("1".equals(justica)) return "stf";
        if ("3".equals(justica)) return "stj";
        return null;
    }
}
