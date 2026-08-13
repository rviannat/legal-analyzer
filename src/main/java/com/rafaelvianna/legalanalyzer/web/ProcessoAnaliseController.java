package com.rafaelvianna.legalanalyzer.web;

import com.rafaelvianna.legalanalyzer.async.AnaliseEspecializadaJobResponse;
import com.rafaelvianna.legalanalyzer.async.AnaliseEspecializadaJobService;
import com.rafaelvianna.legalanalyzer.async.AnaliseJob;
import com.rafaelvianna.legalanalyzer.async.AnaliseJobResponse;
import com.rafaelvianna.legalanalyzer.async.AnaliseJobService;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import com.rafaelvianna.legalanalyzer.datajud.DataJudAuditoria;
import com.rafaelvianna.legalanalyzer.datajud.DataJudInfo;
import com.rafaelvianna.legalanalyzer.exception.DocumentTooLargeException;
import com.rafaelvianna.legalanalyzer.exception.PdfProcessingException;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/processos")
public class ProcessoAnaliseController {
    private final AnaliseJobService jobService;
    private final AnaliseEspecializadaJobService especializadaService;
    private final AppProperties properties;

    public ProcessoAnaliseController(AnaliseJobService jobService, AnaliseEspecializadaJobService especializadaService, AppProperties properties) {
        this.jobService = jobService; this.especializadaService = especializadaService; this.properties = properties;
    }

    @PostMapping(value = "/analisar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnaliseJobResponse> analisar(@RequestParam("arquivo") MultipartFile arquivo) {
        validarArquivo(arquivo); return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobService.iniciar(arquivo));
    }

    @GetMapping("/analises/{id}")
    public ResponseEntity<AnaliseJobResponse> consultar(@PathVariable String id) {
        AnaliseJob job = jobService.buscar(id); return ResponseEntity.ok(AnaliseJobResponse.status(job, especializadaService.opcao(job)));
    }

    @GetMapping("/analises/{id}/datajud")
    public ResponseEntity<DataJudInfo> consultarDataJud(@PathVariable String id) {
        return ResponseEntity.ok(jobService.buscar(id).dataJud());
    }

    @GetMapping("/analises/{id}/datajud/auditoria")
    public ResponseEntity<DataJudAuditoria> consultarAuditoriaDataJud(@PathVariable String id) {
        AnaliseJob job = jobService.buscar(id);
        return ResponseEntity.ok(DataJudAuditoria.de(job.dataJud(), job.resultado() == null ? java.util.List.of() : job.resultado().partes()));
    }

    @PostMapping(value = "/analises/{id}/especializada", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnaliseEspecializadaJobResponse> analisarEspecializada(@PathVariable String id, @RequestBody(required = false) AnaliseEspecializadaRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(especializadaService.iniciar(id, request));
    }

    @GetMapping("/analises-especializadas/{id}")
    public ResponseEntity<AnaliseEspecializadaJobResponse> consultarEspecializada(@PathVariable String id) { return ResponseEntity.ok(especializadaService.consultar(id)); }

    @PostMapping(value = "/health")
    public ResponseEntity<String> health() { return ResponseEntity.ok("OK"); }

    private void validarArquivo(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) throw new PdfProcessingException("Nenhum arquivo foi enviado. Envie um PDF no campo \"arquivo\".");
        String contentType = arquivo.getContentType();
        boolean pareceSerPdfPeloNome = arquivo.getOriginalFilename() != null && arquivo.getOriginalFilename().toLowerCase().endsWith(".pdf");
        if ((contentType == null || !contentType.equalsIgnoreCase(MediaType.APPLICATION_PDF_VALUE)) && !pareceSerPdfPeloNome) throw new PdfProcessingException("O arquivo enviado precisa ser um PDF (application/pdf).");
        long tamanhoMaximo = properties.pdf().maxFileSizeBytes();
        if (arquivo.getSize() > tamanhoMaximo) throw new DocumentTooLargeException("Arquivo excede o tamanho máximo permitido de " + (tamanhoMaximo / 1_000_000) + "MB.");
    }
}
