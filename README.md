# Legal Analyzer — Análise Jurídica de Processos com Agentes de IA

Backend em **Java 17 + Spring Boot 3** que recebe um PDF de processo/documentos
jurídicos, extrai o texto, e usa uma **pipeline de agentes de IA** (Claude, via
API da Anthropic) para produzir uma análise estruturada completa.

## O que o sistema faz

Endpoint `POST /api/v1/processos/analisar` (multipart, campo `arquivo`):

| # | Tarefa | Onde é feita |
|---|--------|--------------|
| 1 | Lê os documentos | `PdfTextExtractionService` (Apache PDFBox) |
| 2 | Identifica as partes | `ExtractionAgent` |
| 3 | Identifica a cronologia | `ExtractionAgent` |
| 4 | Identifica os pedidos | `ExtractionAgent` |
| 5 | Identifica as decisões | `ExtractionAgent` |
| 6 | Identifica prazos/datas relevantes | `ExtractionAgent` |
| 7 | Identifica documentos importantes | `ExtractionAgent` |
| 8 | Resume o processo | `ResumoAgent` |
| 9 | Aponta inconsistências | `InconsistenciaAgent` |
| 10 | Organiza evidências | `EvidenciaAgent` |
| 11 | Gera perguntas de investigação | `PerguntasAgent` |
| 12 | Produz relatório executivo | `RelatorioExecutivoAgent` |

Quando um processo é grande, o texto é dividido em vários trechos (chunks);
cada trecho passa pelo `ExtractionAgent` (tarefas 2-7) e os resultados
parciais são então unificados pelo `ConsolidationAgent` (dedup + ordenação
cronológica) antes de seguir para as tarefas 8-12, que trabalham sobre os
dados já consolidados.

## Arquitetura

```
PDF (multipart) 
   │
   ▼
PdfTextExtractionService  (PDFBox: PDF -> texto puro)
   │
   ▼
PdfTextChunker  (divide texto grande em trechos com overlap)
   │
   ▼
ExtractionAgent × N trechos   ──►  ConsolidationAgent (se N > 1)
                                          │
                     ┌────────────────────┼───────────────────────┐
                     ▼                    ▼                       ▼
               ResumoAgent      InconsistenciaAgent        EvidenciaAgent
                     │                    │                       │
                     └─────────┬──────────┴───────────┬───────────┘
                                ▼                      ▼
                         PerguntasAgent      RelatorioExecutivoAgent
                                │                      │
                                └──────────┬───────────┘
                                           ▼
                             AnaliseProcessoResponse (JSON)
```

Toda a comunicação com o modelo é feita através da interface `AiClient`
(`com.rafaelvianna.legalanalyzer.ai`), implementada por padrão por
`AnthropicAiClient` (API de Mensagens da Anthropic/Claude). Trocar de
provedor de IA (OpenAI, Azure OpenAI, um modelo self-hosted, etc.) significa
apenas criar uma nova implementação de `AiClient` — nenhum agente precisa
mudar.

## Estrutura do projeto

```
src/main/java/com/rafaelvianna/legalanalyzer/
├── LegalAnalyzerApplication.java
├── config/            AppProperties, WebConfig (CORS)
├── web/                ProcessoAnaliseController, GlobalExceptionHandler
│   └── dto/            Records de request/response (partes, pedidos, decisões, ...)
├── pdf/                PdfTextExtractionService, PdfTextChunker
├── ai/                 AiClient, AnthropicAiClient, AiJsonSupport
├── analysis/
│   ├── LegalAnalysisOrchestrator.java   (orquestra a pipeline completa)
│   ├── agents/          Um agente por tarefa (Extraction, Consolidation,
│   │                    Resumo, Inconsistencia, Evidencia, Perguntas,
│   │                    RelatorioExecutivo)
│   └── prompts/         PromptTemplates.java (todos os prompts em um só lugar)
└── exception/          Exceções de domínio (PDF inválido, arquivo grande, erro de IA)
```

## Pré-requisitos

- JDK 17+
- Maven 3.9+
- Uma chave de API da Anthropic (`ANTHROPIC_API_KEY`) — ou substitua
  `AnthropicAiClient` por outra implementação de `AiClient` para usar outro
  provedor de IA.

## Configuração

Variáveis de ambiente (todas com defaults sensatos em `application.yml`):

```bash
export ANTHROPIC_API_KEY="sk-ant-..."          # obrigatória
export ANTHROPIC_MODEL="claude-sonnet-5"        # opcional
export ANTHROPIC_BASE_URL="https://api.anthropic.com/v1/messages"  # opcional
export ANTHROPIC_MAX_TOKENS="4096"              # opcional
export ANTHROPIC_TEMPERATURE="0.2"              # opcional
export ANTHROPIC_TIMEOUT_SECONDS="120"          # opcional
```

