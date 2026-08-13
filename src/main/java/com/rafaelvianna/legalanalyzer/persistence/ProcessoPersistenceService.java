package com.rafaelvianna.legalanalyzer.persistence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ProcessoPersistenceService {
 private final ProcessoRepository repository;
 public ProcessoPersistenceService(ProcessoRepository repository){this.repository=repository;}
 @Transactional public ProcessoEntity criar(String id,String nome,byte[] pdf){return repository.save(new ProcessoEntity(id,nome,pdf));}
 @Transactional public void atualizar(String id,String cnj,ProcessoEntity.Status status,int progresso,String etapa,String mensagem){repository.findById(id).ifPresent(p->{p.atualizar(cnj,status,progresso,etapa,mensagem);repository.save(p);});}
 @Transactional public void salvarRelatorio(String id,byte[] pdf){repository.findById(id).ifPresent(p->{p.concluirRelatorio(pdf);repository.save(p);});}
 @Transactional(readOnly=true) public byte[] relatorio(String id){return repository.findById(id).map(ProcessoEntity::getRelatorioPdf).orElse(null);}
}
