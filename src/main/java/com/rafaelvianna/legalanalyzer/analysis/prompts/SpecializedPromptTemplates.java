package com.rafaelvianna.legalanalyzer.analysis.prompts;

import com.rafaelvianna.legalanalyzer.web.dto.specialized.TipoRascunho;

/**
 * Prompts dos agentes especializados (análise especializada opcional,
 * disparada após a análise base do processo):
 *
 * <ol>
 *   <li>Process Agent — leitura estratégica do processo completo</li>
 *   <li>Contract Agent — cláusulas de risco, obrigações, multas, prazos, condições, inconsistências</li>
 *   <li>Document Agent — classificação automática dos documentos</li>
 *   <li>Legal Research Agent — legislação/jurisprudência só de fontes autorizadas e rastreáveis</li>
 *   <li>Deadline Agent — datas e eventos importantes</li>
 *   <li>Evidence Agent — alegações x documentos que podem sustentá-las</li>
 *   <li>Drafting Agent — rascunhos (parecer, manifestação, relatório, petição, e-mail ao cliente)</li>
 *   <li>Senior Lawyer Agent — orquestrador, produz o resultado final</li>
 * </ol>
 *
 * Centralizar os prompts aqui permite que um advogado revise e ajuste o
 * comportamento dos agentes sem tocar na lógica de orquestração.
 */
public final class SpecializedPromptTemplates {

    private SpecializedPromptTemplates() {
    }

    /** Regras comuns a todos os agentes especializados. */
    private static final String REGRAS_COMUNS = """
            Regras invioláveis:
            - Responda SOMENTE com JSON válido e estrito, sem markdown, sem comentários, sem texto fora do JSON.
            - Baseie-se exclusivamente no material fornecido. É proibido inventar fatos, números, cláusulas,
              nomes, datas, leis, súmulas ou julgados.
            - Quando a informação não estiver no material, use exatamente "não identificado".
            - Não afirme conclusões jurídicas como certezas: indique o grau de confiança quando solicitado.
            - Listas sem itens devem ser retornadas como [].
            """;

    public static final String SYSTEM_PROCESS_AGENT = """
            Você é o Process Agent: advogado analista de contencioso, especializado em ler processos
            judiciais brasileiros por inteiro e extrair a posição estratégica das partes.
            """ + REGRAS_COMUNS;

    public static final String SYSTEM_CONTRACT_AGENT = """
            Você é o Contract Agent: advogado especializado em revisão contratual, focado em identificar
            cláusulas de risco, obrigações, multas, prazos, condições e inconsistências.
            """ + REGRAS_COMUNS;

    public static final String SYSTEM_DOCUMENT_AGENT = """
            Você é o Document Agent: especialista em triagem e classificação de documentos jurídicos
            brasileiros (peças processuais, contratos, decisões, comprovantes, laudos, certidões).
            """ + REGRAS_COMUNS;

    public static final String SYSTEM_LEGAL_RESEARCH_AGENT = """
            Você é o Legal Research Agent. Você só pode usar os TRECHOS DE FONTES AUTORIZADAS fornecidos
            nesta mensagem. É terminantemente proibido citar qualquer lei, artigo, súmula, enunciado ou
            julgado que não apareça literalmente nesses trechos, mesmo que você "conheça" a referência.
            Toda referência deve apontar a URL exata da fonte fornecida. Se os trechos não responderem à
            consulta, diga isso nas lacunas e devolva "referencias": [].
            """ + REGRAS_COMUNS;

    public static final String SYSTEM_DEADLINE_AGENT = """
            Você é o Deadline Agent: especialista em prazos processuais e contratuais brasileiros.
            Você extrai datas e eventos que já estão no material; você NÃO calcula prazos novos nem
            presume forma de contagem — quando a contagem não estiver explícita, use "não identificado".
            """ + REGRAS_COMUNS;

    public static final String SYSTEM_EVIDENCE_AGENT = """
            Você é o Evidence Agent: especialista em prova. Sua função é relacionar cada alegação das
            partes com os documentos do material que podem sustentá-la, sem supor a existência de
            documentos que não foram mencionados.
            """ + REGRAS_COMUNS;

    public static final String SYSTEM_DRAFTING_AGENT = """
            Você é o Drafting Agent: redator jurídico. Você produz RASCUNHOS para revisão obrigatória de
            um advogado responsável. Nunca afirme que a peça está pronta para protocolo. Use marcadores
            explícitos como [CONFERIR], [COMPLETAR] ou [FUNDAMENTO A VALIDAR] em qualquer ponto que
            dependa de informação ausente, e nunca invente números de processo, valores, datas ou
            fundamentos legais.
            """ + REGRAS_COMUNS;

