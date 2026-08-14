package com.rafaelvianna.legalanalyzer.web;

import com.rafaelvianna.legalanalyzer.async.AnaliseEspecializadaJobResponse;
import com.rafaelvianna.legalanalyzer.async.AnaliseEspecializadaJobService;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.AnaliseEspecializadaResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    /** Consulta o job especializado mesmo que o frontend tenha sido fechado e reaberto. */
    @GetMapping("/especializada/{jobId}")
    public ResponseEntity<AnaliseEspecializadaJobResponse> statusEspecializada(@PathVariable String jobId) {
        return ResponseEntity.ok(especializadaService.consultar(jobId));
    }

    /** Baixa o PDF especializado persistido no PostgreSQL quando a análise estiver concluída. */
    @GetMapping("/especializada/{jobId}/relatorio")
    public ResponseEntity<ByteArrayResource> baixarRelatorioEspecializado(@PathVariable String jobId) {
        byte[] pdf = especializadaService.relatorio(jobId);
        String nome = "analise-especializada-" + jobId + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .contentLength(pdf.length)
                .body(new ByteArrayResource(pdf));
    }
}
