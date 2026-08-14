package com.rafaelvianna.legalanalyzer.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProcessoRepository extends JpaRepository<ProcessoEntity,String> {
    List<ProcessoEntity> findAllByOrderByAtualizadoEmDesc();
}
