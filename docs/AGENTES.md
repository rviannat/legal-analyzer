# Arquitetura Multiagente — Legal Analyzer

O Legal Analyzer possui **15 agentes de IA**, organizados em duas equipes. A execução é assíncrona e persistida, permitindo que o usuário feche o frontend sem interromper o processamento.

## Visão geral

```text
                    PROCESSO
                       |
                 Análise Base
                    7 agentes
                       |
                       v
              Relatório Base salvo
                       |
                       v
             Análise Especializada
                    8 agentes
                       |
                       v
             Relatório Final salvo
```

A segunda equipe recebe o resultado produzido pela primeira. Os agentes especializados também recebem os resultados relevantes dos agentes anteriores, evitando análises isoladas e permitindo uma conclusão progressivamente mais rica.

---

# Equipe 1 — Análise Base

A primeira equipe transforma o PDF bruto em uma representação estruturada do processo.

## 1. ExtractionAgent

**Responsabilidade:** extrair fatos estruturados de cada chunk do documento.

Identifica partes, cronologia, pedidos, decisões, prazos/datas e documentos relevantes. É executado por trecho quando o PDF é dividido em chunks.

**Entrada:** texto de um chunk.

**Saída:** `ExtractionResult`.

**Papel na equipe:** coleta de evidências estruturais. É a etapa que transforma texto não estruturado em dados que os demais agentes conseguem consumir.

## 2. ConsolidationAgent

**Responsabilidade:** transformar vários resultados parciais em uma visão única e coerente.

Remove duplicidades, organiza cronologicamente e consolida resultados de documentos grandes. Usa uma estratégia map-reduce para evitar exceder a janela de contexto do modelo.

**Entrada:** vários `ExtractionResult`.

**Saída:** `ExtractionResult` consolidado.

**Papel na equipe:** memória coletiva dos chunks.

## 3. ResumoAgent

**Responsabilidade:** produzir uma síntese objetiva do processo a partir da estrutura consolidada.

**Entrada:** resultado consolidado.

**Saída:** resumo estruturado para consumo humano e pelas etapas posteriores.

**Papel na equipe:** visão executiva inicial.

## 4. InconsistenciaAgent

**Responsabilidade:** identificar contradições, divergências, informações conflitantes e pontos que exigem conferência.

**Entrada:** conteúdo estruturado e contexto consolidado.

**Saída:** inconsistências e alertas.

**Papel na equipe:** controle de qualidade factual.

## 5. EvidenciaAgent

**Responsabilidade:** organizar as evidências e relacioná-las às alegações relevantes.

**Entrada:** processo consolidado e informações extraídas.

**Saída:** grupos/matriz de evidências.

**Papel na equipe:** conectar documentos, fatos e alegações.

## 6. PerguntasAgent

**Responsabilidade:** gerar perguntas de investigação para descobrir lacunas, pontos controvertidos e informações que precisam de confirmação.

**Entrada:** análise consolidada, inconsistências e evidências.

**Saída:** perguntas investigativas.

**Papel na equipe:** identificar o que ainda não sabemos.

## 7. RelatorioExecutivoAgent

**Responsabilidade:** consolidar a análise base em um relatório executivo.

**Entrada:** resultados estruturados produzidos pelas etapas anteriores.

**Saída:** relatório base persistido no processo.

**Papel na equipe:** fechar a primeira fase e fornecer o contexto que alimentará a equipe especializada.

---

# Equipe 2 — Análise Jurídica Especializada

A segunda equipe inicia **automaticamente após a conclusão da análise base**. Não é necessário o usuário clicar em um botão de análise.

Ela trabalha sobre o processo já estruturado e sobre os resultados da Equipe 1.

## 8. DocumentAgent

**Responsabilidade:** analisar e classificar o conjunto documental com visão jurídica especializada.

Identifica a natureza das peças, documentos relevantes, documentos faltantes ou suspeitos e o papel de cada documento no caso.

**Entrada:** processo + análise base.

**Saída:** classificação e achados documentais.

## 9. ProcessAgent

**Responsabilidade:** realizar análise estratégica do processo.

Avalia fase processual, teses, controvérsias, forças, fragilidades, estratégia e prognóstico.

**Entrada:** análise base + resultados documentais + contexto do processo.

**Saída:** diagnóstico processual e estratégico.

## 10. ContractAgent

**Responsabilidade:** examinar contratos e obrigações jurídicas.