Outros parâmetros (tamanho máximo de PDF, tamanho de chunk, overlap) ficam em
`src/main/resources/application.yml`, sob `legal-analyzer.pdf`.

## Executando

```bash
cd legal-analyzer
mvn clean install
mvn spring-boot:run
```

A aplicação sobe em `http://localhost:8080`.

## Exemplo de uso

```bash
curl -X POST http://localhost:8080/api/v1/processos/analisar \
  -F "arquivo=@/caminho/para/processo.pdf"
```

Resposta (resumida — todos os campos são retornados de fato):

```json
{
  "metadata": {
    "nomeArquivo": "processo.pdf",
    "quantidadeCaracteresExtraidos": 138422,
    "quantidadeTrechosProcessados": 4,
    "modeloIaUtilizado": "claude-sonnet-5",
    "dataProcessamento": "2026-08-12T14:32:10Z"
  },
  "partes": [
    { "nome": "João da Silva", "papel": "autor", "qualificacao": "...", "observacoes": "" }
  ],
  "cronologia": [
    { "data": "2023-04-10", "descricaoEvento": "Distribuição da petição inicial", "fase": "conhecimento" }
  ],
  "pedidos": [ { "descricaoPedido": "...", "parteRequerente": "...", "fundamentoLegal": "...", "status": "pendente" } ],
  "decisoes": [ { "data": "2023-09-02", "tipoDecisao": "sentença", "resumoDecisao": "...", "autoridade": "...", "efeitos": "..." } ],
  "prazos": [ { "data": "2026-09-15", "descricaoPrazo": "Prazo para recurso", "criticidade": "alta", "parteResponsavel": "réu" } ],
  "documentosImportantes": [ { "nomeDocumento": "Laudo pericial", "tipo": "prova técnica", "dataDocumento": "2023-07-01", "relevancia": "alta" } ],
  "resumoProcesso": "Trata-se de ação de...",
  "inconsistencias": [ { "descricao": "...", "elementosConflitantes": "...", "gravidade": "média", "recomendacao": "..." } ],
  "gruposEvidencia": [ { "categoria": "Prova documental", "documentos": ["Laudo pericial"], "relevanciaProbatoria": "alta", "observacoes": "" } ],
  "perguntasInvestigacao": [ "O laudo pericial foi contestado por assistente técnico da parte contrária?" ],
  "relatorioExecutivo": {
    "titulo": "Relatório Executivo — Processo X",
    "visaoGeral": "...",
    "pontosCriticos": ["..."],
    "recomendacoes": ["..."],
    "proximosPassos": ["..."],
    "conclusao": "..."
  }
}
```

## Limitações e próximos passos (para uso em produção)

Este projeto é um ponto de partida sólido, mas antes de ir para produção com
documentos jurídicos reais, considere:

- **Processamento assíncrono**: hoje o endpoint é síncrono e pode demorar
  (vários chunks × várias chamadas de IA). Para processos muito longos, vale
  migrar para um padrão de job assíncrono (`POST /analisar` retorna um ID,
  `GET /processos/{id}` consulta o status/resultado).
- **PDFs escaneados / sem camada de texto**: `PdfTextExtractionService`
  lança erro se não conseguir extrair texto. Adicionar OCR (ex.: Tesseract)
  como fallback é recomendado para digitalizações.
- **Segurança e sigilo profissional**: documentos jurídicos são sensíveis
  (segredo de justiça, dados pessoais). Adicione autenticação/autorização
  (ex.: Spring Security + OAuth2), criptografia em trânsito/repouso, e
  avalie os termos de retenção de dados do provedor de IA antes de enviar
  documentos reais.
- **Persistência**: atualmente nada é salvo — a resposta é devolvida e
  descartada. Se quiser histórico/auditoria, adicione um banco de dados
  (ex.: PostgreSQL) e uma tabela de análises.
- **Custo e limites de taxa**: cada análise dispara de 5 a N+5 chamadas ao
  modelo de IA (N = número de chunks). Monitore custo e implemente
  retry/backoff e rate limiting para uso em escala.
- **Validação jurídica**: as respostas dos agentes são geradas por IA e
  podem conter erros ou omissões — este sistema é uma ferramenta de apoio,
  não substitui a revisão de um advogado.

## Testes

```bash
mvn test
```

Inclui um teste unitário de exemplo para `PdfTextChunker`. Recomenda-se
complementar com testes de integração usando um `AiClient` "fake"
(implementação de teste) para validar a orquestração sem custo de chamadas
reais de IA.
