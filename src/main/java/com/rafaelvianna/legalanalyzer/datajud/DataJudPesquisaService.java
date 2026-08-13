package com.rafaelvianna.legalanalyzer.datajud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
        return new DataJudPesquisaResponse(UUID.randomUUID().toString(), "CNJ", info.tribunal(), null, true,
                info.mensagem(), info.consultadoEm(), info, List.of(), 0);
    }

    /** Pesquisa uma amostra de processos por tribunal + assunto TPU, priorizando registros com sinais de encerramento. */
    public DataJudPesquisaResponse pesquisarPorTribunalAssunto(String tribunal, String assunto, int tamanho) {
        if (!properties.dataJud().configurado()) return respostaFalha(tribunal, assunto, "Integração DataJud não configurada.");
        String alias = normalizarTribunal(tribunal);
        if (alias == null) return respostaFalha(tribunal, assunto, "Informe o alias do tribunal, por exemplo tjsp, tjrj, trf3 ou trt2.");
        if (assunto == null || assunto.isBlank()) return respostaFalha(alias, assunto, "Informe o código TPU ou o nome do assunto.");

        int size = Math.max(1, Math.min(tamanho <= 0 ? 10 : tamanho, 25));
        String endpoint = properties.dataJud().baseUrlOuPadrao() + "/api_publica_" + alias + "/_search";
        try {
            ObjectNode query = mapper.createObjectNode();
            ObjectNode bool = mapper.createObjectNode();
            var must = mapper.createArrayNode();
            boolean codigo = assunto.matches("\\d+");
            ObjectNode assuntoMatch = mapper.createObjectNode();
            if (codigo) assuntoMatch.set("assuntos.codigo", mapper.getNodeFactory().numberNode(Long.parseLong(assunto)));
            else assuntoMatch.put("assuntos.nome", assunto);
            must.add(mapper.createObjectNode().set("match", assuntoMatch));

            var should = mapper.createArrayNode();
            for (String termo : List.of("baixa", "baixado", "arquivamento", "arquivado", "extincao", "extinto", "transito em julgado")) {
                should.add(mapper.createObjectNode().set("match", mapper.createObjectNode().put("movimentos.nome", termo)));
            }
            ObjectNode encerramento = mapper.createObjectNode();
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
            if (response.statusCode() < 200 || response.statusCode() >= 300) return respostaFalha(alias, assunto, "DataJud respondeu HTTP " + response.statusCode() + ".");

            JsonNode hits = mapper.readTree(response.body()).path("hits").path("hits");
            List<DataJudAmostra> amostra = new ArrayList<>();
            if (hits.isArray()) for (JsonNode hit : hits) amostra.add(mapearAmostra(hit.path("_source")));
            return new DataJudPesquisaResponse(UUID.randomUUID().toString(), "ORGAO_ASSUNTO", alias, assunto, true,
                    amostra.isEmpty() ? "Nenhum processo da amostra foi localizado." : "Amostra oficial localizada no DataJud.", Instant.now(), null, amostra, amostra.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return respostaFalha(alias, assunto, "Consulta DataJud interrompida.");
        } catch (Exception e) {
            log.warn("Falha na pesquisa agregada DataJud tribunal={} assunto={}: {}", alias, assunto, e.getMessage());
            return respostaFalha(alias, assunto, "DataJud indisponível no momento: " + e.getMessage());
        }
    }

    private DataJudPesquisaResponse respostaFalha(String tribunal, String assunto, String mensagem) {
        return new DataJudPesquisaResponse(UUID.randomUUID().toString(), "ORGAO_ASSUNTO", tribunal, assunto, false,
                mensagem, Instant.now(), null, List.of(), 0);
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
        String ultimaData = null;
        boolean baixa = false;
        if (movimentos.isArray()) for (JsonNode m : movimentos) {
            String nome = m.path("nome").asText("");
            String data = m.path("dataHora").asText("");
            if (!nome.isBlank()) {
                if (baixa(nome)) baixa = true;
                if (ultimaData == null || data.compareTo(ultimaData) > 0) {
                    ultimaData = data;
                    ultima = data + " — " + nome;
                }
            }
        }
        return new DataJudAmostra(source.path("numeroProcesso").asText(null),
                source.path("classe").path("codigo").asText(null), source.path("classe").path("nome").asText(null),
                assuntos, source.path("grau").asText(null), source.path("orgaoJulgador").path("nome").asText(null),
                source.path("dataAjuizamento").asText(null), ultima, baixa);
    }

    private boolean baixa(String nome) {
        String n = nome.toLowerCase(java.util.Locale.ROOT);
        return n.contains("baixa") || n.contains("baixado") || n.contains("arquivamento") || n.contains("arquivado")
                || n.contains("extinção") || n.contains("extincao") || n.contains("trânsito em julgado") || n.contains("transito em julgado");
    }

    private String normalizarTribunal(String tribunal) {
        if (tribunal == null) return null;
        String value = tribunal.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.startsWith("api_publica_")) value = value.substring("api_publica_".length());
        return value.matches("[a-z0-9-]{2,15}") ? value : null;
    }
}
