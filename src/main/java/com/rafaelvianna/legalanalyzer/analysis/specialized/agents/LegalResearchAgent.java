package com.rafaelvianna.legalanalyzer.analysis.specialized.agents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rafaelvianna.legalanalyzer.ai.AiClient;
import com.rafaelvianna.legalanalyzer.ai.AiJsonSupport;
import com.rafaelvianna.legalanalyzer.analysis.prompts.SpecializedPromptTemplates;
import com.rafaelvianna.legalanalyzer.analysis.research.LegalSourceProvider;
import com.rafaelvianna.legalanalyzer.analysis.research.TrechoFonte;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.PesquisaJuridicaDTO;
import com.rafaelvianna.legalanalyzer.web.dto.specialized.ReferenciaJuridicaDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agente 4 — Legal Research Agent: pesquisa legislação e jurisprudência
 * SOMENTE em fontes autorizadas e rastreáveis.
 *
 * Garantias de rastreabilidade implementadas aqui:
 * <ul>
 *   <li>o conteúdo vem do {@link LegalSourceProvider}, que só baixa de domínios da allowlist;</li>
 *   <li>o modelo recebe apenas esses trechos e é instruído a citar somente eles;</li>
 *   <li>toda referência devolvida é validada contra as URLs realmente baixadas — o que não casar é descartado;</li>
 *   <li>se a pesquisa estiver desabilitada ou nada for recuperado, o agente devolve
 *       {@code pesquisaRealizada = false} em vez de deixar o modelo citar de memória.</li>
 * </ul>
 */
@Component
public class LegalResearchAgent {

    private static final Logger log = LoggerFactory.getLogger(LegalResearchAgent.class);

    private final AiClient aiClient;
    private final AiJsonSupport jsonSupport;
    private final LegalSourceProvider sourceProvider;

    public LegalResearchAgent(AiClient aiClient, AiJsonSupport jsonSupport, LegalSourceProvider sourceProvider) {
        this.aiClient = aiClient;
        this.jsonSupport = jsonSupport;
        this.sourceProvider = sourceProvider;
    }

    public boolean disponivel() {
        return sourceProvider.habilitado();
    }

    /** Deriva uma consulta de pesquisa a partir do caso quando o advogado não informa uma. */
    public String derivarConsulta(String resumo, Object pedidos) {
        try {
            String resposta = aiClient.complete(
                    SpecializedPromptTemplates.SYSTEM_LEGAL_RESEARCH_AGENT,
                    SpecializedPromptTemplates.consultaDePesquisa(resumo, jsonSupport.toJson(pedidos)));
            ConsultaDerivada derivada = jsonSupport.parse(resposta, ConsultaDerivada.class);
            return derivada.consulta() == null ? "" : derivada.consulta().trim();
        } catch (RuntimeException e) {
            log.warn("Não foi possível derivar a consulta de pesquisa: {}", e.getMessage());
            return "";
        }
    }

    public PesquisaJuridicaDTO pesquisar(String consulta) {
        if (!sourceProvider.habilitado()) {
            return PesquisaJuridicaDTO.desabilitada(
                    "Pesquisa jurídica desabilitada: nenhuma fonte autorizada foi configurada "
                    + "(legal-analyzer.legal-research). Nenhuma legislação ou jurisprudência foi citada, "
                    + "para evitar referências não rastreáveis.");
        }
        if (consulta == null || consulta.isBlank()) {
            return PesquisaJuridicaDTO.desabilitada(
                    "Pesquisa jurídica não realizada: não foi possível definir uma consulta.");
        }

        List<TrechoFonte> trechos = sourceProvider.buscar(consulta);
        if (trechos.isEmpty()) {
            return new PesquisaJuridicaDTO(false, consulta, "", List.of(),
                    List.of("Nenhum conteúdo foi recuperado das fontes autorizadas para esta consulta."),
                    sourceProvider.fontesConfiguradas(),
                    "As fontes autorizadas não retornaram conteúdo utilizável. Nenhuma referência foi citada.");
        }

        String resposta = aiClient.complete(
                SpecializedPromptTemplates.SYSTEM_LEGAL_RESEARCH_AGENT,
                SpecializedPromptTemplates.pesquisaJuridica(consulta, formatarTrechos(trechos)));

        PesquisaBruta bruta = jsonSupport.parse(resposta, PesquisaBruta.class);
        List<ReferenciaJuridicaDTO> referenciasValidadas = validarReferencias(bruta.referencias(), trechos);

        List<String> lacunas = new ArrayList<>(bruta.lacunas() == null ? List.of() : bruta.lacunas());
        int descartadas = (bruta.referencias() == null ? 0 : bruta.referencias().size()) - referenciasValidadas.size();
        if (descartadas > 0) {
            lacunas.add(descartadas + " referência(s) citada(s) pelo modelo foram descartadas por não corresponderem "
                    + "a nenhuma URL das fontes autorizadas consultadas.");
        }

        return new PesquisaJuridicaDTO(
                true,
                consulta,
                bruta.sintese() == null ? "" : bruta.sintese(),
                referenciasValidadas,
                List.copyOf(lacunas),
                trechos.stream().map(TrechoFonte::fonte).distinct().toList(),
                "Referências limitadas às fontes autorizadas consultadas; confira cada URL antes de usar em peça.");
    }

    private List<ReferenciaJuridicaDTO> validarReferencias(List<ReferenciaBruta> referencias, List<TrechoFonte> trechos) {
        if (referencias == null || referencias.isEmpty()) {
            return List.of();
        }
        Map<String, TrechoFonte> porUrl = trechos.stream()
                .collect(Collectors.toMap(t -> normalizar(t.url()), t -> t, (a, b) -> a));

        List<ReferenciaJuridicaDTO> validadas = new ArrayList<>();
        for (ReferenciaBruta referencia : referencias) {
            if (referencia == null || referencia.url() == null) {
                continue;
            }
            TrechoFonte origem = porUrl.get(normalizar(referencia.url()));
            if (origem == null) {
                log.warn("Referência descartada por URL fora das fontes consultadas: {}", referencia.url());
                continue;
            }
            validadas.add(new ReferenciaJuridicaDTO(
                    vazioSeNulo(referencia.tipo()),
                    vazioSeNulo(referencia.identificacao()),
                    origem.fonte(),
                    origem.url(),
                    vazioSeNulo(referencia.trechoRelevante()),
                    origem.consultadoEm().toString(),
                    true));
        }
        return List.copyOf(validadas);
    }

    private String formatarTrechos(List<TrechoFonte> trechos) {
        StringBuilder sb = new StringBuilder();
        for (TrechoFonte trecho : trechos) {
            sb.append("### FONTE: ").append(trecho.fonte()).append('\n')
              .append("URL: ").append(trecho.url()).append('\n')
              .append("CONTEÚDO:\n").append(trecho.conteudo()).append("\n\n");
        }
        return sb.toString();
    }

    private String normalizar(String url) {
        return url == null ? "" : url.trim().replaceAll("/+$", "").toLowerCase();
    }

    private String vazioSeNulo(String valor) {
        return valor == null ? "" : valor;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConsultaDerivada(String consulta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PesquisaBruta(String sintese, List<ReferenciaBruta> referencias, List<String> lacunas) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReferenciaBruta(String tipo, String identificacao, String fonte, String url, String trechoRelevante) {
    }
}
