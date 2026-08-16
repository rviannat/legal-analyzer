package com.rafaelvianna.legalanalyzer.datajud;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DataJudPesquisaRepository extends JpaRepository<DataJudPesquisaEntity, String> {
    List<DataJudPesquisaEntity> findTop100ByOrderByCriadoEmDesc();
}
