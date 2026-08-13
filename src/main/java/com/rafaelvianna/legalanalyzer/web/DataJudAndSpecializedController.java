package com.rafaelvianna.legalanalyzer.web;

import com.rafaelvianna.legalanalyzer.async.AnaliseEspecializadaJobService;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/processos/analises")
public class DataJudAndSpecializedController {
    private final AnaliseEspecializadaJobService especializadaService;
    public DataJudAndSpecializedController(AnaliseEspecializadaJobService especializadaService) { this.especializadaService = especializadaService; }

    /** Retorna o último resultado especializado concluído para a análise base. */
    @GetMapping("/{id}/especializada/ultima")
    public ResponseEntity<AnaliseEspecializadaResponse> ultimaEspecializada(@PathVariable String id) {
        AnaliseEspecializadaResponse result = especializadaService.ultimoResultadoDaBase(id);
        return result == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }
}
