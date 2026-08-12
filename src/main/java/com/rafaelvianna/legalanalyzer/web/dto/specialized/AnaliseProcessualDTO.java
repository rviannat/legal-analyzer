package com.rafaelvianna.legalanalyzer.web.dto.specialized;

import java.util.List;

/** Resultado do Process Agent: leitura estratégica do processo como um todo. */
public record AnaliseProcessualDTO(
        boolean processoIdentificado,
        String faseAtual,
        String riscoGeral,
        String teseAutor,
        String teseReu,
        List<String> pontosControvertidos,
        List<String> forcas,
        List<String> fragilidades,
        List<String> estrategiaSugerida,
        String prognostico,
        String observacoes
) {
    public static AnaliseProcessualDTO naoAplicavel(String motivo) {
        return new AnaliseProcessualDTO(false, "não identificado", "não identificado",
                "não identificado", "não identificado", List.of(), List.of(), List.of(), List.of(),
                "não identificado", motivo);
    }
}
