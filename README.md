# Legal Analyzer — Análise Jurídica de Processos com Agentes de IA

Backend em **Java 17 + Spring Boot 3** que recebe PDFs de processos/documentos jurídicos, extrai e estrutura o conteúdo e executa uma pipeline assíncrona de agentes de IA. O provedor padrão é o **Ollama**, permitindo processamento local sem enviar o documento para terceiros.

O projeto combina três camadas de inteligência:

1. **Análise documental** — entende o que está dentro do PDF.
2. **Análise especializada** — agentes jurídicos trabalham sobre o caso já estruturado.
3. **Auditoria externa** — o **DataJud/CNJ** confronta o processo com metadados e movimentações oficiais disponíveis publicamente.

> **Importante:** o Legal Analyzer é uma ferramenta de apoio à análise jurídica. Os resultados gerados por IA e os dados públicos devem ser revisados por profissional habilitado.

## O que o sistema faz

Endpoint principal: `POST /api/v1/processos/analisar` (multipart, campo `arquivo`).

| # | Tarefa | Componente |
|---|---|---|
| 1 | Extrai o texto do PDF | `PdfTextExtractionService` / PDFBox |
| 2 | Divide processos grandes em chunks | `PdfTextChunker` |
| 3 | Identifica partes | `ExtractionAgent` |
| 4 | Identifica cronologia | `ExtractionAgent` |
| 5 | Identifica pedidos | `ExtractionAgent` |
| 6 | Identifica decisões | `ExtractionAgent` |
| 7 | Identifica prazos e datas | `ExtractionAgent` |
| 8 | Identifica documentos relevantes | `ExtractionAgent` |
| 9 | Consolida resultados de múltiplos chunks | `ConsolidationAgent` |
| 10 | Resume o processo | `ResumoAgent` |
| 11 | Detecta inconsistências | `InconsistenciaAgent` |
| 12 | Organiza evidências | `EvidenciaAgent` |
| 13 | Gera perguntas de investigação | `PerguntasAgent` |
| 14 | Produz relatório executivo | `RelatorioExecutivoAgent` |
| 15 | Enriquece a capa com dados públicos | `DataJudService` / `DataJudAuditoria` |
| 16 | Sincroniza a linha do tempo com movimentações oficiais | `DataJudAuditoria` |

Quando o processo é grande, os resultados dos chunks são consolidados antes das etapas finais. A análise base é assíncrona e o frontend pode acompanhar o progresso por ID.

## Arquitetura

```text
PDF
 │
 ▼
PdfTextExtractionService
 │
 ▼
PdfTextChunker
 │
 ▼
ExtractionAgent × N
 │
 ▼
ConsolidationAgent
 │
 ├──► ResumoAgent
 ├──► InconsistenciaAgent
 ├──► EvidenciaAgent
 ├──► PerguntasAgent
 └──► RelatorioExecutivoAgent
          │
          ├──────────────► DataJudService
          │                     │
          │                     ▼
          │               DataJudAuditoria
          │                     │
          │                     ├── enriquecimento da capa
          │                     └── auditoria da timeline
          │
          ▼
AnaliseProcessoResponse
          │
          ▼
Análise especializada opcional
          │
          ├── ProcessAgent
          ├── ContractAgent
          ├── DocumentAgent
          ├── DeadlineAgent
          ├── EvidenceAgent
          ├── LegalResearchAgent
          ├── DraftingAgent
          └── SeniorLawyerAgent
```

Toda comunicação com IA passa pela interface `AiClient`. O projeto possui `OllamaAiClient` como implementação padrão e `AnthropicAiClient` como alternativa configurável.

## Estrutura do projeto

```text
src/main/java/com/rafaelvianna/legalanalyzer/
├── ai/                 Clientes de IA e suporte JSON
├── async/              Jobs assíncronos da análise base/especializada
├── config/             Configuração, propriedades e CORS
├── datajud/            Integração e auditoria DataJud/CNJ
├── pdf/                Extração e chunking de PDFs
├── rag/                Índice e chat ancorado no processo
├── analysis/
│   ├── agents/         Agentes da análise base
│   ├── specialized/    Orquestração e agentes especializados
│   ├── research/       Pesquisa jurídica com allowlist
│   └── prompts/        Prompts dos agentes
├── web/                Controllers e DTOs
└── exception/          Exceções de domínio
```

