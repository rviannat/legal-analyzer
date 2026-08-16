# Legal Analyzer — Análise Jurídica de Processos com Agentes de IA

Backend Java 17 + Spring Boot 3 para análise assíncrona de processos jurídicos com IA, PostgreSQL, RAG/chat e auditoria oficial através da API Pública DataJud/CNJ.

> **Status atual:** upload, processamento assíncrono, análise documental, análise especializada, RAG/chat, relatório PDF persistido, integração DataJud/CNJ e central de pesquisas estão implementados. Resultados de pesquisas DataJud agora são persistidos no PostgreSQL antes de qualquer processamento.

> O Legal Analyzer é ferramenta de apoio. Resultados de IA e dados públicos devem ser revisados por profissional habilitado.

## Status atual

- Upload de PDF e pipeline assíncrono com progresso.
- Detecção/enriquecimento de CNJ via DataJud.
- Análise documental e análise jurídica especializada opcional.
- Três equipes de agentes implementadas.
- RAG, briefing e chat ancorado no processo.
- Relatório PDF gerado e persistido no PostgreSQL como `bytea`.
- Exportação do relatório diretamente do PostgreSQL.
- PostgreSQL com Flyway e validação de schema.
- Logs detalhados de etapas, DataJud, geração de PDF e persistência.
- Pesquisas DataJud por CNJ, CPF e Tribunal + Assunto/TPU.
- Resultados de pesquisa persistidos em `datajud_pesquisas`.
- Histórico das últimas pesquisas disponível em `GET /api/v1/datajud/pesquisas`.
- Processos encontrados por pesquisa podem entrar no mesmo pipeline utilizado pelo upload.

## PostgreSQL

Banco padrão: `legal_analyzer`, porta `5432`.

Credenciais não são versionadas:

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/legal_analyzer"
export SPRING_DATASOURCE_USERNAME="postgres"
export SPRING_DATASOURCE_PASSWORD="admin"
```

## DataJud/CNJ

O DataJud é utilizado como camada de auditoria e enriquecimento oficial.

Consultas disponíveis:

- processo por CNJ;
- processo por CPF;
- tribunal + assunto/TPU;
- amostras de processos encerrados;
- processamento direto de processo localizado por CNJ, CPF ou amostra.

A sequência para uma pesquisa processável é:

```text
Pesquisa DataJud
      ↓
Resultado encontrado
      ↓
Persistência em PostgreSQL
      ↓
Usuário seleciona PROCESSAR
      ↓
Mesmo pipeline do upload
      ↓
Agentes → RAG → Relatório PDF
```

### Endpoints DataJud

```text
GET  /api/v1/datajud/processos/cnj
POST /api/v1/datajud/processos/cnj
POST /api/v1/datajud/processos/cnj/processar
GET  /api/v1/datajud/processos/cpf
POST /api/v1/datajud/processos/cpf/processar
GET  /api/v1/datajud/processos/amostra
POST /api/v1/datajud/processos/amostra/processar
GET  /api/v1/datajud/pesquisas
```

Toda pesquisa encontrada é registrada com tipo, parâmetro, tribunal, assunto, CNJ, classe, grau, órgão julgador, status de processamento e eventual `analiseId`.

## As três equipes de agentes

### Equipe 1 — Análise Base / Documental — 7 agentes

| Agente | Responsabilidade |
|---|---|
| `ExtractionAgent` | Extrai fatos, pedidos, decisões, prazos, documentos e entidades. |
| `ConsolidationAgent` | Consolida chunks e remove duplicidades. |
| `ResumoAgent` | Produz a síntese estruturada. |
| `InconsistenciaAgent` | Identifica contradições e divergências. |
| `EvidenciaAgent` | Organiza evidências e relações entre fatos e documentos. |
| `PerguntasAgent` | Identifica lacunas e perguntas de investigação. |
| `RelatorioExecutivoAgent` | Consolida a análise documental. |

### Equipe 2 — Análise Jurídica Especializada — 8 agentes

| Agente | Responsabilidade |
|---|---|
| `DocumentAgent` | Classificação documental e análise jurídica. |
| `ProcessAgent` | Estratégia, situação processual e teses. |
| `ContractAgent` | Contratos, obrigações, riscos e cláusulas. |
| `DeadlineAgent` | Prazos, audiências e eventos críticos. |
| `EvidenceAgent` | Avaliação especializada das evidências. |
| `LegalResearchAgent` | Pesquisa jurídica rastreável e fundamentação. |
| `DraftingAgent` | Rascunhos jurídicos. |
| `SeniorLawyerAgent` | Revisão e consolidação jurídica. |

### Equipe 3 — Inteligência Externa / Validação Processual — 8 agentes

| Agente | Responsabilidade |
|---|---|
| `ProcessSearchAgent` | Confirma processo e metadados oficiais. |
| `MovementAgent` | Coleta e normaliza movimentações externas. |
| `PartiesAgent` | Valida partes e nomes disponíveis externamente. |
| `DecisionsAgent` | Identifica decisões e atos relevantes. |
| `CourtAgent` | Valida tribunal, órgão, grau, classe e competência. |
| `TimelineAgent` | Reconcilia linha do tempo documental e oficial. |
| `ExternalEvidenceAgent` | Confronta evidências internas com fontes externas. |
| `JusReconciliationAgent` | Consolida confirmações, conflitos e lacunas. |

## Análise especializada

Suporta `parteRepresentada`, `contextoAdicional`, `pesquisaJuridica`, `consultaPesquisa`, `forcarProcesso`, `forcarContrato` e rascunhos de parecer, manifestação, petição e e-mail.

```text
POST /api/v1/processos/analises/{idDaAnaliseBase}/especializada
```

## Persistência das pesquisas

A tabela `datajud_pesquisas` mantém histórico das consultas e permite saber se um resultado já foi encaminhado ao processamento.

Logs importantes:

```text
[DATAJUD-PERSISTENCIA:id] RESULTADO SALVO
[DATAJUD-PERSISTENCIA:id] AMOSTRA SALVA
[DATAJUD-PERSISTENCIA:id] PROCESSAMENTO VINCULADO
```

Isso garante rastreabilidade entre a consulta oficial e a análise gerada.

## RAG, chat e relatório

Após o processamento, o caso pode ser indexado para briefing e chat.

O relatório final é salvo no PostgreSQL e exportado diretamente do banco:

```text
GET /api/v1/processos/analises/{id}/relatorio-pdf
```

O backend registra nome do arquivo, bytes gerados, persistência e confirmação da gravação.

## Próximas evoluções

- Central de histórico de pesquisas no frontend.
- Filtros avançados por TPU.
- Auditoria aprofundada de movimentações ocultas.
- Alertas críticos de prazos.
- Reconciliação DataJud mais profunda entre as três equipes.
- Refinamento do Legal Research com metadados TPU estruturados.

## Execução

```bash
mvn clean install
mvn spring-boot:run
```

Pré-requisitos: JDK 17+, Maven 3.9+, PostgreSQL, Ollama quando configurado e chave DataJud quando a integração estiver habilitada.

## Documentação

- `docs/EQUIPES-E-AGENTES.md`
- `docs/AGENTES.md`
- `docs/EQUIPE-3-DATAJUD.md`

Não grave senhas ou chaves de API no Git.