    public static final String SYSTEM_SENIOR_LAWYER_AGENT = """
            Você é o Senior Lawyer Agent: advogado sênior responsável pelo caso. Você recebe o trabalho
            dos agentes especializados e produz o resultado final para o escritório. Você pondera as
            contribuições, aponta divergências entre os agentes, separa o que está comprovado do que é
            hipótese e registra o que ainda depende de verificação humana.
            """ + REGRAS_COMUNS;

    /** Agente 3: Document Agent — roda primeiro e orienta o roteamento dos demais. */
    public static String classificacaoDocumental(String nomeArquivo, String documentosJson, String amostraTexto) {
        return """
                Classifique o material abaixo.

                Formato de resposta (JSON estrito):
                {
                  "naturezaPrincipal": "(processo judicial|contrato|documento avulso|misto|não identificado)",
                  "confianca": "(alta|média|baixa)",
                  "documentos": [ { "nomeDocumento": "", "categoria": "(petição inicial|contestação|réplica|sentença|acórdão|decisão interlocutória|despacho|contrato|procuração|comprovante|laudo|certidão|notificação|outro)", "subtipo": "", "dataDocumento": "", "confianca": "(alta|média|baixa)", "justificativa": "" } ],
                  "indicios": ["trechos ou expressões do material que sustentam a classificação"],
                  "observacoes": ""
                }

                ARQUIVO: %s

                DOCUMENTOS JÁ IDENTIFICADOS NA ANÁLISE BASE (JSON):
                %s

                AMOSTRA DO TEXTO ORIGINAL:
                ---
                %s
                ---
                """.formatted(nomeArquivo, documentosJson, amostraTexto);
    }

    /** Agente 1: Process Agent. */
    public static String analiseProcessual(String dadosBaseJson, String resumo, String parteRepresentada,
                                           String contextoAdvogado, String amostraTexto) {
        return """
                Analise o processo de forma estratégica, do ponto de vista da parte representada.

                Formato de resposta (JSON estrito):
                {
                  "processoIdentificado": true,
                  "faseAtual": "",
                  "riscoGeral": "(alto|médio|baixo|não identificado)",
                  "teseAutor": "",
                  "teseReu": "",
                  "pontosControvertidos": [""],
                  "forcas": ["pontos favoráveis à parte representada, com base no material"],
                  "fragilidades": ["pontos desfavoráveis à parte representada"],
                  "estrategiaSugerida": ["medidas concretas, na ordem de prioridade"],
                  "prognostico": "avaliação qualitativa com a ressalva de que depende de prova e do juízo",
                  "observacoes": ""
                }

                PARTE REPRESENTADA: %s
                CONTEXTO INFORMADO PELO ADVOGADO: %s

                RESUMO DA ANÁLISE BASE:
                %s

                DADOS ESTRUTURADOS DA ANÁLISE BASE (partes, cronologia, pedidos, decisões, prazos, documentos):
                %s

                AMOSTRA DO TEXTO ORIGINAL:
                ---
                %s
                ---
                """.formatted(parteRepresentada, contextoAdvogado, resumo, dadosBaseJson, amostraTexto);
    }

    /** Agente 2: Contract Agent. */
    public static String analiseContratual(String parteRepresentada, String contextoAdvogado, String amostraTexto) {
        return """
                Analise o contrato abaixo e identifique cláusulas de risco, obrigações, multas, prazos,
                condições e inconsistências.

                Formato de resposta (JSON estrito):
                {
                  "contratoIdentificado": true,
                  "objetoContrato": "",
                  "partesContratantes": "",
                  "clausulasRisco": [ { "clausula": "identificação (ex.: Cláusula 7.2)", "trechoCitado": "citação curta e literal do contrato", "risco": "", "gravidade": "(alta|média|baixa)", "impacto": "", "recomendacao": "" } ],
                  "obrigacoes": [ { "parteObrigada": "", "descricao": "", "prazoCumprimento": "", "clausula": "", "consequenciaDescumprimento": "" } ],
                  "multas": [ { "clausula": "", "hipoteseIncidencia": "", "valorOuPercentual": "", "parteResponsavel": "", "cumulatividade": "(cumulativa com perdas e danos|não cumulativa|não identificado)" } ],
                  "prazos": [ { "clausula": "", "descricao": "", "dataOuTermo": "", "tipo": "(vigência|renovação|denúncia|pagamento|entrega|outro)" } ],
                  "condicoes": [ { "clausula": "", "tipo": "(suspensiva|resolutiva|termo|outra)", "descricao": "", "efeito": "" } ],
                  "inconsistencias": [ { "descricao": "", "clausulasEnvolvidas": [""], "gravidade": "(alta|média|baixa)", "sugestaoCorrecao": "" } ],
                  "observacoes": ""
                }

                Regras adicionais:
                - "trechoCitado" deve ser transcrição literal e curta do contrato; se não for possível citar, use "não identificado".
                - Avalie o risco na perspectiva da parte representada.
                - Se o material não for um contrato, retorne "contratoIdentificado": false e listas vazias.

                PARTE REPRESENTADA: %s
                CONTEXTO INFORMADO PELO ADVOGADO: %s

                TEXTO DO CONTRATO:
                ---
                %s
                ---
                """.formatted(parteRepresentada, contextoAdvogado, amostraTexto);
    }

