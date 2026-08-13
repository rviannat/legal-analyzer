package com.rafaelvianna.legalanalyzer.web;

import com.rafaelvianna.legalanalyzer.datajud.DataJudPesquisaResponse;
import com.rafaelvianna.legalanalyzer.datajud.DataJudPesquisaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/datajud")
public class DataJudPesquisaController {
    private final DataJudPesquisaService service;
    public DataJudPesquisaController(DataJudPesquisaService service) { this.service = service; }

    @PostMapping("/processos/cnj")
    public ResponseEntity<DataJudPesquisaResponse> pesquisarCnj(@RequestParam String numeroProcesso) {
        return ResponseEntity.ok(service.pesquisarCnj(numeroProcesso));
    }

    @GetMapping("/processos/cnj")
    public ResponseEntity<DataJudPesquisaResponse> pesquisarCnjGet(@RequestParam String numeroProcesso) {
        return ResponseEntity.ok(service.pesquisarCnj(numeroProcesso));
    }

    @GetMapping("/processos/amostra")
    public ResponseEntity<DataJudPesquisaResponse> pesquisarAmostra(
            @RequestParam String codigoTribunal,
            @RequestParam String assunto,
            @RequestParam(defaultValue = "10") int tamanho) {
        return ResponseEntity.ok(service.pesquisarPorTribunalAssunto(codigoTribunal, assunto, tamanho));
    }
}