## Análise especializada — 8 agentes

A análise especializada só é liberada depois da conclusão da análise base e é disparada explicitamente pelo advogado.

| Agente | Responsabilidade |
|---|---|
| `ProcessAgent` | Fase processual, teses, controvérsias, forças, fragilidades, estratégia e prognóstico |
| `ContractAgent` | Cláusulas de risco, obrigações, multas, prazos, condições e inconsistências |
| `DocumentAgent` | Classificação documental e roteamento do caso |
| `LegalResearchAgent` | Pesquisa jurídica rastreável e baseada em fontes autorizadas |
| `DeadlineAgent` | Prazos, audiências, vencimentos e eventos críticos |
| `EvidenceAgent` | Relação entre alegações, provas e lacunas probatórias |
| `DraftingAgent` | Rascunhos de parecer, manifestação, petição, relatório e e-mail |
| `SeniorLawyerAgent` | Consolidação e parecer final dos agentes especializados |

Fluxo:

```text
Análise base concluída
        │
        ▼
DocumentAgent
   ┌────┴────┐
   ▼         ▼
Process   Contract
   └────┬────┘
        ▼
Deadline → Evidence → LegalResearch (opcional)
                         │
                         ▼
                  Drafting (opcional)
                         │
                         ▼
                 SeniorLawyerAgent
```

### Endpoint

```bash
curl -X POST http://localhost:8080/api/v1/processos/analises/{idDaAnaliseBase}/especializada \
  -H "Content-Type: application/json" \
  -d '{
    "parteRepresentada": "Construtora Alfa Ltda",
    "contextoAdicional": "Defendemos a ré; objetivo é reduzir a multa contratual.",
    "pesquisaJuridica": true,
    "consultaPesquisa": "onus da prova em acao de cobranca CPC art. 373",
    "forcarContrato": true,
    "rascunhos": ["PARECER", "EMAIL_CLIENTE"]
  }'
```

Todos os campos são opcionais: `parteRepresentada`, `contextoAdicional`, `pesquisaJuridica`, `consultaPesquisa`, `forcarProcesso`, `forcarContrato` e `rascunhos`.

## Integração DataJud — auditoria oficial do processo

O Legal Analyzer possui uma camada específica para integração com a **API Pública do DataJud do Conselho Nacional de Justiça (CNJ)**.

A documentação oficial da API está disponível em:

https://www.cnj.jus.br/sistemas/datajud/api-publica/

A integração não substitui o conteúdo do PDF. Ela funciona como uma **camada de auditoria e enriquecimento**, permitindo comparar o que o documento informa com o que está disponível na base pública do Poder Judiciário.

### O que já está implementado

#### 1. Identificação automática do processo

O sistema identifica uma numeração CNJ no documento. Quando encontrada, a integração determina o tribunal a partir da numeração e consulta o endpoint público correspondente.

Exemplo:

```text
0001234-56.2026.8.26.0000
              │  │
              │  └── código do tribunal/foro
              └──── justiça estadual
```

A integração possui mapeamento para tribunais estaduais e também trata códigos de Justiça Federal, Justiça do Trabalho, STF e STJ quando suportados pela configuração atual.

#### 2. Auditoria e enriquecimento da capa

Quando o processo é localizado, os metadados públicos retornados podem enriquecer a análise com:

- tribunal;
- grau de jurisdição;
- classe processual oficial;
- órgão julgador;
- quantidade de movimentações;
- última movimentação;
- movimentações oficiais e seus complementos.

O resultado é representado por `DataJudInfo` e `DataJudAuditoria`.

A resposta diferencia claramente:

- `ENCONTRADO`;
- `NAO_ENCONTRADO`;
- `NAO_CONFIGURADO`;
- `NUMERO_NAO_IDENTIFICADO`;
- `INDISPONIVEL`;
- `AGUARDANDO`.

Isso evita que uma falha ou ausência no DataJud seja interpretada como inexistência do processo.

