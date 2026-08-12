package com.rafaelvianna.legalanalyzer.analysis.prompts;

/**
 * Templates de prompt para cada um dos agentes da pipeline de análise
 * jurídica. Centralizar os prompts aqui facilita ajuste fino e revisão
 * por um especialista do domínio (advogado) sem precisar mexer na lógica
 * de orquestração.
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    public static final String SYSTEM_JURIDICO = """
            Você é um assistente jurídico especializado em análise de processos e documentos judiciais
            brasileiros. Seu trabalho é ler trechos de processos e produzir SOMENTE respostas em formato
            JSON válido, estrito, sem markdown, sem comentários e sem nenhum texto fora do JSON.
            Nunca invente fatos que não estejam no texto fornecido; quando uma informação não estiver
            clara ou não puder ser localizada, utilize o valor "não identificado". Seja preciso, objetivo
            e mantenha a terminologia jurídica adequada ao direito brasileiro.
            """;

    /** Tarefas 2 a 7: partes, cronologia, pedidos, decisões, prazos e documentos. */
    public static String extracao(String trecho) {
        return """
                Analise o trecho de processo judicial abaixo e extraia as informações solicitadas.

                Responda SOMENTE com um JSON no formato exato abaixo (sem texto adicional, sem markdown):
                {
                  "partes": [ { "nome": "", "papel": "(autor|réu|terceiro interessado|testemunha|outro)", "qualificacao": "", "observacoes": "" } ],
                  "eventosCronologia": [ { "data": "AAAA-MM-DD (ou texto original se a data estiver incompleta)", "descricaoEvento": "", "fase": "" } ],
                  "pedidos": [ { "descricaoPedido": "", "parteRequerente": "", "fundamentoLegal": "", "status": "(pendente|deferido|indeferido|não identificado)" } ],
                  "decisoes": [ { "data": "", "tipoDecisao": "(sentença|despacho|acórdão|decisão interlocutória|outro)", "resumoDecisao": "", "autoridade": "", "efeitos": "" } ],
                  "prazos": [ { "data": "", "descricaoPrazo": "", "criticidade": "(alta|média|baixa)", "parteResponsavel": "" } ],
                  "documentosImportantes": [ { "nomeDocumento": "", "tipo": "", "dataDocumento": "", "relevancia": "" } ]
                }

                Regras:
                - Se não houver itens para uma categoria neste trecho, retorne uma lista vazia [].
                - Não repita eventos idênticos.
                - Datas devem ser normalizadas para AAAA-MM-DD sempre que possível.
                - Considere apenas o que está explicitamente no texto; não presuma informações.

                TRECHO DO PROCESSO:
                ---
                %s
                ---
                """.formatted(trecho);
    }

    /** Consolidação de múltiplos blocos de extração (usado quando o processo é dividido em vários trechos). */
    public static String consolidacao(String jsonParciais) {
        return """
                Você recebeu vários blocos JSON parciais extraídos de diferentes trechos do MESMO processo
                judicial, na ordem em que aparecem no documento. Consolide-os em um único JSON, removendo
                duplicatas (mesma parte, mesmo evento, mesmo pedido, mesma decisão, mesmo prazo ou mesmo
                documento mencionados em blocos diferentes), unificando informações complementares sobre a
                mesma parte/evento, e ORDENANDO cronologicamente "eventosCronologia", "decisoes" e "prazos"
                pela data (mais antiga primeiro; itens sem data ou com data não identificada vão ao final).

                Responda SOMENTE com um JSON no MESMO formato dos blocos parciais:
                {
                  "partes": [...],
                  "eventosCronologia": [...],
                  "pedidos": [...],
                  "decisoes": [...],
                  "prazos": [...],
                  "documentosImportantes": [...]
                }

                BLOCOS PARCIAIS (um JSON por trecho, na ordem do documento):
                [
                %s
                ]
                """.formatted(jsonParciais);
    }

    /** Tarefa 8: resumo do processo. */
    public static String resumo(String dadosConsolidadosJson, String amostraTexto) {
        return """
                Com base nos dados estruturados extraídos do processo e na amostra do texto original abaixo,
                escreva um RESUMO EXECUTIVO do processo (entre 3 e 6 parágrafos), em português, cobrindo:
                do que se trata o processo, quem são as partes e seus papéis, o que está sendo pedido,
                o andamento processual até o momento e o status atual.

                Responda SOMENTE com um JSON no formato:
                { "resumoProcesso": "texto do resumo aqui" }

                DADOS ESTRUTURADOS (JSON):
                %s

                AMOSTRA DO TEXTO ORIGINAL:
                ---
                %s
                ---
                """.formatted(dadosConsolidadosJson, amostraTexto);
    }

    /** Tarefa 9: inconsistências. */
    public static String inconsistencias(String dadosConsolidadosJson, String amostraTexto) {
        return """
                Analise os dados estruturados do processo e a amostra do texto original em busca de
                INCONSISTÊNCIAS, CONTRADIÇÕES ou LACUNAS relevantes — por exemplo: datas conflitantes,
                decisões que contradizem pedidos, partes referidas de formas diferentes, prazos que
                parecem incompatíveis com a cronologia, documentos citados mas não localizados no texto,
                ou afirmações contraditórias entre trechos.

                Responda SOMENTE com um JSON no formato:
                {
                  "inconsistencias": [
                    { "descricao": "", "elementosConflitantes": "", "gravidade": "(alta|média|baixa)", "recomendacao": "" }
                  ]
                }
                Se não houver inconsistências relevantes, retorne uma lista vazia.

                DADOS ESTRUTURADOS (JSON):
                %s

                AMOSTRA DO TEXTO ORIGINAL:
                ---
                %s
                ---
                """.formatted(dadosConsolidadosJson, amostraTexto);
    }

    /** Tarefa 10: organização de evidências. */
    public static String evidencias(String dadosConsolidadosJson) {
        return """
                Com base na lista de documentos importantes e demais dados estruturados do processo abaixo,
                organize as EVIDÊNCIAS em grupos lógicos por categoria (ex.: "Prova documental", "Perícias",
                "Comunicações", "Contratos", "Comprovantes financeiros", "Decisões judiciais", etc.),
                indicando a relevância probatória de cada grupo.

                Responda SOMENTE com um JSON no formato:
                {
                  "gruposEvidencia": [
                    { "categoria": "", "documentos": ["nome do documento 1", "nome do documento 2"], "relevanciaProbatoria": "(alta|média|baixa)", "observacoes": "" }
                  ]
                }
                Se não houver documentos suficientes para agrupar, retorne uma lista vazia.

                DADOS ESTRUTURADOS (JSON):
                %s
                """.formatted(dadosConsolidadosJson);
    }

    /** Tarefa 11: perguntas de investigação para o advogado. */
    public static String perguntasInvestigacao(String dadosConsolidadosJson, String inconsistenciasJson, String resumo) {
        return """
                Você é um assistente de um advogado que está revisando este processo. Com base no resumo,
                nos dados estruturados e nas inconsistências identificadas abaixo, gere uma lista de
                PERGUNTAS DE INVESTIGAÇÃO objetivas e acionáveis que o advogado deveria investigar ou
                esclarecer antes da próxima etapa do caso (ex.: documentos a solicitar, fatos a confirmar
                com o cliente, prazos a verificar, teses a aprofundar).

                Responda SOMENTE com um JSON no formato:
                { "perguntasInvestigacao": ["pergunta 1", "pergunta 2", "..."] }

                RESUMO DO PROCESSO:
                %s

                DADOS ESTRUTURADOS (JSON):
                %s

                INCONSISTÊNCIAS IDENTIFICADAS (JSON):
                %s
                """.formatted(resumo, dadosConsolidadosJson, inconsistenciasJson);
    }

    /** Tarefa 12: relatório executivo final. */
    public static String relatorioExecutivo(String nomeArquivo, String resumo, String dadosConsolidadosJson,
                                             String inconsistenciasJson, String perguntasJson) {
        return """
                Produza um RELATÓRIO EXECUTIVO final e completo sobre o processo "%s", destinado a um sócio
                ou advogado responsável que não teve tempo de ler o processo inteiro. Use linguagem jurídica
                clara, objetiva e executiva, priorizando o que é acionável.

                Responda SOMENTE com um JSON no formato:
                {
                  "relatorioExecutivo": {
                    "titulo": "",
                    "visaoGeral": "",
                    "pontosCriticos": ["", ""],
                    "recomendacoes": ["", ""],
                    "proximosPassos": ["", ""],
                    "conclusao": ""
                  }
                }

                RESUMO DO PROCESSO:
                %s

                DADOS ESTRUTURADOS (JSON):
                %s

                INCONSISTÊNCIAS (JSON):
                %s

                PERGUNTAS DE INVESTIGAÇÃO (JSON):
                %s
                """.formatted(nomeArquivo, resumo, dadosConsolidadosJson, inconsistenciasJson, perguntasJson);
    }
}
