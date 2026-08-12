package com.rafaelvianna.legalanalyzer.pdf;

import com.rafaelvianna.legalanalyzer.exception.PdfProcessingException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayInputStream;

/**
 * Responsável pela tarefa 1 (leitura dos documentos): extrai o texto puro
 * de um PDF de processo/documento jurídico usando Apache PDFBox.
 */
@Service
public class PdfTextExtractionService {

    /**
     * Extrai e normaliza o texto de um PDF enviado via upload.
     *
     * @param arquivo PDF recebido no endpoint
     * @return texto extraído, normalizado
     */
    public String extractText(MultipartFile arquivo) {
        try {
            return extractText(arquivo.getBytes(), arquivo.getOriginalFilename());
        } catch (IOException e) {
            throw new PdfProcessingException("Falha ao ler o arquivo PDF: " + e.getMessage(), e);
        }
    }

    public String extractText(byte[] conteudo, String nomeArquivo) {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(conteudo))) {
            if (document.isEncrypted()) {
                throw new PdfProcessingException(
                        "O PDF está protegido/criptografado e não pode ser processado. " +
                        "Remova a proteção antes de enviar o arquivo.");
            }

            if (document.getNumberOfPages() == 0) {
                throw new PdfProcessingException("O PDF enviado não contém páginas.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String textoBruto = stripper.getText(document);

            String textoNormalizado = normalizar(textoBruto);

            if (textoNormalizado.isBlank()) {
                throw new PdfProcessingException(
                        "Não foi possível extrair texto do PDF. O arquivo pode conter apenas imagens " +
                        "escaneadas sem OCR — nesse caso, é necessário aplicar OCR antes da análise.");
            }

            return textoNormalizado;
        } catch (IOException e) {
            throw new PdfProcessingException("Falha ao ler o arquivo PDF: " + e.getMessage(), e);
        }
    }

    private String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        // Remove espaços em excesso mantendo quebras de parágrafo, e normaliza quebras de linha.
        String semQuebrasEstranhas = texto.replace("\r\n", "\n").replace("\r", "\n");
        String semEspacosRepetidos = semQuebrasEstranhas.replaceAll("[ \\t]{2,}", " ");
        String semLinhasVaziasExcessivas = semEspacosRepetidos.replaceAll("\\n{3,}", "\n\n");
        return semLinhasVaziasExcessivas.trim();
    }
}
