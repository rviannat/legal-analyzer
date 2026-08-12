package com.rafaelvianna.legalanalyzer.analysis.research;

import com.rafaelvianna.legalanalyzer.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Implementação padrão do {@link LegalSourceProvider}: consulta apenas as URLs
 * declaradas em {@code legal-analyzer.legal-research.fontes} e só aceita
 * respostas cujo host esteja na allowlist
 * {@code legal-analyzer.legal-research.dominios-autorizados}.
 *
 * Enquanto {@code legal-analyzer.legal-research.enabled=false} (padrão), a
 * pesquisa fica desligada e o agente informa isso explicitamente, em vez de
 * permitir que o modelo invente citações.
 */
@Component
public class AllowlistLegalSourceProvider implements LegalSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(AllowlistLegalSourceProvider.class);

    private final AppProperties properties;
    private final HttpClient httpClient;

    public AllowlistLegalSourceProvider(AppProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public boolean habilitado() {
        AppProperties.LegalResearch cfg = properties.legalResearch();
        return cfg != null && cfg.enabled() && !cfg.fontesOuVazio().isEmpty();
    }

    @Override
    public List<String> fontesConfiguradas() {
        AppProperties.LegalResearch cfg = properties.legalResearch();
        if (cfg == null) {
            return List.of();
        }
        return cfg.fontesOuVazio().stream().map(AppProperties.FonteJuridica::nome).toList();
    }

    @Override
    public List<TrechoFonte> buscar(String consulta) {
        if (!habilitado() || !StringUtils.hasText(consulta)) {
            return List.of();
        }
        AppProperties.LegalResearch cfg = properties.legalResearch();
        String consultaCodificada = URLEncoder.encode(consulta, StandardCharsets.UTF_8);

        List<TrechoFonte> trechos = new ArrayList<>();
        for (AppProperties.FonteJuridica fonte : cfg.fontesOuVazio()) {
            if (trechos.size() >= cfg.maxFontesPorConsulta()) {
                break;
            }
            String url = fonte.urlTemplate().replace("{consulta}", consultaCodificada);
            if (!hostAutorizado(url, cfg.dominiosOuVazio())) {
                log.warn("Fonte '{}' ignorada: host de {} não está na allowlist de domínios autorizados.",
                        fonte.nome(), url);
                continue;
            }
            try {
                HttpRequest requisicao = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(cfg.timeoutSeconds()))
                        .header("User-Agent", "legal-analyzer/1.0 (pesquisa juridica)")
                        .GET()
                        .build();
                HttpResponse<String> resposta = httpClient.send(
                        requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (resposta.statusCode() / 100 != 2) {
                    log.warn("Fonte '{}' retornou status {} — ignorada.", fonte.nome(), resposta.statusCode());
                    continue;
                }
                // A URL final (após redirecionamentos) também precisa ser autorizada.
                String urlFinal = resposta.uri().toString();
                if (!hostAutorizado(urlFinal, cfg.dominiosOuVazio())) {
                    log.warn("Fonte '{}' redirecionou para host não autorizado ({}) — ignorada.",
                            fonte.nome(), urlFinal);
                    continue;
                }
                String texto = limparHtml(resposta.body(), cfg.maxCharsPorFonte());
                if (StringUtils.hasText(texto)) {
                    trechos.add(new TrechoFonte(fonte.nome(), urlFinal, texto, Instant.now()));
                }
            } catch (Exception e) {
                log.warn("Falha ao consultar a fonte '{}': {}", fonte.nome(), e.getMessage());
            }
        }
        return List.copyOf(trechos);
    }

    private boolean hostAutorizado(String url, List<String> dominiosAutorizados) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String hostNormalizado = host.toLowerCase(Locale.ROOT);
            return dominiosAutorizados.stream()
                    .map(d -> d.toLowerCase(Locale.ROOT).trim())
                    .filter(StringUtils::hasText)
                    .anyMatch(d -> hostNormalizado.equals(d) || hostNormalizado.endsWith("." + d));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String limparHtml(String corpo, int maxChars) {
        String texto = corpo
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("(?m)^ +", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return texto.length() <= maxChars ? texto : texto.substring(0, maxChars) + "\n[...trecho truncado...]";
    }
}
