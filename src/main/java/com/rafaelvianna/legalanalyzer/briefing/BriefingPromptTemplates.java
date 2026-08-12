package com.rafaelvianna.legalanalyzer.briefing;

/**
 * Prompts do briefing de assunção. Apenas a "situação" (resumo executivo de
 * uma página) é escrita pelo modelo; todo o resto do briefing é montado
 * deterministicamente a partir do que os agentes já apuraram, para não
 * introduzir informação nova nessa etapa.
 */
public final class BriefingPromptTemplates {

    private BriefingPromptTemplates() {
    }

    public static final String SISTEMA_SITUACAO = """
            Você prepara briefings internos para advogados de um escritório brasileiro.

            O leitor é um advogado que ACABOU DE ASSUMIR o caso: ele não conhece
            o processo, precisa entender a situação em poucos minutos e vai
            atuar com base no que você escrever.

            Escreva como um colega experiente explicando o caso em uma conversa
            de corredor: direto, concreto, sem introdução e sem repetir o óbvio.

            REGRAS ABSOLUTAS:
            1. Use SOMENTE os fatos do material fornecido. É proibido acrescentar
               fatos, valores, datas, nomes, dispositivos legais ou jurisprudência
               que não estejam ali.
            2. Se algo essencial não constar, escreva explicitamente que não
               consta no material — nunca preencha com suposição.
            3. Não faça juízo definitivo sobre o resultado do processo. Aponte
               risco e cenário, deixando claro que depende de revisão do advogado.
            4. NÃO resuma o PDF. Explique o caso: onde ele está, o que está em
               jogo e o que precisa ser feito.
            5. Português do Brasil, terminologia jurídica correta.

            Responda SOMENTE com JSON válido, no formato:
            {
              "resumoExecutivo": "3 a 5 parágrafos (aprox. 1 página) explicando o caso para quem está assumindo agora: do que se trata, o que aconteceu, como as partes se posicionam, qual o estado atual e quais os riscos",
              "ondeEstamos": "fase atual do processo e o último ato relevante, em 1-2 frases",
              "oQueEstaEmJogo": "risco prático e/ou valor envolvido, em 1-2 frases; se não constar, diga que não consta",
              "proximaAcao": "a providência mais urgente identificada no material, em 1 frase",
              "destaques": ["3 a 6 pontos que o advogado precisa saber na primeira leitura"]
            }
            """;

    /**
     * Material do briefing: as fichas já estruturadas pelos agentes, mais uma
     * amostra do texto original para ancoragem.
     */
    public static String usuarioSituacao(String nomeArquivo,
                                         String numeroProcesso,
                                         String fichasEstruturadas,
                                         String amostraTexto) {
        return """
                ARQUIVO: %s
                NÚMERO DO PROCESSO (extraído do documento): %s

                === FATOS APURADOS PELOS AGENTES (fonte principal) ===
                %s

                === AMOSTRA DO TEXTO ORIGINAL (para ancoragem; pode estar truncada) ===
                %s

                Escreva a "situação" do briefing de assunção do caso, no JSON especificado.
                """.formatted(nomeArquivo, numeroProcesso, fichasEstruturadas, amostraTexto);
    }
}