### Validação das partes — limitação importante

A API Pública do DataJud não deve ser tratada como fonte pública de nomes completos das partes. Por isso, a implementação atual **não declara divergência de nomes como fraude** com base apenas no DataJud.

A auditoria registra explicitamente quando a validação de partes não pode ser concluída pela API pública. Essa decisão evita falso positivo e preserva o significado jurídico da evidência.

A evolução planejada é permitir validação contra uma fonte oficial adequada quando houver base jurídica e técnica para isso.

## Sincronização da linha do tempo e movimentações ocultas

O `DataJudAuditoria` compara a cronologia extraída do PDF com as movimentações oficiais retornadas pelo DataJud.

Para cada movimentação oficial, o sistema tenta localizar uma correspondência:

```text
DataJud
  │
  ├── data
  ├── nome da movimentação
  └── complementos
       │
       ▼
PDF / cronologia extraída
       │
       ├── corresponde
       │      └── CORRESPONDENTE_NO_PDF
       │
       └── não corresponde
              └── NAO_ENCONTRADA_NO_PDF
```

O resultado produz uma **linha do tempo híbrida**, combinando:

- eventos extraídos do PDF;
- movimentações oficiais do DataJud;
- indicação da origem (`PDF` ou `DATAJUD`);
- status da correspondência;
- indicação de eventos oficiais sem correspondente claro no documento;
- data de publicação, quando identificada;
- data de trânsito em julgado, quando identificada.

### Importante sobre movimentações ocultas

O alerta:

> **Movimentação oficial sem correspondente no PDF**

significa uma **lacuna de correspondência**, e não prova isoladamente que o processo esteja incompleto. O advogado deve conferir o processo oficial antes de concluir que existe documento faltante.

## Legal Research Agent + TPU/CNJ

O `LegalResearchAgent` foi projetado para pesquisar legislação e jurisprudência somente em fontes autorizadas e rastreáveis.

A próxima camada de precisão da pesquisa utiliza os metadados processuais padronizados pelo CNJ, especialmente:

- classe processual;
- assuntos processuais;
- códigos TPU;
- tribunal/grau;
- movimentações relevantes;
- fatos e pedidos extraídos do PDF.

A ideia é transformar uma pesquisa genérica como:

```text
"indenização dano moral"
```

em uma consulta contextualizada:

```text
Classe TPU + assunto TPU + tribunal + fato controvertido + fundamento legal
```

Isso reduz ruído e aumenta a precisão da pesquisa jurídica.

### Estado atual

A integração DataJud já fornece `classeProcessual`, tribunal, grau, órgão julgador e movimentações para a análise. A utilização desses metadados como filtros estruturados do `LegalResearchAgent` deve permanecer explícita e rastreável: o agente não deve inventar códigos TPU nem transformar uma classificação ausente em fato.

Quando um código/assunto TPU não estiver disponível, a pesquisa deve continuar usando os dados efetivamente extraídos, registrando a lacuna.

### Regras de rastreabilidade

O `LegalResearchAgent` não deve citar legislação ou jurisprudência de memória. A pesquisa segue três princípios:

1. **Allowlist de fontes** — somente domínios autorizados são consultados.
2. **Contexto verificável** — o modelo recebe o conteúdo efetivamente recuperado.
3. **Validação da referência** — referências não correspondentes às fontes consultadas são descartadas e registradas como lacuna.

A pesquisa é desabilitada por padrão quando `LEGAL_RESEARCH_ENABLED=false`.

## RAG e chat com o processo

Depois da análise, o processo pode ser indexado para permitir briefing de assunção e chat ancorado no conteúdo.

O índice combina:

- texto do PDF, página por página;
- ficha estruturada da análise;
- embeddings do Ollama quando disponíveis;
- busca lexical ponderada por IDF.

O chat é obrigado a ancorar as respostas em trechos recuperados e páginas do documento. Marcadores de citação inventados são removidos.

Endpoints principais:

```text
GET  /api/v1/processos/analises/{id}/briefing
GET  /api/v1/processos/analises/{id}/briefing.md
POST /api/v1/processos/analises/{id}/chat
GET  /api/v1/processos/chats/{sessaoId}
GET  /api/v1/processos/analises/{id}/indice
```

