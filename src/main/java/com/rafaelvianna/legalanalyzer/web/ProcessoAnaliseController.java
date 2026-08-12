package com.rafaelvianna.legalanalyzer.web;

import com.rafaelvianna.legalanalyzer.async.AnaliseJobResponse;
import com.rafaelvianna.legalanalyzer.async.AnaliseJobService;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import com.rafaelvianna.legalanalyzer.exception.DocumentTooLargeException;
import com.rafaelvianna.legalanalyzer.exception.PdfProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/processos")
public class ProcessoAnaliseController {
    private final AnaliseJobService jobService;
    private final AppProperties properties;

    public ProcessoAnaliseController(AnaliseJobService jobService, AppProperties properties) {
        this.jobService = jobService;
        this.properties = properties;
    }

    @PostMapping(value = "/analisar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnaliseJobResponse> analisar(@RequestParam("arquivo") MultipartFile arquivo) {
        validarArquivo(arquivo);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobService.iniciar(arquivo));
    }

    @GetMapping("/analises/{id}")
    public ResponseEntity<AnaliseJobResponse> consultar(@PathVariable String id) {
        return ResponseEntity.ok(jobService.consultar(id));
    }

    @PostMapping(value = "/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    private void validarArquivo(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new PdfProcessingException("Nenhum arquivo foi enviado. Envie um PDF no campo \"arquivo\".");
        }
        String contentType = arquivo.getContentType();
        boolean pareceSerPdfPeloNome = arquivo.getOriginalFilename() != null
                && arquivo.getOriginalFilename().toLowerCase().endsWith(".pdf");
        if ((contentType == null || !contentType.equalsIgnoreCase(MediaType.APPLICATION_PDF_VALUE)) && !pareceSerPdfPeloNome) {
            throw new PdfProcessingException("O arquivo enviado precisa ser um PDF (application/pdf).");
        }
        long tamanhoMaximo = properties.pdf().maxFileSizeBytes();
        if (arquivo.getSize() > tamanhoMaximo) {
            throw new DocumentTooLargeException("Arquivo excede o tamanho máximo permitido de " + (tamanhoMaximo / 1_000_000) + "MB.");
        }
    }
}
