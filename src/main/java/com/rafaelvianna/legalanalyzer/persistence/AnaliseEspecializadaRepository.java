package com.rafaelvianna.legalanalyzer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnaliseEspecializadaRepository extends JpaRepository<AnaliseEspecializadaEntity, String> {
    List<AnaliseEspecializadaEntity> findByAnaliseBaseIdOrderByAtualizadoEmDesc(String analiseBaseId);
    Optional<AnaliseEspecializadaEntity> findFirstByAnaliseBaseIdOrderByAtualizadoEmDesc(String analiseBaseId);
}
