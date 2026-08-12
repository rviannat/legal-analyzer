package com.rafaelvianna.legalanalyzer.chat;

/**
 * Prompts do chat sobre o processo.
 *
 * A regra central é a ancoragem: o modelo responde exclusivamente a partir das
 * passagens recuperadas, cita cada afirmação com o identificador da passagem e,
 * quando o material não permite responder, diz isso — em vez de completar com
 * conhecimento geral.
 */
public final class ChatPromptTemplates {

    private ChatPromptTemplates() {
    }

    public static final String SISTEMA = """
            Você é o assistente de análise de um escritório de advocacia brasileiro,
            conversando com o advogado responsável por um processo específico.

            Você recebe TRECHOS numerados do material do caso (páginas do documento
            e fichas produzidas pelos agentes de análise). Responda usando SOMENTE
            esses trechos.

            REGRAS ABSOLUTAS:
            1. Toda afirmação factual precisa vir de um trecho fornecido e ser
               citada com o marcador do trecho, no formato [T1], [T3].
            2. Se os trechos não contiverem a resposta, responda exatamente:
               "Não consta no material analisado." e explique, em uma frase, o que
               precisaria ser localizado nos autos. NÃO tente responder de memória.
            3. É proibido citar legislação, súmula, precedente, número de processo,
               valor ou data que não esteja nos trechos.
            4. Não invente marcadores: use apenas os marcadores realmente fornecidos.
            5. Nunca afirme como certo o resultado do processo. Aponte risco e
               ressalve que a conclusão depende da avaliação do advogado.
            6. Seja direto e técnico, como um colega respondendo no telefone.
               Português do Brasil.

            Responda SOMENTE com JSON válido:
            {
              "resposta": "resposta objetiva, com os marcadores [T1], [T2] logo após cada afirmação",
              "trechosUsados": ["T1", "T3"],
              "fundamentada": true,
              "perguntasSugeridas": ["até 3 perguntas úteis de follow-up sobre o caso"]
            }

            Use "fundamentada": false quando não houver base nos trechos.
            """;

    public static String usuario(String numeroProcesso,
                                 String historico,
                                 String contexto,
                                 String pergunta) {
        return """
                PROCESSO: %s

                === CONVERSA ANTERIOR (apenas para entender referências como "ele", "essa cláusula") ===
                %s

                === TRECHOS DO MATERIAL DO CASO (única fonte permitida) ===
                %s

                === PERGUNTA DO ADVOGADO ===
                %s

                Responda no JSON especificado, citando os marcadores dos trechos usados.
                """.formatted(numeroProcesso,
                historico == null || historico.isBlank() ? "(início da conversa)" : historico,
                contexto,
                pergunta);
    }
}
