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
    private static final List<String> TRIBUNAIS = List.of("tjac","tjal","tjap","tjam","tjba","tjce","tjdft","tjes","tjgo","tjma","tjmt","tjms","tjmg","tjpa","tjpb","tjpr","tjpe","tjpi","tjrj","tjrn","tjrs","tjro","tjrr","tjsc","tjse","tjsp","tjto","trf1","trf2","trf3","trf4","trf5","trf6","trt1","trt2","trt3","trt4","trt5","trt6","trt7","trt8","trt9","trt10","trt11","trt12","trt13","trt14","trt15","trt16","trt17","trt18","trt19","trt20","trt21","trt22","trt23","trt24","stj","stf","tst","tse");
    private final AppProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final DataJudService dataJudService;
    public DataJudPesquisaService(AppProperties properties, ObjectMapper mapper, DataJudService dataJudService) {
        this.properties = properties; this.mapper = mapper; this.dataJudService = dataJudService;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(properties.dataJud().timeoutSecondsOuPadrao())).build();
    }
    public DataJudInfo infoPorCnj(String numeroProcesso) { return dataJudService.consultar(numeroProcesso); }
    public DataJudPesquisaResponse pesquisarCnj(String numeroProcesso) {
        DataJudInfo info = infoPorCnj(numeroProcesso);
        return new DataJudPesquisaResponse(UUID.randomUUID().toString(), "CNJ", info.tribunal(), null, true, info.mensagem(), info.consultadoEm(), info, List.of(), 0);
    }
    public DataJudPesquisaResponse pesquisarPorCpf(String cpf, String tribunal) {
        DataJudInfo info = infoPorCpf(cpf, tribunal);
        return new DataJudPesquisaResponse(UUID.randomUUID().toString(), "CPF", info.tribunal(), cpf, true, info.mensagem(), info.consultadoEm(), info, List.of(), info.encontrado() ? 1 : 0);
    }
    public DataJudInfo infoPorCpf(String cpf, String tribunal) {
        String digits = cpf == null ? "" : cpf.replaceAll("\\D", "");
        if (!digits.matches("\\d{11}")) throw new IllegalArgumentException("CPF inválido. Informe 11 dígitos.");
        if (!properties.dataJud().configurado()) return new DataJudInfo(DataJudStatus.NAO_CONFIGURADO, cpf, tribunal, null, false, null, null, null, null, null, "Integração DataJud não configurada.", Instant.now(), List.of());
        List<String> candidatos = tribunal == null || tribunal.isBlank() ? TRIBUNAIS : List.of(normalizarTribunal(tribunal));
        log.info("DataJud CPF: iniciando pesquisa para CPF mascarado ***{}. tribunais={}", digits.substring(8), candidatos.size());
        for (String alias : candidatos) {
            if (alias == null || alias.isBlank()) continue;
            try {
                DataJudInfo info = consultarDocumento(alias, digits);
                if (info.encontrado()) { log.info("DataJud CPF: processo encontrado no tribunal={} CNJ={}", alias, info.numeroProcesso()); return info; }
            } catch (Exception e) { log.debug("DataJud CPF: tribunal={} sem resultado/erro={}", alias, e.getMessage()); }
        }
        return new DataJudInfo(DataJudStatus.NAO_ENCONTRADO, cpf, tribunal, null, false, 0, null, null, null, null, "Nenhum processo público localizado para este CPF no(s) tribunal(is) consultado(s). A API pública respeita as regras de proteção de dados e pode não expor o documento da parte.", Instant.now(), List.of());
    }
    private DataJudInfo consultarDocumento(String alias, String cpf) throws Exception {
        String endpoint = properties.dataJud().baseUrlOuPadrao() + "/api_publica_" + alias + "/_search";
        ObjectNode query = mapper.createObjectNode();
        query.put("size", 1);
        ObjectNode match = mapper.createObjectNode();
        match.put("partes.numeroDocumentoPrincipal", cpf);
        query.set("query", mapper.createObjectNode().set("match", match));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(properties.dataJud().timeoutSecondsOuPadrao())).header("Authorization", "APIKey " + properties.dataJud().apiKey()).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(query.toString())).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) return new DataJudInfo(DataJudStatus.INDISPONIVEL, cpf, alias, endpoint, false, null, null, null, null, null, "DataJud respondeu HTTP " + response.statusCode() + ".", Instant.now(), List.of());
        JsonNode hits = mapper.readTree(response.body()).path("hits").path("hits");
        if (!hits.isArray() || hits.isEmpty()) return new DataJudInfo(DataJudStatus.NAO_ENCONTRADO, cpf, alias, endpoint, false, 0, null, null, null, null, "Nenhum processo encontrado neste tribunal.", Instant.now(), List.of());
        JsonNode source = hits.get(0).path("_source");
        String cnj = source.path("numeroProcesso").asText(null);
        List<DataJudMovimento> movimentos = new ArrayList<>();
        JsonNode ms = source.path("movimentos");
        if (ms.isArray()) for (JsonNode m : ms) movimentos.add(new DataJudMovimento(m.path("dataHora").asText(null), m.path("nome").asText(null), null));
        return new DataJudInfo(DataJudStatus.ENCONTRADO, cnj, alias, endpoint, true, movimentos.size(), movimentos.isEmpty() ? null : movimentos.get(movimentos.size()-1).dataHora() + " — " + movimentos.get(movimentos.size()-1).nome(), source.path("classe").path("nome").asText(null), source.path("orgaoJulgador").path("nome").asText(null), source.path("grau").asText(null), "Processo localizado pelo documento da parte no DataJud.", Instant.now(), movimentos);
    }
    public DataJudPesquisaResponse pesquisarPorTribunalAssunto(String tribunal, String assunto, int tamanho) {
        if (!properties.dataJud().configurado()) return respostaFalha(tribunal, assunto, "Integração DataJud não configurada.");
        String alias = normalizarTribunal(tribunal); if (alias == null) return respostaFalha(tribunal, assunto, "Informe o alias do tribunal.");
        if (assunto == null || assunto.isBlank()) return respostaFalha(alias, assunto, "Informe o código TPU ou nome do assunto.");
        int size = Math.max(1, Math.min(tamanho <= 0 ? 10 : tamanho, 25)); String endpoint = properties.dataJud().baseUrlOuPadrao() + "/api_publica_" + alias + "/_search";
        try {
            ObjectNode query = mapper.createObjectNode(); ObjectNode bool = mapper.createObjectNode(); var must = mapper.createArrayNode();
            ObjectNode assuntoMatch = mapper.createObjectNode(); if (assunto.matches("\\d+")) assuntoMatch.set("assuntos.codigo", mapper.getNodeFactory().numberNode(Long.parseLong(assunto))); else assuntoMatch.put("assuntos.nome", assunto);
            must.add(mapper.createObjectNode().set("match", assuntoMatch)); var should = mapper.createArrayNode();
            for (String termo : List.of("baixa","baixado","arquivamento","arquivado","extincao","extinto","transito em julgado")) should.add(mapper.createObjectNode().set("match", mapper.createObjectNode().put("movimentos.nome", termo)));
            ObjectNode encerramento = mapper.createObjectNode(); encerramento.set("should", should); encerramento.put("minimum_should_match", 1); must.add(encerramento); bool.set("must", must); query.set("query", mapper.createObjectNode().set("bool", bool)); query.put("size", size);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(properties.dataJud().timeoutSecondsOuPadrao())).header("Authorization", "APIKey " + properties.dataJud().apiKey()).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(query.toString())).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString()); if (response.statusCode() < 200 || response.statusCode() >= 300) return respostaFalha(alias, assunto, "DataJud respondeu HTTP " + response.statusCode() + ".");
            JsonNode hits = mapper.readTree(response.body()).path("hits").path("hits"); List<DataJudAmostra> amostra = new ArrayList<>(); if (hits.isArray()) for (JsonNode hit : hits) amostra.add(mapearAmostra(hit.path("_source")));
            return new DataJudPesquisaResponse(UUID.randomUUID().toString(), "ORGAO_ASSUNTO", alias, assunto, true, amostra.isEmpty() ? "Nenhum processo da amostra foi localizado." : "Amostra oficial localizada no DataJud.", Instant.now(), null, amostra, amostra.size());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); return respostaFalha(alias, assunto, "Consulta DataJud interrompida."); }
        catch (Exception e) { log.warn("Falha na pesquisa agregada DataJud tribunal={} assunto={}: {}", alias, assunto, e.getMessage()); return respostaFalha(alias, assunto, "DataJud indisponível no momento: " + e.getMessage()); }
    }
    private DataJudPesquisaResponse respostaFalha(String tribunal, String assunto, String mensagem) { return new DataJudPesquisaResponse(UUID.randomUUID().toString(), "ORGAO_ASSUNTO", tribunal, assunto, false, mensagem, Instant.now(), null, List.of(), 0); }
    private DataJudAmostra mapearAmostra(JsonNode source) { List<String> assuntos = new ArrayList<>(); JsonNode a = source.path("assuntos"); if (a.isArray()) for (JsonNode x : a) { String c=x.path("codigo").asText(""); String n=x.path("nome").asText(""); if (!c.isBlank()||!n.isBlank()) assuntos.add(c.isBlank()?n:c+" — "+n); } JsonNode ms=source.path("movimentos"); String ultima=null, ultimaData=null; boolean baixa=false; if(ms.isArray()) for(JsonNode m:ms){String n=m.path("nome").asText("");String d=m.path("dataHora").asText(""); if(!n.isBlank()){if(baixa(n))baixa=true;if(ultimaData==null||d.compareTo(ultimaData)>0){ultimaData=d;ultima=d+" — "+n;}}} return new DataJudAmostra(source.path("numeroProcesso").asText(null),source.path("classe").path("codigo").asText(null),source.path("classe").path("nome").asText(null),assuntos,source.path("grau").asText(null),source.path("orgaoJulgador").path("nome").asText(null),source.path("dataAjuizamento").asText(null),ultima,baixa); }
    private boolean baixa(String n){String x=n.toLowerCase(java.util.Locale.ROOT);return x.contains("baixa")||x.contains("baixado")||x.contains("arquivamento")||x.contains("arquivado")||x.contains("extinção")||x.contains("extincao")||x.contains("trânsito em julgado")||x.contains("transito em julgado");}
    private String normalizarTribunal(String tribunal){if(tribunal==null)return null;String v=tribunal.trim().toLowerCase(java.util.Locale.ROOT);if(v.startsWith("api_publica_"))v=v.substring(12);return v.matches("[a-z0-9-]{2,15}")?v:null;}
}
