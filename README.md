# Legal Analyzer — Análise Jurídica de Processos com Agentes de IA

Backend em **Java 17 + Spring Boot 3** para análise assíncrona de processos jurídicos com IA, persistência em PostgreSQL, RAG/chat e auditoria oficial através da API Pública DataJud/CNJ.

> **Status:** plataforma em evolução ativa. O fluxo principal de upload, análise documental, análise especializada, integração DataJud/CNJ, RAG, persistência PostgreSQL e geração/download do relatório PDF já estão implementados. A camada de pesquisas DataJud está disponível e a próxima evolução é unificar todos os resultados de pesquisa ao mesmo pipeline do upload.

> **Importante:** o Legal Analyzer é uma ferramenta de apoio à análise jurídica. Resultados de IA e dados públicos devem ser revisados por profissional habilitado.

## Status atual

### Fluxo principal

- Upload de PDF por `POST /api/v1/processos/analisar`.
- Extração de texto com PDFBox e divisão em chunks.
- Processamento assíncrono com acompanhamento de status, etapa e progresso.
- Análise documental em equipe.
- Análise jurídica especializada opcional.
- RAG/indexação e chat ancorado no processo.
- Geração do relatório final em PDF.
- Persistência do PDF original, dados do processo e relatório PDF no PostgreSQL.
- Exportação do relatório diretamente do PostgreSQL por `GET /api/v1/processos/analises/{id}/relatorio-pdf`.
- Logs detalhados de processamento, geração do PDF e persistência.

### PostgreSQL

O banco é PostgreSQL na porta padrão `5432`, com banco `legal_analyzer`. O Hibernate usa validação de schema e os arquivos PDF são armazenados como `bytea`.

As credenciais não ficam versionadas. Configure, por exemplo:

```bash
export SPRING_DATASOURCE_PASSWORD=admin
```

### DataJud/CNJ

A API Pública do DataJud é usada como camada de auditoria e enriquecimento oficial. Já existem consultas por:

- número CNJ;
- CPF;
- tribunal + assunto/TPU;
- consulta agregada para amostras de processos encerrados;
- processamento direto de processo localizado por CNJ ou CPF.

Quando um processo é localizado por CNJ/CPF, o backend consegue gerar uma representação PDF dos dados oficiais e encaminhá-la ao mesmo pipeline assíncrono utilizado pelo upload.

A integração também fornece dados para enriquecimento da capa e auditoria da linha do tempo, incluindo tribunal, grau, classe, órgão julgador e movimentações oficiais quando disponíveis.

## As três equipes de agentes

O processamento especializado é organizado em **três equipes implementadas**, executadas sequencialmente. A documentação detalhada está em [`docs/EQUIPES-E-AGENTES.md`](docs/EQUIPES-E-AGENTES.md).

### Equipe 1 — Análise Base / Documental — 7 agentes

**Pergunta:** o que existe nos documentos?

| Agente | Responsabilidade |
|---|---|
| `ExtractionAgent` | Extrai fatos, pedidos, decisões, prazos, documentos e entidades das partes. |
| `ConsolidationAgent` | Consolida resultados de múltiplos chunks e remove duplicidades. |
| `ResumoAgent` | Produz a síntese estruturada do processo. |
| `InconsistenciaAgent` | Identifica contradições, divergências e informações incompatíveis. |
| `EvidenciaAgent` | Organiza evidências e relaciona documentos, fatos e alegações. |
| `PerguntasAgent` | Identifica lacunas e perguntas de investigação. |
| `RelatorioExecutivoAgent` | Consolida a análise documental em relatório executivo. |

### Equipe 2 — Análise Jurídica Especializada — 8 agentes

**Pergunta:** o que juridicamente pode ser concluído a partir do material analisado?

| Agente | Responsabilidade |
|---|---|
| `DocumentAgent` | Classificação documental e análise jurídica dos documentos relevantes. |
| `ProcessAgent` | Estratégia, situação processual, teses, controvérsias, forças e fragilidades. |
| `ContractAgent` | Contratos, obrigações, cláusulas, riscos, multas e inconsistências. |
| `DeadlineAgent` | Prazos, audiências, vencimentos e eventos críticos. |
| `EvidenceAgent` | Avaliação especializada da força e relação das evidências. |
| `LegalResearchAgent` | Pesquisa jurídica rastreável e fundamentação em fontes autorizadas. |
| `DraftingAgent` | Rascunhos de parecer, manifestação, petição, relatório e e-mail. |
| `SeniorLawyerAgent` | Revisão e consolidação das conclusões da equipe. |

### Equipe 3 — Inteligência Externa / Validação Processual — 8 agentes

**Pergunta:** o que as fontes processuais oficiais confirmam, complementam ou contradizem?

A equipe utiliza os dados oficiais retornados pelo DataJud e funciona como camada independente de validação factual/processual.

| Agente | Responsabilidade |
|---|---|
| `ProcessSearchAgent` | Confirma processo, tribunal, grau, classe, assunto e metadados processuais. |
| `MovementAgent` | Coleta e normaliza movimentações e eventos da linha do tempo externa. |
| `PartiesAgent` | Valida partes, polos, representantes e nomes disponíveis externamente. |
| `DecisionsAgent` | Identifica decisões e atos relevantes e os relaciona às conclusões internas. |
| `CourtAgent` | Valida tribunal, órgão julgador, grau, classe e competência disponível. |
| `TimelineAgent` | Reconcilia a linha do tempo documental com a linha do tempo oficial. |
| `ExternalEvidenceAgent` | Identifica fatos externos que confirmam ou contradizem as evidências internas. |
| `JusReconciliationAgent` | Consolida confirmações, conflitos, lacunas e divergências da equipe. |

### Fluxo das equipes