    /** Agente 5: Deadline Agent. */
    public static String agendaPrazos(String prazosBaseJson, String cronologiaJson, String amostraTexto) {
        return """
                Extraia as datas e eventos importantes do material, consolidando o que já foi identificado
                na análise base.

                Formato de resposta (JSON estrito):
                {
                  "prazos": [ { "descricao": "", "dataInicio": "AAAA-MM-DD ou não identificado", "dataFinal": "AAAA-MM-DD ou não identificado", "prazoEmDias": "número ou não identificado", "tipoContagem": "(dias úteis|dias corridos|não identificado)", "fundamento": "dispositivo ou cláusula citada no material, ou não identificado", "criticidade": "(alta|média|baixa)", "parteResponsavel": "" } ],
                  "eventos": [ { "data": "AAAA-MM-DD", "evento": "", "tipo": "(audiência|perícia|sessão de julgamento|vencimento|protocolo|outro)", "comparecimentoObrigatorio": "(sim|não|não identificado)", "observacoes": "" } ],
                  "datasAmbiguas": ["datas contraditórias ou incompletas encontradas no material"],
                  "aviso": ""
                }

                Regras adicionais:
                - Não calcule datas finais que não estejam no material; se o termo final não estiver expresso, use "não identificado".
                - Ordene "prazos" e "eventos" da data mais antiga para a mais recente; itens sem data vão ao final.
                - Em "aviso", registre que a contagem processual deve ser conferida no sistema do tribunal.

                PRAZOS DA ANÁLISE BASE (JSON):
                %s

                CRONOLOGIA DA ANÁLISE BASE (JSON):
                %s

                AMOSTRA DO TEXTO ORIGINAL:
                ---
                %s
                ---
                """.formatted(prazosBaseJson, cronologiaJson, amostraTexto);
    }

    /** Agente 6: Evidence Agent. */
    public static String matrizEvidencias(String dadosBaseJson, String gruposEvidenciaJson, String amostraTexto) {
        return """
                Relacione cada alegação relevante com os documentos do material que podem sustentá-la.

                Formato de resposta (JSON estrito):
                {
                  "alegacoes": [ {
                    "alegacao": "",
                    "parteQueAlega": "",
                    "onusDaProva": "(autor|réu|não identificado)",
                    "documentosSuporte": [ { "nomeDocumento": "", "localizacao": "página, fl. ou trecho onde aparece; ou não identificado", "comoSustenta": "", "forcaProbatoria": "(forte|média|fraca)" } ],
                    "grauSustentacao": "(sustentada|parcialmente sustentada|não sustentada)",
                    "observacoes": ""
                  } ],
                  "lacunasProbatorias": ["alegações sem documento que as sustente"],
                  "provasSugeridas": ["provas a produzir ou documentos a solicitar ao cliente"],
                  "observacoes": ""
                }

                Regras adicionais:
                - Use apenas documentos citados no material; nunca invente anexo, fl. ou número de documento.
                - Se uma alegação não tiver documento correspondente, mantenha "documentosSuporte": [] e registre a lacuna.

                DADOS ESTRUTURADOS DA ANÁLISE BASE:
                %s

                GRUPOS DE EVIDÊNCIA DA ANÁLISE BASE:
                %s

                AMOSTRA DO TEXTO ORIGINAL:
                ---
                %s
                ---
                """.formatted(dadosBaseJson, gruposEvidenciaJson, amostraTexto);
    }

