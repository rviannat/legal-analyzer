# Equipes de Inteligência do Legal Analyzer

## Visão geral

O Legal Analyzer é organizado em equipes de agentes especializados. Cada equipe responde a uma pergunta diferente e seus resultados são acumulados para que as equipes seguintes possam trabalhar sobre contexto já produzido.

```text
PROCESSO
   |
   +-- EQUIPE 1: Análise documental ........ 7 agentes
   |
   +-- EQUIPE 2: Análise jurídica .......... 8 agentes
   |
   +-- EQUIPE 3: Inteligência externa ...... 8 agentes
   |
   +-- EQUIPE 4: Inteligência jurídica ..... planejada
   |
   +-- Reconciliação / Senior Lawyer
```

## Equipe 1 — Análise Base / Documental

**Pergunta:** o que existe nos documentos?

1. `ExtractionAgent` — extrai fatos, pedidos, decisões, prazos, documentos e entidades de cada parte do processo.
2. `ConsolidationAgent` — consolida partes analisadas, remove duplicidades e mantém uma representação coerente do processo.
3. `ResumoAgent` — produz uma síntese estruturada do caso.
4. `InconsistenciaAgent` — identifica contradições, divergências e informações incompatíveis.
5. `EvidenciaAgent` — organiza evidências e relaciona documentos, fatos e alegações.
6. `PerguntasAgent` — identifica lacunas e perguntas que ainda precisam ser respondidas.
7. `RelatorioExecutivoAgent` — consolida a análise documental em relatório executivo.

**Saída:** relatório base, fatos estruturados, evidências, inconsistências, lacunas e contexto para as equipes seguintes.

## Equipe 2 — Análise Jurídica Especializada

**Pergunta:** o que juridicamente pode ser concluído a partir do material analisado?

1. `DocumentAgent` — classificação e análise jurídica dos documentos relevantes.
2. `ProcessAgent` — diagnóstico da estratégia e situação processual.
3. `ContractAgent` — análise de contratos, obrigações, cláusulas e riscos.
4. `DeadlineAgent` — identificação e análise de prazos e eventos críticos.
5. `EvidenceAgent` — avaliação especializada da força e relação das evidências.
6. `LegalResearchAgent` — pesquisa jurídica e fundamentação aplicável.
7. `DraftingAgent` — preparação de rascunhos e conclusões jurídicas.
8. `SeniorLawyerAgent` — revisão e consolidação das conclusões da equipe.

A equipe 2 é iniciada automaticamente após a análise base e recebe o contexto produzido anteriormente.

## Equipe 3 — Inteligência Externa / Validação Processual

**Pergunta:** o que as fontes processuais oficiais confirmam, complementam ou contradizem no que já foi analisado?

A equipe 3 será especializada no consumo de fontes oficiais, inicialmente a integração DataJud/Jus já existente na aplicação. Cada agente terá um domínio específico para evitar duplicidade e permitir rastreabilidade.

### Agentes

1. `ProcessSearchAgent` — confirma processo, tribunal, grau, classe, assunto e metadados processuais.
2. `MovementAgent` — coleta e normaliza movimentações e eventos para a linha do tempo externa.
3. `PartiesAgent` — valida partes, polos, representantes e nomes disponíveis externamente.
4. `DecisionsAgent` — identifica decisões e atos relevantes e os relaciona às conclusões internas.
5. `CourtAgent` — valida tribunal, órgão julgador, grau, classe e dados de competência disponíveis.
6. `TimelineAgent` — reconcilia a linha do tempo documental com a linha do tempo oficial externa.
7. `ExternalEvidenceAgent` — identifica fatos externos que confirmam ou contradizem evidências e conclusões das equipes 1 e 2.
8. `JusReconciliationAgent` — reunião final da equipe; consolida confirmações, conflitos, lacunas e divergências para consumo jurídico posterior.

### Princípio

```text
Resultados Equipes 1 e 2 + Dados oficiais externos
                         |
                    normalização
                         |
                      comparação
                  /       |       \
             confirmado conflito  lacuna
                  \       |       /
                   JusReconciliation
                         |
                  Relatório externo
```

A equipe 3 não deve repetir a análise do PDF. Ela atua como camada independente de validação factual/processual.

### Rastreabilidade

Cada consulta deve registrar, quando disponível: processo, fonte, endpoint, tribunal, data/hora, parâmetros sem segredos, status HTTP, referência ao resultado bruto, dados normalizados, agente responsável, duração, erro e evidência interna que motivou a consulta.

### Integração existente

A aplicação já possui `DataJudService`, que resolve o tribunal pelo número CNJ, consulta o endpoint público correspondente e extrai classe, órgão julgador, grau e movimentações. A Equipe 3 deve reutilizar essa camada em vez de duplicar autenticação, resolução de tribunal e transporte HTTP.

## Equipe 4 — Inteligência Jurídica / Jurisprudência

**Status:** planejada.

**Pergunta:** o que legislação, jurisprudência, precedentes e fundamentos jurídicos externos dizem sobre as questões identificadas?

Será definida depois da Equipe 3 para separar validação factual/processual de pesquisa jurídica.

## Regras arquiteturais

1. Responsabilidade única e observável por agente.
2. Agentes posteriores recebem contexto relevante dos anteriores.
3. Consultas externas são rastreáveis e persistidas.
4. Falha externa não apaga resultados já produzidos.
5. Resultados indicam origem e confiança quando possível.
6. Divergências são preservadas, não escondidas.
7. Processo, equipe, agente, etapa, progresso e logs são observáveis na Central de Processos.
8. Relatórios são persistidos para download posterior.