```text
PDF / Processo localizado
        |
        v
EQUIPE 1 — Análise Documental
        |
        v
EQUIPE 2 — Análise Jurídica Especializada
        |
        v
EQUIPE 3 — DataJud / Validação Externa
        |
        v
Relatório Final + RAG + Exportação PDF
```

A análise especializada é liberada depois da análise base e é disparada explicitamente pelo advogado. A Equipe 3 pode utilizar a consulta DataJud para confrontar os resultados das equipes anteriores com dados oficiais.

## Análise especializada

A análise especializada suporta configuração de:

- `parteRepresentada`;
- `contextoAdicional`;
- `pesquisaJuridica`;
- `consultaPesquisa`;
- `forcarProcesso`;
- `forcarContrato`;
- `rascunhos` (`PARECER`, `MANIFESTACAO`, `PETICAO`, `EMAIL_CLIENTE` e opções suportadas pela API).

Endpoint:

```text
POST /api/v1/processos/analises/{idDaAnaliseBase}/especializada
```

## DataJud — auditoria e enriquecimento

A documentação oficial está em:

https://www.cnj.jus.br/sistemas/datajud/api-publica/

A integração não substitui o PDF. Ela confronta o documento com dados públicos oficiais.

Principais capacidades:

1. Identificação automática de CNJ e resolução do tribunal.
2. Enriquecimento da capa com classe, tribunal, grau e órgão julgador.
3. Auditoria da linha do tempo.
4. Identificação de movimentações oficiais sem correspondente claro no PDF.
5. Linha do tempo híbrida PDF + DataJud.
6. Consultas por CNJ e CPF.
7. Consultas agregadas por tribunal e assunto/TPU.
8. Disparo do pipeline de análise a partir de processo encontrado.

A API pública não é tratada como fonte garantida de nomes completos das partes. O sistema evita declarar fraude apenas por divergência de nomes quando a fonte oficial não permite uma validação segura.

## Pesquisas DataJud

O frontend possui uma área **Pesquisas** para concentrar as buscas disponíveis na API.

Endpoints já disponíveis no backend:

```text
GET  /api/v1/datajud/processos/cnj
GET  /api/v1/datajud/processos/cpf
GET  /api/v1/datajud/processos/amostra
POST /api/v1/datajud/processos/cnj/processar
POST /api/v1/datajud/processos/cpf/processar
```

A próxima evolução desta área é permitir que cada processo retornado por uma pesquisa agregada seja selecionado e entre no mesmo fluxo de persistência e processamento do upload.

## Legal Research + TPU/CNJ

O `LegalResearchAgent` usa pesquisa jurídica rastreável e fontes autorizadas. Os metadados DataJud/TPU podem refinar a consulta por classe, assunto, tribunal, grau, fatos e pedidos.

O agente não deve inventar códigos TPU ou referências. Quando um metadado não estiver disponível, a lacuna deve ser preservada.

## RAG e chat

Após a análise, o processo pode ser indexado para briefing e chat ancorado no conteúdo.

Endpoints principais:

```text
GET  /api/v1/processos/analises/{id}/briefing
GET  /api/v1/processos/analises/{id}/briefing.md
POST /api/v1/processos/analises/{id}/chat
GET  /api/v1/processos/chats/{sessaoId}
GET  /api/v1/processos/analises/{id}/indice
```

## Relatório PDF

O relatório final é persistido no PostgreSQL como `bytea`.

O botão **Exportar** não recria o relatório no frontend. Ele solicita o PDF persistido no backend:

```text
GET /api/v1/processos/analises/{id}/relatorio-pdf
```

O processamento registra logs sobre:

- início da geração;
- nome do arquivo;
- tamanho em bytes;
- persistência;
- confirmação da gravação;
- conclusão do processo.

## Configuração

DataJud:

```yaml
legal-analyzer:
  data-jud:
    enabled: true
    api-key: ${DATAJUD_API_KEY:}
    base-url: ${DATAJUD_BASE_URL:https://api-publica.datajud.cnj.jus.br}
    timeout-seconds: ${DATAJUD_TIMEOUT_SECONDS:20}
```

Ollama:

```bash
export AI_PROVIDER="ollama"
export OLLAMA_MODEL="llama3.1:8b"
export OLLAMA_BASE_URL="http://localhost:11434/api/chat"
export OLLAMA_TIMEOUT_SECONDS="600"
```

PostgreSQL:

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/legal_analyzer"
export SPRING_DATASOURCE_USERNAME="postgres"
export SPRING_DATASOURCE_PASSWORD="admin"
```

Não grave chaves DataJud ou senhas no Git.

## Execução

Pré-requisitos:

- JDK 17+
- Maven 3.9+
- PostgreSQL
- Ollama quando `AI_PROVIDER=ollama`
- chave DataJud quando a integração estiver habilitada

```bash
mvn clean install
mvn spring-boot:run
```

A aplicação sobe, por padrão, em `http://localhost:8080`.

## Documentação adicional

- [`docs/EQUIPES-E-AGENTES.md`](docs/EQUIPES-E-AGENTES.md) — equipes, agentes, responsabilidades e regras arquiteturais.
- [`docs/AGENTES.md`](docs/AGENTES.md) — documentação dos agentes.

## Próximas evoluções

- Unificar completamente os resultados de pesquisas agregadas DataJud com persistência e pipeline normal.
- Expandir filtros de pesquisa por TPU.
- Refinar o Legal Research com classe/assunto TPU estruturados.
- Evoluir alertas críticos e monitoramento de prazos oficiais.
- Expandir auditoria de documentos e movimentações oficiais.

## Licença e responsabilidade

O projeto é destinado a apoio à análise e automação jurídica. A decisão profissional continua sendo responsabilidade do advogado ou profissional habilitado que utiliza a ferramenta.
