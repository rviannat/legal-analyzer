package com.rafaelvianna.legalanalyzer.chat;

import com.rafaelvianna.legalanalyzer.rag.PassagemRecuperada;
import com.rafaelvianna.legalanalyzer.web.dto.rag.CitacaoDTO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Confronta a resposta do modelo com as passagens que foram efetivamente
 * recuperadas do caso.
 *
 * É a trava que impede a citação falsa: marcadores que não existem no contexto
 * são apagados do texto e, se nenhum marcador válido sobrar, a resposta é
 * considerada não fundamentada — o sistema prefere admitir a lacuna a entregar
 * ao advogado uma referência que não existe.
 */
public final class ValidadorAncoragem {

    private static final Pattern MARCADOR = Pattern.compile("\\[T(\\d+)\\]");

    /**
     * @param texto              resposta já limpa dos marcadores inválidos
     * @param citacoes           fontes verificadas, com página quando aplicável
     * @param fundamentada       se a resposta pode ser entregue como resposta
     * @param marcadoresRemovidos marcadores inventados pelo modelo e descartados
     */
    public record Resultado(String texto,
                            List<CitacaoDTO> citacoes,
                            boolean fundamentada,
                            List<String> marcadoresRemovidos) {
    }

    private ValidadorAncoragem() {
    }

    public static Resultado validar(RespostaChatIa bruta, Map<String, PassagemRecuperada> porMarcador) {
        String texto = bruta == null || bruta.resposta() == null ? "" : bruta.resposta().trim();
        boolean modeloAdmitiuLacuna = bruta != null && Boolean.FALSE.equals(bruta.fundamentada());

        Set<String> validos = new LinkedHashSet<>();
        Set<String> invalidos = new LinkedHashSet<>();

        Matcher matcher = MARCADOR.matcher(texto);
        while (matcher.find()) {
            String marcador = "T" + matcher.group(1);
            if (porMarcador.containsKey(marcador)) {
                validos.add(marcador);
            } else {
                invalidos.add(marcador);
            }
        }
        // Marcadores declarados em "trechosUsados" também valem, se existirem.
        if (bruta != null && bruta.trechosUsados() != null) {
            for (String declarado : bruta.trechosUsados()) {
                if (declarado != null && porMarcador.containsKey(declarado.trim())) {
                    validos.add(declarado.trim());
                }
            }
        }

        for (String invalido : invalidos) {
            texto = texto.replace("[" + invalido + "]", "");
        }
        texto = texto.replaceAll(" {2,}", " ").trim();

        boolean fundamentada = !validos.isEmpty() && !modeloAdmitiuLacuna && !texto.isBlank();

        List<CitacaoDTO> citacoes = new ArrayList<>();
        if (fundamentada) {
            for (String marcador : validos) {
                var passagem = porMarcador.get(marcador).passagem();
                citacoes.add(new CitacaoDTO(
                        passagem.citacao(),
                        passagem.pagina(),
                        passagem.tipo().name(),
                        recortar(passagem.texto())));
            }
        }

        return new Resultado(texto, List.copyOf(citacoes), fundamentada, List.copyOf(invalidos));
    }

    private static String recortar(String texto) {
        String limpo = texto.replace('\n', ' ').trim();
        return limpo.length() <= 320 ? limpo : limpo.substring(0, 320) + "...";
    }
}
