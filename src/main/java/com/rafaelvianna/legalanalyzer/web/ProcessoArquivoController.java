package com.rafaelvianna.legalanalyzer.web;

import com.rafaelvianna.legalanalyzer.persistence.ProcessoPersistenceService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/processos")
public class ProcessoArquivoController {
    private final ProcessoPersistenceService persistence;
    public ProcessoArquivoController(ProcessoPersistenceService persistence){this.persistence=persistence;}
    @PostMapping(value="/analises/{id}/relatorio-pdf", consumes=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Void> salvarRelatorio(@PathVariable String id, @RequestBody byte[] pdf){
        if(pdf==null || pdf.length==0) return ResponseEntity.badRequest().build();
        persistence.salvarRelatorio(id,pdf); return ResponseEntity.noContent().build();
    }
    @GetMapping(value="/analises/{id}/relatorio-pdf", produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> baixarRelatorio(@PathVariable String id){
        byte[] pdf=persistence.relatorio(id);
        if(pdf==null || pdf.length==0) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=relatorio-"+id+".pdf").body(pdf);
    }
}
