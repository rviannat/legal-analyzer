# Equipes de Inteligência do Legal Analyzer

## Visão geral

O Legal Analyzer é organizado em equipes de agentes especializados. Cada equipe responde a uma pergunta diferente e seus resultados são acumulados para que as equipes seguintes possam trabalhar sobre contexto já produzido.

```text
PROCESSO
   |
   +-- EQUIPE 1: Análise documental ........ 7 agentes
   |       |
   |       v
   +-- EQUIPE 2: Análise jurídica .......... 8 agentes
   |       |
   |       v
   +-- EQUIPE 3: Inteligência externa ...... 8 agentes
   |       |
   |       v
   +-- RELATÓRIO FINAL / DOWNLOAD
   |
   +-- EQUIPE 4: Inteligência jurídica ..... planejada
```

As três primeiras equipes são executadas **em sequência**, para controlar consumo de CPU, memória e chamadas externas. A Equipe 3 somente começa depois que a Equipe 2 termina.

## Experiência de progresso

A barra de progresso representa a equipe atualmente em execução. Ao trocar de equipe, a barra volta para 0%.

```text
Equipe 1 — Analisando
████████████████████ 100%

Equipe 2 — Analisando
████████████████████ 100%

Equipe 3 — Validando DataJud/Jus
████████████████████ 100%
```

A Central de Processos também exibe os logs com a equipe, agente, etapa, progresso, contexto e estimativa.

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

A Equipe 2 começa automaticamente após a Equipe 1. Os agentes recebem o resultado da análise base e os agentes posteriores recebem relatórios anteriores da própria equipe.

## Equipe 3 — Inteligência Externa / Validação Processual

**Pergunta:** o que as fontes processuais oficiais confirmam, complementam ou contradizem no que já foi analisado?

A Equipe 3 começa **somente depois da conclusão da Equipe 2**. Ela consome uma consulta DataJud por processo e distribui o resultado aos seus oito agentes.

### Agentes

1. `ProcessSearchAgent` — confirma processo, tribunal, grau, classe, assunto e metadados processuais.
2. `MovementAgent` — coleta e normaliza movimentações e eventos para a linha do tempo externa.
3. `PartiesAgent` — valida partes, polos, representantes e nomes disponíveis externamente.
4. `DecisionsAgent` — identifica decisões e atos relevantes e os relaciona às conclusões internas.
5. `CourtAgent` — valida tribunal, órgão julgador, grau, classe e dados de competência disponíveis.
6. `TimelineAgent` — reconcilia a linha do tempo documental com a linha do tempo oficial externa.
7. `ExternalEvidenceAgent` — identifica fatos externos que confirmam ou contradizem evidências e conclusões das equipes 1 e 2.
8. `JusReconciliationAgent` — reunião final da equipe; consolida confirmações, conflitos, lacunas e divergências.

### Princípio

```text
Equipe 1 + Equipe 2
        |
        +---- contexto produzido
        |
        v
   consulta DataJud
        |
        v
   7 especialistas
        |
        v
JusReconciliationAgent
        |
        +-- confirmado
        +-- conflito
        +-- informação nova
        +-- lacuna
```

A Equipe 3 não repete a análise do PDF. Ela funciona como uma camada independente de validação factual/processual.

### Rastreabilidade

Cada etapa registra, quando disponível: processo, fonte, endpoint, tribunal, data/hora, status, agente, duração, erro e resultado. A chave DataJud nunca aparece nos logs.

### Integração existente

A aplicação possui `DataJudService`, que resolve o tribunal pelo número CNJ, consulta o endpoint público correspondente e extrai classe, órgão julgador, grau e movimentações. A Equipe 3 reutiliza essa camada em vez de duplicar autenticação, resolução de tribunal e transporte HTTP.

## Equipe 4 — Inteligência Jurídica / Jurisprudência

**Status:** planejada.

**Pergunta:** o que legislação, jurisprudência, precedentes e fundamentos jurídicos externos dizem sobre as questões identificadas?

Será definida depois da Equipe 3 para separar validação factual/processual de pesquisa jurídica.

## Regras arquiteturais

1. Responsabilidade única e observável por agente.
2. Agentes posteriores recebem contexto relevante dos anteriores.
3. Equipes longas são executadas sequencialmente para controlar carga.
4. Consultas externas são rastreáveis e persistidas.
5. Falha externa não apaga resultados já produzidos.
6. Divergências são preservadas, não escondidas.
7. Processo, equipe, agente, etapa, progresso e logs são observáveis na Central de Processos.
8. Relatórios são persistidos para download posterior.
9. Fechar o navegador não interrompe o processamento do backend.
