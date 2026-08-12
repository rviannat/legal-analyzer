package com.rafaelvianna.legalanalyzer.analysis.research;

import com.rafaelvianna.legalanalyzer.config.AppProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que a pesquisa jurídica só recupera conteúdo de fontes autorizadas:
 * host fora da allowlist é recusado antes da requisição, e o conteúdo devolvido
 * é sempre texto realmente baixado, com a URL rastreável.
 */
class AllowlistLegalSourceProviderTest {

    private HttpServer servidor;
    private int porta;

    @BeforeEach
    void iniciarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/busca", troca -> {
            byte[] corpo = ("<html><head><style>body{color:red}</style></head><body>"
                    + "<script>var x = 1;</script>"
                    + "<p>CPC, art. 373, II &mdash; o &oacute;nus da prova incumbe ao r&eacute;u "
                    + "quanto &agrave; exist&ecirc;ncia de fato impeditivo.</p>"
                    + "</body></html>").getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            troca.sendResponseHeaders(200, corpo.length);
            try (OutputStream out = troca.getResponseBody()) {
                out.write(corpo);
            }
        });
        servidor.start();
        porta = servidor.getAddress().getPort();
    }

    @AfterEach
    void pararServidor() {
        servidor.stop(0);
    }

    @Test
    @DisplayName("desabilitado quando não há fontes configuradas")
    void desabilitadoSemFontes() {
        AllowlistLegalSourceProvider provider = provider(
                new AppProperties.LegalResearch(true, List.of("127.0.0.1"), List.of(), 8000, 3, 5));

        assertThat(provider.habilitado()).isFalse();
        assertThat(provider.buscar("ônus da prova")).isEmpty();
    }

    @Test
    @DisplayName("desabilitado quando a flag enabled é false")
    void desabilitadoPorFlag() {
        AllowlistLegalSourceProvider provider = provider(new AppProperties.LegalResearch(
                false, List.of("127.0.0.1"), List.of(fonteLocal()), 8000, 3, 5));

        assertThat(provider.habilitado()).isFalse();
        assertThat(provider.buscar("ônus da prova")).isEmpty();
    }

    @Test
    @DisplayName("recupera o trecho de fonte autorizada, com URL rastreável e HTML limpo")
    void recuperaFonteAutorizada() {
        AllowlistLegalSourceProvider provider = provider(new AppProperties.LegalResearch(
                true, List.of("127.0.0.1"), List.of(fonteLocal()), 8000, 3, 5));

        assertThat(provider.habilitado()).isTrue();
        List<TrechoFonte> trechos = provider.buscar("ônus da prova");

        assertThat(trechos).hasSize(1);
        TrechoFonte trecho = trechos.get(0);
        assertThat(trecho.fonte()).isEqualTo("Fonte local de teste");
        assertThat(trecho.url()).contains("127.0.0.1:" + porta);
        assertThat(trecho.conteudo()).contains("CPC, art. 373, II");
        assertThat(trecho.conteudo()).doesNotContain("<script", "var x = 1", "color:red");
        assertThat(trecho.consultadoEm()).isNotNull();
    }

    @Test
    @DisplayName("recusa fonte cujo host não está na allowlist")
    void recusaHostForaDaAllowlist() {
        AllowlistLegalSourceProvider provider = provider(new AppProperties.LegalResearch(
                true, List.of("planalto.gov.br"), List.of(fonteLocal()), 8000, 3, 5));

        assertThat(provider.buscar("ônus da prova")).isEmpty();
    }

    @Test
    @DisplayName("respeita o limite de fontes por consulta")
    void respeitaLimiteDeFontes() {
        AppProperties.FonteJuridica fonte = fonteLocal();
        AllowlistLegalSourceProvider provider = provider(new AppProperties.LegalResearch(
                true, List.of("127.0.0.1"), List.of(fonte, fonte, fonte), 8000, 1, 5));

        assertThat(provider.buscar("ônus da prova")).hasSize(1);
    }

    @Test
    @DisplayName("trunca o conteúdo no limite configurado")
    void truncaConteudo() {
        AllowlistLegalSourceProvider provider = provider(new AppProperties.LegalResearch(
                true, List.of("127.0.0.1"), List.of(fonteLocal()), 20, 3, 5));

        List<TrechoFonte> trechos = provider.buscar("ônus da prova");
        assertThat(trechos).hasSize(1);
        assertThat(trechos.get(0).conteudo()).contains("[...trecho truncado...]");
    }

    private AppProperties.FonteJuridica fonteLocal() {
        return new AppProperties.FonteJuridica(
                "Fonte local de teste",
                "http://127.0.0.1:" + porta + "/busca?q={consulta}");
    }

    private AllowlistLegalSourceProvider provider(AppProperties.LegalResearch cfg) {
        AppProperties properties = new AppProperties(
                new AppProperties.Ai("ollama", "", "llama3.1:8b", "http://localhost:11434/api/chat",
                        4096, 0.2, 30, 16384, true, "30m"),
                new AppProperties.Pdf(1024, 100, 10),
                new AppProperties.Especializada(16000, 5, 8000),
                cfg,
                AppProperties.Rag.padrao());
        return new AllowlistLegalSourceProvider(properties);
    }
}
