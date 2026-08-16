package com.rafaelvianna.legalanalyzer.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ProcessoPersistenceService {
    private static final Logger log=LoggerFactory.getLogger(ProcessoPersistenceService.class);
    private final ProcessoRepository repository;
    public ProcessoPersistenceService(ProcessoRepository repository){this.repository=repository;}
    @Transactional public ProcessoEntity criar(String id,String nome,byte[] pdf){ProcessoEntity e=repository.save(new ProcessoEntity(id,nome,pdf));log.info("[PERSISTENCIA:{}] PROCESSO CRIADO | arquivo={} | pdfOriginalBytes={} | banco=PostgreSQL",id,nome,pdf==null?0:pdf.length);return e;}
    @Transactional public void atualizar(String id,String cnj,ProcessoEntity.Status status,int progresso,String etapa,String mensagem){repository.findById(id).ifPresentOrElse(p->{p.atualizar(cnj,status,progresso,etapa,mensagem);repository.save(p);log.info("[PERSISTENCIA:{}] STATUS SALVO | status={} | progresso={}% | etapa={} | cnj={}",id,status,progresso,etapa,cnj);},()->log.warn("[PERSISTENCIA:{}] STATUS NÃO SALVO | processo não encontrado no PostgreSQL",id));}
    @Transactional public void salvarRelatorio(String id,byte[] pdf){if(pdf==null||pdf.length==0)throw new IllegalArgumentException("Relatório PDF vazio para o processo "+id);repository.findById(id).ifPresentOrElse(p->{p.concluirRelatorio(pdf);repository.saveAndFlush(p);log.info("[PERSISTENCIA:{}] PDF FINAL SALVO | banco=PostgreSQL | bytes={} | confirmado=true",id,pdf.length);},()->{throw new IllegalArgumentException("Processo não encontrado: "+id);});}
    @Transactional(readOnly=true) public byte[] relatorio(String id){byte[] pdf=repository.findById(id).map(ProcessoEntity::getRelatorioPdf).orElse(null);log.info("[PERSISTENCIA:{}] PDF EXPORTAÇÃO | encontrado={} | bytes={}",id,pdf!=null,pdf==null?0:pdf.length);return pdf;}
    @Transactional(readOnly=true) public byte[] arquivoOriginal(String id){byte[] pdf=repository.findById(id).map(ProcessoEntity::getArquivoPdf).orElse(null);log.info("[PERSISTENCIA:{}] PDF ORIGINAL | encontrado={} | bytes={}",id,pdf!=null,pdf==null?0:pdf.length);return pdf;}
    @Transactional(readOnly=true) public ProcessoEntity buscar(String id){return repository.findById(id).orElse(null);}
    @Transactional(readOnly=true) public List<ProcessoEntity> listar(){return repository.findAllByOrderByAtualizadoEmDesc();}
}
