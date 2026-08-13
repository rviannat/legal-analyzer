package com.rafaelvianna.legalanalyzer.pdf;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;

/** Segmentador jurídico: remove ruído repetitivo e cria blocos maiores. */
@Component
public class PdfTextChunker {
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern MANY_NEWLINES = Pattern.compile("\\n{3,}");
    private static final Pattern PAGE_MARKER = Pattern.compile("(?i)^(?:p[áa]gina\\s+)?\\d+(?:\\s+de\\s+\\d+)?$");

    public List<String> chunk(String texto, int tamanhoMaximoCaracteres, int sobreposicaoCaracteres) {
        if (texto == null || texto.isBlank()) return List.of();
        int tamanho = Math.max(tamanhoMaximoCaracteres, 7_000);
        int overlap = Math.min(Math.max(sobreposicaoCaracteres, 0), tamanho / 5);
        String limpo = limparEstrutura(texto);
        String[] partes = limpo.split("\\n\\s*\\n");
        List<String> paragrafos = new ArrayList<>();
        for (String parte : partes) {
            String p = MULTI_SPACE.matcher(parte.replace('\n', ' ')).replaceAll(" ").trim();
            if (p.length() >= 20) paragrafos.add(p);
        }
        if (paragrafos.isEmpty()) return List.of(limpo);

        List<String> resultado = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String p : paragrafos) {
            if (atual.length() > 0 && atual.length() + p.length() + 2 > tamanho) {
                resultado.add(atual.toString().trim());
                String cauda = overlap > 0 ? cauda(atual, overlap) : "";
                atual.setLength(0);
                if (!cauda.isBlank()) atual.append(cauda).append("\n\n");
            }
            atual.append(p).append("\n\n");
        }
        if (!atual.toString().isBlank()) resultado.add(atual.toString().trim());
        return resultado;
    }

    private String limparEstrutura(String texto) {
        String normalizado = texto.replace("\r\n", "\n").replace('\r', '\n');
        String[] linhas = normalizado.split("\\n");
        Map<String, Integer> frequencia = new HashMap<>();
        for (String linha : linhas) {
            String chave = normalizarLinha(linha);
            if (chave.length() >= 6 && chave.length() <= 180) frequencia.merge(chave, 1, Integer::sum);
        }
        StringBuilder out = new StringBuilder(normalizado.length());
        for (String linha : linhas) {
            String chave = normalizarLinha(linha);
            if (PAGE_MARKER.matcher(chave).matches()) continue;
            if (frequencia.getOrDefault(chave, 0) >= 4 && chave.length() <= 180) continue;
            out.append(linha.trim()).append('\n');
        }
        return MANY_NEWLINES.matcher(out.toString()).replaceAll("\n\n").trim();
    }

    private String normalizarLinha(String linha) {
        return MULTI_SPACE.matcher(linha.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    private String cauda(StringBuilder texto, int tamanho) {
        String s = texto.toString();
        if (s.length() <= tamanho) return s;
        int inicio = s.length() - tamanho;
        int quebra = s.indexOf(' ', inicio);
        return quebra >= 0 && quebra < s.length() - 1 ? s.substring(quebra + 1) : s.substring(inicio);
    }
}