    /** Agente 4: Legal Research Agent — recebe apenas trechos já baixados de fontes autorizadas. */
    public static String pesquisaJuridica(String consulta, String trechosAutorizados) {
        return """
                Responda à consulta jurídica usando EXCLUSIVAMENTE os trechos de fontes autorizadas abaixo.

                Formato de resposta (JSON estrito):
                {
                  "sintese": "resposta objetiva, apoiada apenas nos trechos fornecidos",
                  "referencias": [ { "tipo": "(legislação|jurisprudência|súmula|enunciado|outro)", "identificacao": "ex.: CPC, art. 373, II", "fonte": "nome da fonte fornecida", "url": "URL exata fornecida", "trechoRelevante": "citação literal extraída do trecho fornecido" } ],
                  "lacunas": ["o que a consulta pedia e os trechos não permitem responder"]
                }

                Regras adicionais:
                - Cada referência precisa ter "url" idêntica a uma das URLs fornecidas; referências com outra URL serão descartadas.
                - "trechoRelevante" deve ser literal do material fornecido.
                - Se os trechos não sustentarem resposta, devolva "sintese": "não identificado" e "referencias": [].

                CONSULTA: %s

                TRECHOS DE FONTES AUTORIZADAS:
                ---
                %s
                ---
                """.formatted(consulta, trechosAutorizados);
    }

    /** Deriva a consulta de pesquisa a partir do caso, quando o advogado não informa uma. */
    public static String consultaDePesquisa(String resumo, String pedidosJson) {
        return """
                Formule UMA consulta de pesquisa jurídica objetiva (máximo 15 palavras) para o caso abaixo,
                usando terminologia da legislação brasileira.

                Formato de resposta (JSON estrito):
                { "consulta": "" }

                RESUMO DO CASO:
                %s

                PEDIDOS:
                %s
                """.formatted(resumo, pedidosJson);
    }

    /** Agente 7: Drafting Agent. */
    public static String rascunho(TipoRascunho tipo, String contextoCasoJson, String parteRepresentada,
                                  String contextoAdvogado, int maxChars) {
        return """
                Produza um RASCUNHO de %s, para revisão obrigatória do advogado responsável.

                Formato de resposta (JSON estrito):
                {
                  "titulo": "",
                  "conteudo": "texto do rascunho, com parágrafos separados por \\n\\n, no máximo %d caracteres",
                  "pontosDeAtencao": ["riscos, teses frágeis ou escolhas de redação que o advogado deve decidir"],
                  "lacunasParaPreencher": ["cada informação marcada como [CONFERIR]/[COMPLETAR] no texto"]
                }

                Regras adicionais:
                - Nunca invente número de processo, vara, comarca, valores, datas ou citação de lei/julgado:
                  use [COMPLETAR] ou [FUNDAMENTO A VALIDAR].
                - Escreva em português jurídico brasileiro, claro e sem excesso de adjetivos.
                - No caso de e-mail ao cliente, use linguagem acessível e evite promessas de resultado.

                PARTE REPRESENTADA: %s
                CONTEXTO INFORMADO PELO ADVOGADO: %s

                MATERIAL CONSOLIDADO DO CASO (resultado dos demais agentes, em JSON):
                %s
                """.formatted(tipo.descricao(), maxChars, parteRepresentada, contextoAdvogado, contextoCasoJson);
    }

    /** Agente 8: Senior Lawyer Agent — orquestrador, produz o resultado final. */
    public static String parecerSenior(String nomeArquivo, String parteRepresentada, String materialAgentesJson) {
        return """
                Você recebeu o trabalho dos agentes especializados sobre o caso. Produza o resultado final.

                Formato de resposta (JSON estrito):
                {
                  "titulo": "",
                  "sinteseExecutiva": "3 a 6 frases, do ponto de vista da parte representada",
                  "conclusoes": [""],
                  "riscosPrincipais": ["risco + gravidade + origem (qual agente apontou)"],
                  "recomendacoes": ["ações recomendadas, em ordem de prioridade"],
                  "proximosPassos": ["passos operacionais com responsável sugerido"],
                  "pendenciasParaOAdvogado": ["o que precisa de verificação humana antes de qualquer uso"],
                  "divergenciasEntreAgentes": "divergências ou lacunas entre os resultados dos agentes; 'nenhuma' se não houver",
                  "ressalvas": "limites da análise (material analisado, ausência de acesso aos autos, revisão obrigatória)"
                }

                Regras adicionais:
                - Não introduza fatos novos: sintetize apenas o que os agentes produziram.
                - Se um agente não foi executado ou não encontrou dados, considere isso uma lacuna, não uma conclusão.
                - Referências jurídicas só podem ser mencionadas se estiverem no bloco de pesquisa jurídica com URL.

                ARQUIVO: %s
                PARTE REPRESENTADA: %s

                MATERIAL DOS AGENTES (JSON):
                %s
                """.formatted(nomeArquivo, parteRepresentada, materialAgentesJson);
    }
}
