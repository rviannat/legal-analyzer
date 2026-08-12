package com.rafaelvianna.legalanalyzer.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Índice em memória das passagens de um caso, com busca híbrida.
 *
 * <ul>
 *   <li><b>Semântica</b>: similaridade de cosseno entre o embedding da pergunta
 *       e os das passagens (quando o modelo de embeddings está disponível).</li>
 *   <li><b>Léxica</b>: sobreposição de termos ponderada por IDF, sempre ativa.
 *       Ela é essencial no jurídico, onde a pergunta costuma trazer o termo
 *       exato ("cláusula 7.2", "art. 373", nome da parte, número do documento).</li>
 * </ul>
 *
 * O score final é a soma ponderada dos dois sinais, de modo que uma passagem
 * com o termo literal não seja perdida por um embedding mediano.
 */
public final class IndiceProcesso {

    private static final Pattern SEPARADOR = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** Palavras muito comuns em português/jurídico, sem valor discriminante. */
    private static final Set<String> STOPWORDS = Set.of(
            "a", "as", "o", "os", "um", "uma", "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas",
            "por", "para", "com", "sem", "que", "qual", "quais", "quando", "onde", "como", "se", "ao", "aos",
            "e", "ou", "mas", "foi", "ser", "sao", "são", "esta", "está", "este", "esse", "isso", "sobre",
            "existe", "houve", "ha", "há", "the", "of");

    private final List<Passagem> passagens;
    private final boolean semantico;
    /** Documentos (passagens) em que cada termo aparece — base do IDF. */
    private final Map<String, Integer> frequenciaDocumental;

    public IndiceProcesso(List<Passagem> passagens, boolean semantico) {
        this.passagens = List.copyOf(passagens);
        this.semantico = semantico;
        this.frequenciaDocumental = calcularFrequenciaDocumental(this.passagens);
    }

    public static IndiceProcesso vazio() {
        return new IndiceProcesso(List.of(), false);
    }

    public List<Passagem> passagens() {
        return passagens;
    }

    public int tamanho() {
        return passagens.size();
    }

    public boolean estaVazio() {
        return passagens.isEmpty();
    }

    public boolean semantico() {
        return semantico;
    }

    public long totalPaginasIndexadas() {
        return passagens.stream()
                .filter(p -> p.pagina() != null)
                .map(Passagem::pagina)
                .distinct()
                .count();
    }

    /**
     * Recupera as passagens mais relevantes para a consulta.
     *
     * @param consulta       pergunta em linguagem natural
     * @param vetorConsulta  embedding da pergunta (pode ser nulo → só léxico)
     * @param limite         quantidade máxima de passagens
     * @param scoreMinimo    corte de relevância (evita devolver ruído)
     */
    public List<PassagemRecuperada> buscar(String consulta, float[] vetorConsulta, int limite, double scoreMinimo) {
        if (passagens.isEmpty() || consulta == null || consulta.isBlank()) {
            return List.of();
        }
        List<String> termos = tokenizar(consulta);
        List<PassagemRecuperada> candidatos = new ArrayList<>();

        for (Passagem passagem : passagens) {
            double scoreLexico = scoreLexico(termos, passagem);
            double scoreSemantico = (vetorConsulta != null && passagem.vetor() != null)
                    ? Math.max(0, cosseno(vetorConsulta, passagem.vetor()))
                    : 0.0;

            double score;
            String estrategia;
            if (scoreSemantico > 0 && scoreLexico > 0) {
                score = 0.6 * scoreSemantico + 0.4 * scoreLexico;
                estrategia = "hibrida";
            } else if (scoreSemantico > 0) {
                score = 0.6 * scoreSemantico;
                estrategia = "semantica";
            } else {
                score = 0.4 * scoreLexico;
                estrategia = "lexica";
            }

            // Fichas da análise já são fatos consolidados: pequeno reforço para
            // que o chat prefira responder a partir do que os agentes apuraram.
            if (passagem.tipo() == Passagem.Tipo.FICHA_ANALISE) {
                score *= 1.1;
            }

            if (score >= scoreMinimo) {
                candidatos.add(new PassagemRecuperada(passagem, score, estrategia));
            }
        }

        candidatos.sort(Comparator.comparingDouble(PassagemRecuperada::score).reversed());
        return candidatos.size() <= limite ? List.copyOf(candidatos) : List.copyOf(candidatos.subList(0, limite));
    }

    /**
     * Procura a página em que um termo literal aparece (ex.: o nome de um
     * documento citado na petição). É a base da coluna "página" da matriz de
     * evidências do briefing: um ponteiro conferível, não uma suposição.
     */
    public Optional<Passagem> localizarTermo(String termo) {
        if (termo == null || termo.isBlank()) {
            return Optional.empty();
        }
        String normalizado = normalizar(termo);
        if (normalizado.length() < 4) {
            return Optional.empty();
        }
        return passagens.stream()
                .filter(p -> p.pagina() != null)
                .filter(p -> normalizar(p.texto()).contains(normalizado))
                .min(Comparator.comparingInt(Passagem::pagina));
    }

    // --- pontuação ---------------------------------------------------------

    private double scoreLexico(List<String> termosConsulta, Passagem passagem) {
        if (termosConsulta.isEmpty()) {
            return 0;
        }
        List<String> termosPassagem = tokenizar(passagem.texto());
        if (termosPassagem.isEmpty()) {
            return 0;
        }
        Set<String> presentes = new HashSet<>(termosPassagem);

        double soma = 0;
        double pesoTotal = 0;
        int total = Math.max(1, passagens.size());
        for (String termo : termosConsulta) {
            int df = frequenciaDocumental.getOrDefault(termo, 0);
            double idf = Math.log(1.0 + (double) total / (1 + df));
            pesoTotal += idf;
            if (presentes.contains(termo)) {
                soma += idf;
            }
        }
        return pesoTotal == 0 ? 0 : soma / pesoTotal;
    }

    static double cosseno(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }
        double produto = 0;
        double normaA = 0;
        double normaB = 0;
        for (int i = 0; i < a.length; i++) {
            produto += a[i] * b[i];
            normaA += a[i] * a[i];
            normaB += b[i] * b[i];
        }
        if (normaA == 0 || normaB == 0) {
            return 0;
        }
        return produto / (Math.sqrt(normaA) * Math.sqrt(normaB));
    }

    private Map<String, Integer> calcularFrequenciaDocumental(List<Passagem> passagens) {
        Map<String, Integer> df = new HashMap<>();
        for (Passagem passagem : passagens) {
            for (String termo : new HashSet<>(tokenizar(passagem.texto()))) {
                df.merge(termo, 1, Integer::sum);
            }
        }
        return df;
    }

    private List<String> tokenizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        List<String> termos = new ArrayList<>();
        for (String bruto : SEPARADOR.split(normalizar(texto))) {
            if (bruto.length() >= 3 && !STOPWORDS.contains(bruto)) {
                termos.add(bruto);
            }
        }
        return termos;
    }

    /** Minúsculas sem acentos, para casar "cláusula" com "clausula". */
    public static String normalizar(String texto) {
        String semAcento = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return semAcento.toLowerCase(Locale.ROOT);
    }
}
