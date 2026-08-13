package com.rafaelvianna.legalanalyzer.web;

import com.rafaelvianna.legalanalyzer.async.AnaliseJobResponse;
import com.rafaelvianna.legalanalyzer.async.AnaliseJobService;
import com.rafaelvianna.legalanalyzer.datajud.DataJudPesquisaResponse;
import com.rafaelvianna.legalanalyzer.datajud.DataJudPesquisaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/datajud")
public class DataJudPesquisaController {
    private final DataJudPesquisaService service;
    private final AnaliseJobService analiseJobService;
    public DataJudPesquisaController(DataJudPesquisaService service, AnaliseJobService analiseJobService) { this.service = service; this.analiseJobService = analiseJobService; }

    @PostMapping("/processos/cnj")
    public ResponseEntity<DataJudPesquisaResponse> pesquisarCnj(@RequestParam String numeroProcesso) {
        return ResponseEntity.ok(service.pesquisarCnj(numeroProcesso));
    }

    @GetMapping("/processos/cnj")
    public ResponseEntity<DataJudPesquisaResponse> pesquisarCnjGet(@RequestParam String numeroProcesso) {
        return ResponseEntity.ok(service.pesquisarCnj(numeroProcesso));
    }

    @PostMapping("/processos/cnj/processar")
    public ResponseEntity<AnaliseJobResponse> pesquisarCnjEProcessar(@RequestParam String numeroProcesso) {
        return ResponseEntity.accepted().body(analiseJobService.iniciarPorDataJud(service.infoPorCnj(numeroProcesso), "consulta CNJ"));
    }

    @PostMapping("/processos/cpf/processar")
    public ResponseEntity<AnaliseJobResponse> pesquisarCpfEProcessar(@RequestParam String cpf, @RequestParam(required = false) String tribunal) {
        return ResponseEntity.accepted().body(analiseJobService.iniciarPorDataJud(service.infoPorCpf(cpf, tribunal), "consulta CPF"));
    }

    @GetMapping("/processos/cpf")
    public ResponseEntity<DataJudPesquisaResponse> pesquisarCpf(@RequestParam String cpf, @RequestParam(required = false) String tribunal) {
        return ResponseEntity.ok(service.pesquisarPorCpf(cpf, tribunal));
    }

    @GetMapping("/processos/amostra")
    public ResponseEntity<DataJudPesquisaResponse> pesquisarAmostra(@RequestParam String codigoTribunal, @RequestParam String assunto, @RequestParam(defaultValue = "10") int tamanho) {
        return ResponseEntity.ok(service.pesquisarPorTribunalAssunto(codigoTribunal, assunto, tamanho));
    }
}
