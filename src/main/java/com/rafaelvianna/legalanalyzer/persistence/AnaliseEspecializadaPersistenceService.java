package com.rafaelvianna.legalanalyzer.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AnaliseEspecializadaPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(AnaliseEspecializadaPersistenceService.class);
    private final AnaliseEspecializadaRepository repository;
    private final ObjectMapper objectMapper;

    public AnaliseEspecializadaPersistenceService(AnaliseEspecializadaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AnaliseEspecializadaEntity criar(String id, String analiseBaseId, String nomeArquivo) {
        AnaliseEspecializadaEntity entity = repository.save(new AnaliseEspecializadaEntity(id, analiseBaseId, nomeArquivo));
        log.info("[ESPECIALIZADA:{}] PERSISTENCIA | job criado | base={} | banco=PostgreSQL", id, analiseBaseId);
        return entity;
    }

    @Transactional
    public void atualizar(String id, String status, int progresso, String etapa, String mensagem, List<Map<String, Object>> logs) {
        repository.findById(id).ifPresentOrElse(entity -> {
            entity.atualizar(status, progresso, etapa, mensagem, serializar(logs));
            repository.save(entity);
            log.info("[ESPECIALIZADA:{}] PERSISTENCIA | status={} | progresso={}% | etapa={}", id, status, progresso, etapa);
        }, () -> log.warn("[ESPECIALIZADA:{}] PERSISTENCIA | job não encontrado", id));
    }

    @Transactional
    public void concluir(String id, AnaliseEspecializadaResponse resultado, byte[] relatorioPdf, List<Map<String, Object>> logs) {
        try {
            String resultadoJson = objectMapper.writeValueAsString(resultado);
            repository.findById(id).ifPresentOrElse(entity -> {
                entity.concluir(resultadoJson, relatorioPdf, serializar(logs));
                repository.saveAndFlush(entity);
                log.info("[ESPECIALIZADA:{}] PERSISTENCIA | CONCLUÍDA | relatório PDF salvo | bytes={}", id,
                        relatorioPdf == null ? 0 : relatorioPdf.length);
            }, () -> { throw new IllegalArgumentException("Análise especializada não encontrada: " + id); });
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível persistir a análise especializada " + id, e);
        }
    }

    @Transactional
    public void falhar(String id, int progresso, String etapa, String mensagem, List<Map<String, Object>> logs) {
        atualizar(id, "ERRO", progresso, etapa, mensagem, logs);
    }

    @Transactional(readOnly = true)
    public byte[] relatorio(String id) {
        return repository.findById(id).map(AnaliseEspecializadaEntity::getRelatorioPdf).orElse(null);
    }

    @Transactional(readOnly = true)
    public AnaliseEspecializadaEntity buscar(String id) {
        return repository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public AnaliseEspecializadaEntity ultimaDaBase(String analiseBaseId) {
        return repository.findFirstByAnaliseBaseIdOrderByAtualizadoEmDesc(analiseBaseId).orElse(null);
    }

    private String serializar(List<Map<String, Object>> logs) {
        try { return objectMapper.writeValueAsString(logs == null ? List.of() : logs); }
        catch (Exception e) { return "[]"; }
    }

    public List<Map<String, Object>> desserializarLogs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {}); }
        catch (Exception e) { log.warn("Não foi possível ler logs da análise especializada: {}", e.getMessage()); return List.of(); }
    }
}
