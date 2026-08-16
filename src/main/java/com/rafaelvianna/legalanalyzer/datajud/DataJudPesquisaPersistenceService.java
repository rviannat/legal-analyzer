package com.rafaelvianna.legalanalyzer.datajud;

import com.rafaelvianna.legalanalyzer.async.AnaliseJobResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class DataJudPesquisaPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(DataJudPesquisaPersistenceService.class);
    private final DataJudPesquisaRepository repository;

    public DataJudPesquisaPersistenceService(DataJudPesquisaRepository repository) { this.repository = repository; }

    @Transactional
    public DataJudPesquisaEntity salvar(String tipo, String parametro, String tribunal, String assunto,
                                        DataJudInfo info, String classeCodigo) {
        DataJudPesquisaEntity entity = repository.saveAndFlush(
                DataJudPesquisaEntity.from(tipo, parametro, tribunal, assunto, info, classeCodigo));
        log.info("[DATAJUD-PERSISTENCIA:{}] RESULTADO SALVO | tipo={} | parametro={} | CNJ={} | encontrado={} | banco=PostgreSQL",
                entity.getId(), tipo, parametro, entity.getNumeroCnj(), entity.isResultadoEncontrado());
        return entity;
    }

    @Transactional
    public DataJudPesquisaEntity salvarAmostra(String tribunal, String assunto, DataJudAmostra amostra) {
        DataJudPesquisaEntity entity = repository.saveAndFlush(
                DataJudPesquisaEntity.fromAmostra(tribunal, assunto, amostra));
        log.info("[DATAJUD-PERSISTENCIA:{}] AMOSTRA SALVA | tribunal={} | assunto={} | CNJ={} | encontrado=true | banco=PostgreSQL",
                entity.getId(), tribunal, assunto, entity.getNumeroCnj());
        return entity;
    }

    @Transactional
    public void marcarProcessado(String id, AnaliseJobResponse job) {
        repository.findById(id).ifPresent(entity -> {
            entity.marcarProcessado(job.id());
            repository.saveAndFlush(entity);
            log.info("[DATAJUD-PERSISTENCIA:{}] PROCESSAMENTO VINCULADO | analiseId={} | confirmado=true", id, job.id());
        });
    }

    @Transactional(readOnly = true)
    public List<DataJudPesquisaEntity> listar() { return repository.findTop100ByOrderByCriadoEmDesc(); }
}