## Configuração

Principais variáveis do Ollama:

```bash
export AI_PROVIDER="ollama"
export OLLAMA_MODEL="llama3.1:8b"
export OLLAMA_BASE_URL="http://localhost:11434/api/chat"
export OLLAMA_MAX_TOKENS="4096"
export OLLAMA_TEMPERATURE="0.2"
export OLLAMA_TIMEOUT_SECONDS="600"
export OLLAMA_CONTEXT_WINDOW="16384"
export OLLAMA_JSON_MODE="true"
export OLLAMA_KEEP_ALIVE="30m"
```

Configuração DataJud:

```yaml
legal-analyzer:
  data-jud:
    enabled: true
    api-key: ${DATAJUD_API_KEY:}
    base-url: ${DATAJUD_BASE_URL:https://api-publica.datajud.cnj.jus.br}
    timeout-seconds: ${DATAJUD_TIMEOUT_SECONDS:20}
```

Use a chave fornecida pelo CNJ/DataJud através de variável de ambiente. Não grave credenciais no Git.

## Execução

Pré-requisitos:

- JDK 17+
- Maven 3.9+
- Ollama em execução quando `AI_PROVIDER=ollama`
- chave DataJud configurada quando a integração estiver habilitada

```bash
mvn clean install
mvn spring-boot:run
```

A aplicação sobe, por padrão, em `http://localhost:8080`.

Teste rápido:

```bash
curl -X POST http://localhost:8080/api/v1/processos/analisar \
  -F "arquivo=@/caminho/para/processo.pdf"
```

## Performance com Ollama local

Modelos locais podem ser o principal gargalo do sistema, especialmente em CPU. Processos grandes podem gerar dezenas ou centenas de chamadas de inferência.

Recomendações:

- utilizar modelo com boa aderência a JSON;
- manter `keep-alive` habilitado;
- ajustar `chunk-char-size` de acordo com a memória disponível;
- configurar timeout compatível com inferência local;
- limitar análises simultâneas;
- considerar GPU/VRAM para produção.

O sistema deve continuar tratando timeout/indisponibilidade do DataJud como uma falha isolada da camada de auditoria, sem destruir a análise documental.

## Persistência e produção

Atualmente os jobs e o índice podem operar em memória. Para produção, os próximos passos naturais são:

- PostgreSQL para análises, jobs, auditoria e histórico;
- armazenamento persistente dos PDFs;
- pgvector ou Qdrant para escala do RAG;
- fila de processamento;
- retry/backoff para IA e DataJud;
- autenticação/autorização;
- criptografia em trânsito e repouso;
- trilha de auditoria;
- OCR para PDFs digitalizados;
- observabilidade e métricas.

## Testes

```bash
mvn test
```

A suíte cobre componentes de extração/chunking, RAG, ancoragem, clientes de IA, seleção de provedor, pesquisa jurídica e orquestração especializada. A integração DataJud deve possuir testes específicos para resolução de tribunal, tratamento de respostas, auditoria de timeline e indisponibilidade da API.

## Roadmap jurídico

```text
[OK] Análise documental com agentes
[OK] Análise especializada opcional
[OK] RAG + chat ancorado
[OK] Integração DataJud
[OK] Enriquecimento de capa com metadados públicos
[OK] Auditoria da timeline contra movimentações oficiais
[OK] Identificação de movimentações oficiais sem correspondência clara no PDF
[PRÓXIMO] Alimentar LegalResearchAgent com metadados TPU/DataJud
[PRÓXIMO] Refinar pesquisa por classe + assunto + tribunal + fato
[PRÓXIMO] Persistência PostgreSQL
[PRÓXIMO] Auditoria completa e histórico de consultas
```

## Aviso jurídico

O Legal Analyzer é software de apoio à análise. Nenhuma saída de IA deve ser considerada, isoladamente, uma conclusão jurídica definitiva. Dados do DataJud representam a informação disponibilizada pela API pública no momento da consulta e podem estar sujeitos a limitações, atrasos, indisponibilidade ou restrições de publicidade.