Analisa cláusulas, obrigações, multas, condições, prazos, riscos e inconsistências contratuais.

**Entrada:** documentos contratuais + análise anterior.

**Saída:** matriz de riscos e achados contratuais.

## 11. DeadlineAgent

**Responsabilidade:** analisar o calendário jurídico do caso.

Identifica prazos, audiências, vencimentos, eventos críticos e dependências temporais.

**Entrada:** cronologia, movimentações e resultados dos agentes anteriores.

**Saída:** eventos e riscos temporais.

## 12. EvidenceAgent

**Responsabilidade:** construir uma análise probatória especializada.

Relaciona alegações, fatos e provas, identifica lacunas e avalia quais documentos sustentam ou enfraquecem cada ponto relevante.

**Entrada:** evidências da análise base + documentos + conclusões anteriores.

**Saída:** matriz probatória especializada.

## 13. LegalResearchAgent

**Responsabilidade:** executar pesquisa jurídica rastreável.

Pesquisa legislação e jurisprudência em fontes autorizadas e usa os metadados do processo quando disponíveis. Não deve inventar referências: toda citação precisa estar vinculada ao conteúdo efetivamente recuperado.

**Entrada:** fatos, teses, controvérsias, metadados processuais e resultados anteriores.

**Saída:** fundamentos jurídicos e referências verificáveis.

## 14. DraftingAgent

**Responsabilidade:** transformar os achados em rascunhos jurídicos úteis.

Pode produzir parecer, manifestação, petição, relatório ou comunicação ao cliente, sempre para revisão do profissional responsável.

**Entrada:** conclusões das etapas anteriores e fundamentos jurídicos.

**Saída:** documentos/rascunhos estruturados.

## 15. SeniorLawyerAgent

**Responsabilidade:** realizar a consolidação jurídica final.

Recebe os resultados da equipe especializada, confronta conclusões, identifica lacunas e contradições e produz a visão final para apoio à decisão do advogado.

**Entrada:** relatório base + resultados dos especialistas + pesquisa + matriz probatória + rascunhos.

**Saída:** conclusão e relatório especializado final.

**Importante:** o SeniorLawyerAgent é o **oitavo agente da equipe especializada**, não o primeiro. Ele funciona como revisor/consolidador final.

---

# Fluxo entre os agentes

```text
PDF
 |
v
ExtractionAgent × N
 |
v
ConsolidationAgent
 |
+--> ResumoAgent
+--> InconsistenciaAgent
+--> EvidenciaAgent
+--> PerguntasAgent
 |
v
RelatorioExecutivoAgent
 |
v
RELATÓRIO BASE
 |
+--> DocumentAgent
 |
+--> ProcessAgent
 |
+--> ContractAgent
 |
+--> DeadlineAgent
 |
+--> EvidenceAgent
 |
+--> LegalResearchAgent
 |
+--> DraftingAgent
 |
v
SeniorLawyerAgent
 |
v
RELATÓRIO ESPECIALIZADO
```

A ordem interna pode permitir paralelismo quando não houver dependência entre agentes. Quando um agente depende de um resultado anterior, esse resultado é fornecido explicitamente ao próximo agente.

# Observabilidade

Cada fase possui status e progresso persistidos. A interface deve mostrar:

- fase atual;
- agente atual;
- percentual da fase;
- mensagem operacional;
- estimativa quando disponível;
- agentes já concluídos;
- logs detalhados;
- relatório disponível para download.

A barra da segunda fase começa novamente em `0%` quando a análise base chega a `100%`, deixando claro que se trata de uma nova etapa de processamento e não de perda de progresso.

# Resumo das equipes

| Equipe | Agentes | Objetivo |
|---|---:|---|
| Análise Base | 7 | Estruturar, validar, resumir e preparar o processo |
| Análise Especializada | 8 | Aprofundar estratégia, prova, contratos, prazos, pesquisa e redação |
| **Total** | **15** | **Análise jurídica multiagente** |

## Princípio arquitetural

Os agentes não devem funcionar como 15 modelos independentes. O valor da arquitetura está no **encadeamento de contexto**: cada etapa recebe resultados confiáveis das etapas anteriores, acrescenta uma perspectiva especializada e deixa sua saída disponível para os agentes seguintes.

Os resultados devem ser persistidos para que o processamento seja resiliente a reinícios e para que o usuário possa consultar os relatórios e logs posteriormente.
