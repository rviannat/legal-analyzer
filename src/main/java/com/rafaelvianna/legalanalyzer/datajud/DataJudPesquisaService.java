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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DataJudPesquisaService {
    private static final Logger log = LoggerFactory.getLogger(DataJudPesquisaService.class);

    private final AppProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final DataJudService dataJudService;

    public DataJudPesquisaService(AppProperties properties, ObjectMapper mapper, DataJudService dataJudService) {
        this.properties = properties;
        this.mapper = mapper;
        this.dataJudService = dataJudService;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.dataJud().timeoutSecondsOuPadrao()))
                .build();
    }

    public DataJudPesquisaResponse pesquisarCnj(String numeroProcesso) {
        DataJudInfo info = dataJudService.consultar(numeroProcesso);
        return new DataJudPesquisaResponse(
                UUID.randomUUID().toString(), "CNJ", info.tribunal(), null, true,
                info.mensagem(), info.consultadoEm(), info, List.of(), 0);
    }

    /**
     * Pesquisa uma amostra de processos por tribunal + assunto TPU.
     * A consulta inclui movimentos de baixa/encerramento como sinal de seleção,
     * mas não afirma que o registro está definitivamente baixado quando o DataJud
     * não fornece essa situação como campo direto no documento público.
     */
    public DataJudPesquisaResponse pesquisarPorTribunalAssunto(String tribunal, String assunto, int tamanho) {
        if (!properties.dataJud().configurado()) {
            return new DataJudPesquisaResponse(UUID.randomUUID().toString(), "ORGAO_ASSUNTO", tribunal, assunto, false,
                    "Integração DataJud não configurada.", Instant.now(), null, List.of(), 0);
        }
        String alias = normalizarTribunal(tribunal);
        if (alias == null || alias.isBlank()) {
            return new DataJudPesquisaResponse(UUID.randomUUID().toString(), "ORGAO_ASSUNTO", tribunal, assunto, false,
                    "Informe o alias do tribunal, por exemplo tjsp, tjrj, trf3 ou trt2.", Instant.now(), null, List.of(), 0);
        }
        if (assunto == null || assunto.isBlank()) {
            return new DataJudPesquisaResponse(UUID.randomUUID().toString(), "ORGAO_ASSUNTO", alias, assunto, false,
                    "Informe o código TPU ou o nome do assunto.", Instant.now(), null, List.of(), 0);
        }
        int size = Math.max(1, Math.min(tamanho <= 0 ? 10 : tamanho, 25));
        String endpoint = properties.dataJud().baseUrlOuPadrao() + "/api_publica_" + alias + "/_search";
        try {
            JsonNode query = mapper.createObjectNode();
            var bool = mapper.createObjectNode();
            var must = mapper.createArrayNode();
            var assuntoNode = mapper.createObjectNode();
            boolean codigo = assunto.matches("\\d+");
            assuntoNode.set(codigo ? "match" : "match", mapper.createObjectNode()
                    .put(codigo ? "assuntos.codigo" : "assuntos.nome", codigo ? Long.parseLong(assunto) : assunto));
            must.add(assuntoNode);

            var encerramento = mapper.createObjectNode();
            var should = mapper.createArrayNode();
            for (String termo : List.of("baixa", "baixado", "arquivamento", "arquivado", "extincao", "extinto", "transito em julgado")) {
                should.add(mapper.createObjectNode().set("match", mapper.createObjectNode().put("movimentos.nome", termo)));
            }
            encerramento.set("should", should);
            encerramento.put("minimum_should_match", 1);
            must.add(encerramento);
            bool.set("must", must);
            query.set("query", mapper.createObjectNode().set("bool", bool));
            query.put("size", size);
            query.set("sort", mapper.createArrayNode().add(mapper.createObjectNode().set("dataAjuizamento", mapper.createObjectNode().put("order", "desc"))));

            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(properties.dataJud().timeoutSecondsOuPadrao()))
                    .header("Authorization", "APIKey " + properties.dataJud().apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(query.toString()))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new DataJudPesquisaResponse(UUID.randomUUID().toString(), "ORGAO_ASSUNTO", alias, assunto, false,
                        "DataJud respondeu HTTP " + response.statusCode() + ".", Instant.now(), null, List.of(), 0);
            }
            JsonNode hits = mapper.readTree(response.body()).path("hits").path("hits");
            List<DataJudAmostra> amostra = new ArrayList<>();
            if (hits.isArray()) for (JsonNode hit : hits) amostra.add(mapearAmostra(hit.path("_source")));
            return new DataJudPesquisaResponse(UUID.randomUUID().toString(), "ORGAO_ASSUNTO", alias, assunto, true,
                    amostra.isEmpty() ? "Nenhum processo da amostra foi localizado." : "Amostra oficial localizada no DataJud.", Instant.now(), null, amostra, amostra.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DataJudPesquisaResponse(UUID.randomUUID().toString(), "ORGAO_ASSUNTO", alias, assunto, false,
                    "Consulta DataJud interrompida.", Instant.now(), null, List.of(), 0);
        } catch (Exception e) {
            log.warn("Falha na pesquisa agregada DataJud tribunal={} assunto={}: {}", alias, assunto, e.getMessage());
            return new DataJudPesquisaResponse(UUID.randomUUID().toString(), "ORGAO_ASSUNTO", alias, assunto, false,
                    "DataJud indisponível no momento: " + e.getMessage(), Instant.now(), null, List.of(), 0);
        }
    }

    private DataJudAmostra mapearAmostra(JsonNode source) {
        List<String> assuntos = new ArrayList<>();
        JsonNode assuntosNode = source.path("assuntos");
        if (assuntosNode.isArray()) for (JsonNode a : assuntosNode) {
            String codigo = a.path("codigo").asText("");
            String nome = a.path("nome").asText("");
            String valor = codigo.isBlank() ? nome : codigo + " — " + nome;
            if (!valor.isBlank()) assuntos.add(valor);
        }
        JsonNode movimentos = source.path("movimentos");
        String ultima = null;
        boolean baixa = false;
        if (movimentos.isArray()) for (JsonNode m : movimentos) {
            String nome = m.path("nome").asText("");
            String data = m.path("dataHora").asText("");
            if (!nome.isBlank()) {
                if (baixa(nome)) baixa = true;
                if (ultima == null || data.compareTo(ultima.substring(0, Math.min(19, ultima.length()))) > 0) ultima = data + " — " + nome;
            }
        }
        return new DataJudAmostra(
                source.path("numeroProcesso").asText(null),
                source.path("classe").path("codigo").asText(null),
                source.path("classe").path("nome").asText(null),
                assuntos,
                source.path("grau").asText(null),
                source.path("orgaoJulgador").path("nome").asText(null),
                source.path("dataAjuizamento").asText(null),
                ultima,
                baixa);
    }

    private boolean baixa(String nome) {
        String n = nome.toLowerCase(java.util.Locale.ROOT);
        return n.contains("baixa") || n.contains("baixado") || n.contains("arquivamento") || n.contains("arquivado") || n.contains("extinção") || n.contains("extincao") || n.contains("trânsito em julgado") || n.contains("transito em julgado");
    }

    private String normalizarTribunal(String tribunal) {
        if (tribunal == null) return null;
        String value = tribunal.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.startsWith("api_publica_")) value = value.substring("api_publica_".length());
        if (!value.matches("[a-z0-9-]{2,15}")) return null;
        return value;
    }
}
