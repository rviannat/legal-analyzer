package com.rafaelvianna.legalanalyzer.web;

import com.rafaelvianna.legalanalyzer.async.AnaliseJobResponse;
import com.rafaelvianna.legalanalyzer.async.AnaliseJobService;
import com.rafaelvianna.legalanalyzer.datajud.DataJudAmostra;
import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import com.rafaelvianna.legalanalyzer.datajud.DataJudPesquisaEntity;
import com.rafaelvianna.legalanalyzer.datajud.DataJudPesquisaPersistenceService;
import com.rafaelvianna.legalanalyzer.datajud.DataJudPesquisaResponse;
import com.rafaelvianna.legalanalyzer.datajud.DataJudPesquisaService;
import com.rafaelvianna.legalanalyzer.persistence.ProcessoEntity;
import com.rafaelvianna.legalanalyzer.persistence.ProcessoPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/datajud")
public class DataJudPesquisaController {
    private static final Logger log = LoggerFactory.getLogger(DataJudPesquisaController.class);
    private final DataJudPesquisaService service;
    private final DataJudPesquisaPersistenceService persistence;
    private final AnaliseJobService analiseJobService;
    private final ProcessoPersistenceService processoPersistenceService;

    public DataJudPesquisaController(DataJudPesquisaService service,
                                     DataJudPesquisaPersistenceService persistence,
                                     AnaliseJobService analiseJobService,
                                     ProcessoPersistenceService processoPersistenceService) {
        this.service = service;
        this.persistence = persistence;
        this.analiseJobService = analiseJobService;
        this.processoPersistenceService = processoPersistenceService;
    }

    @PostMapping("/processos/cnj")
    public ResponseEntity<DataJudPesquisaResponse> pesquisarCnj(@RequestParam String numeroProcesso) {
        return ResponseEntity.ok(persistirCnj(numeroProcesso));
    }

    @GetMapping("/processos/cnj")
    public ResponseEntity<DataJudPesquisaResponse> pesquisarCnjGet(@RequestParam String numeroProcesso) {
        return ResponseEntity.ok(persistirCnj(numeroProcesso));
    }

    @PostMapping("/processos/cnj/processar")
    public ResponseEntity<AnaliseJobResponse> pesquisarCnjEProcessar(@RequestParam String numeroProcesso) {
        DataJudInfo info = service.infoPorCnj(numeroProcesso);
        DataJudPesquisaEntity pesquisa = persistence.salvar("CNJ", numeroProcesso, info.tribunal(), null, info, null);
        AnaliseJobResponse job = service.iniciarProcessamentoPorCnj(numeroProcesso);

        // O CNJ usado nesta pesquisa já foi confirmado pelo DataJud. Se o PDF
        // gerado pela consulta não contiver o número em texto extraível, o
        // processo ainda deve carregar o CNJ pesquisado. Isso também garante
        // que a tela Processos não fique com o campo CNJ vazio.
        preencherCnjValidado(job.id(), numeroProcesso);
        persistence.marcarProcessado(pesquisa.getId(), job);
        return ResponseEntity.accepted().body(job);
    }

    @PostMapping("/processos/cpf/processar")
    public ResponseEntity<AnaliseJobResponse> pesquisarCpfEProcessar(@RequestParam String cpf,
                                                                       @RequestParam(required = false) String tribunal) {
        DataJudInfo info = service.infoPorCpf(cpf, tribunal);
        DataJudPesquisaEntity pesquisa = persistence.salvar("CPF", cpf, tribunal, null, info, null);
        AnaliseJobResponse job = service.iniciarProcessamentoPorCpf(cpf, tribunal);
        persistence.marcarProcessado(pesquisa.getId(), job);
        return ResponseEntity.accepted().body(job);
    }

    @GetMapping("/processos/cpf")
    public ResponseEntity<DataJudPesquisaResponse> pesquisarCpf(@RequestParam String cpf,
                                                                  @RequestParam(required = false) String tribunal) {
        DataJudPesquisaResponse response = service.pesquisarPorCpf(cpf, tribunal);
        persistence.salvar("CPF", cpf, tribunal, null, response.processo(), null);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/processos/amostra")
    public ResponseEntity<DataJudPesquisaResponse> pesquisarAmostra(@RequestParam String codigoTribunal,
                                                                      @RequestParam String assunto,
                                                                      @RequestParam(defaultValue = "10") int tamanho) {
        DataJudPesquisaResponse response = service.pesquisarPorTribunalAssunto(codigoTribunal, assunto, tamanho);
        for (DataJudAmostra amostra : response.amostra()) {
            persistence.salvarAmostra(codigoTribunal, assunto, amostra);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/processos/amostra/processar")
    public ResponseEntity<AnaliseJobResponse> processarAmostra(@RequestParam String numeroProcesso) {
        DataJudInfo info = service.infoPorCnj(numeroProcesso);
        DataJudPesquisaEntity pesquisa = persistence.salvar("ORGAO_ASSUNTO", numeroProcesso, null, null, info, null);
        AnaliseJobResponse job = service.iniciarProcessamentoPorAmostra(numeroProcesso);
        preencherCnjValidado(job.id(), numeroProcesso);
        persistence.marcarProcessado(pesquisa.getId(), job);
        return ResponseEntity.accepted().body(job);
    }

    @GetMapping("/pesquisas")
    public ResponseEntity<List<DataJudPesquisaEntity>> historico() {
        return ResponseEntity.ok(persistence.listar());
    }

    private DataJudPesquisaResponse persistirCnj(String numeroProcesso) {
        DataJudPesquisaResponse response = service.pesquisarCnj(numeroProcesso);
        persistence.salvar("CNJ", numeroProcesso, response.tribunal(), null, response.processo(), null);
        return response;
    }

    private void preencherCnjValidado(String analiseId, String cnj) {
        if (cnj == null || !cnj.matches("\\d{7}-\\d{2}\\.\\d{4}\\.\\d\\.\\d{2}\\.\\d{4}")) {
            return;
        }
        var job = analiseJobService.buscar(analiseId);
        String atual = job.numeroProcesso();
        if (atual != null && atual.matches("\\d{7}-\\d{2}\\.\\d{4}\\.\\d\\.\\d{2}\\.\\d{4}")) {
            return;
        }
        job.numeroProcesso(cnj);
        processoPersistenceService.atualizar(
                analiseId,
                cnj,
                ProcessoEntity.Status.valueOf(job.status().name()),
                job.progresso(),
                job.etapa(),
                job.mensagem());
        log.info("[DATAJUD-CNJ:{}] CNJ FALLBACK | PDF não forneceu CNJ extraível | usando CNJ validado pela pesquisa | CNJ={}", analiseId, cnj);
    }
}
