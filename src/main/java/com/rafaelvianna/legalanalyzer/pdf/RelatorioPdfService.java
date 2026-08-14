package com.rafaelvianna.legalanalyzer.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaelvianna.legalanalyzer.web.dto.AnaliseProcessoResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class RelatorioPdfService {
    private static final Logger log = LoggerFactory.getLogger(RelatorioPdfService.class);
    private static final float MARGIN = 45;
    private static final float FONT_SIZE = 9;
    private static final float LEADING = 12;
    private final ObjectMapper objectMapper;

    public RelatorioPdfService(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public byte[] gerar(String nomeArquivo, String cnj, AnaliseProcessoResponse resultado) {
        if (resultado == null) throw new IllegalArgumentException("Resultado da análise não disponível para gerar o PDF.");
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            List<String> linhas = new ArrayList<>();
            linhas.add("LEGAL ANALYZER - RELATÓRIO EXECUTIVO");
            linhas.add("Arquivo: " + nomeArquivo);
            linhas.add("CNJ: " + (cnj == null ? "não identificado" : cnj));
            linhas.add("");
            adicionarSecao(linhas, "METADADOS", resultado.metadata());
            adicionarSecao(linhas, "PARTES", resultado.partes());
            adicionarSecao(linhas, "CRONOLOGIA", resultado.cronologia());
            adicionarSecao(linhas, "PEDIDOS", resultado.pedidos());
            adicionarSecao(linhas, "DECISÕES", resultado.decisoes());
            adicionarSecao(linhas, "PRAZOS", resultado.prazos());
            adicionarSecao(linhas, "DOCUMENTOS IMPORTANTES", resultado.documentosImportantes());
            adicionarTexto(linhas, "RESUMO DO PROCESSO", resultado.resumoProcesso());
            adicionarSecao(linhas, "INCONSISTÊNCIAS", resultado.inconsistencias());
            adicionarSecao(linhas, "GRUPOS DE EVIDÊNCIA", resultado.gruposEvidencia());
            adicionarSecao(linhas, "PERGUNTAS DE INVESTIGAÇÃO", resultado.perguntasInvestigacao());
            adicionarSecao(linhas, "RELATÓRIO EXECUTIVO", resultado.relatorioExecutivo());

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            float y = page.getMediaBox().getHeight() - MARGIN;
            for (String linha : linhas) {
                if (y < MARGIN) { stream.endText(); stream.close(); page = new PDPage(PDRectangle.A4); document.addPage(page); stream = new PDPageContentStream(document, page); y = page.getMediaBox().getHeight() - MARGIN; }
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, linha.startsWith("LEGAL ANALYZER") ? 14 : FONT_SIZE);
                stream.newLineAtOffset(MARGIN, y);
                stream.showText(sanitizar(linha));
                stream.endText();
                y -= linha.startsWith("LEGAL ANALYZER") ? 20 : LEADING;
            }
            stream.close();
            document.save(output);
            byte[] pdf = output.toByteArray();
            log.info("PDF GERADO | arquivo={} | cnj={} | bytes={} | paginas={}", nomeArquivo, cnj, pdf.length, document.getNumberOfPages());
            return pdf;
        } catch (Exception e) {
            log.error("Falha ao gerar PDF | arquivo={} | cnj={} | {}", nomeArquivo, cnj, e.getMessage(), e);
            throw new IllegalStateException("Não foi possível gerar o relatório PDF.", e);
        }
    }

    private void adicionarSecao(List<String> linhas, String titulo, Object valor) {
        linhas.add(titulo);
        try { linhas.addAll(quebrar(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(valor))); }
        catch (Exception e) { linhas.add(String.valueOf(valor)); }
        linhas.add("");
    }

    private void adicionarTexto(List<String> linhas, String titulo, String valor) { linhas.add(titulo); linhas.addAll(quebrar(valor == null ? "" : valor)); linhas.add(""); }

    private List<String> quebrar(String texto) {
        List<String> result = new ArrayList<>();
        if (texto == null || texto.isBlank()) { result.add("(sem informação)"); return result; }
        for (String original : texto.replace("\r", "").split("\n", -1)) {
            String linha = original;
            while (linha.length() > 105) { result.add(linha.substring(0, 105)); linha = linha.substring(105); }
            result.add(linha);
        }
        return result;
    }

    private String sanitizar(String texto) { return new String(texto.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1).replaceAll("[^\\x20-\\x7E]", "?"); }
}
