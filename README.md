# Legal Analyzer — Análise Jurídica de Processos com Agentes de IA

Backend em **Java 17 + Spring Boot 3** que recebe um PDF de processo/documentos
jurídicos, extrai o texto, e usa uma **pipeline de agentes de IA** (por padrão
**Ollama**, rodando um modelo local — nenhum documento sai da sua máquina) para
produzir uma análise estruturada completa.

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
`OllamaAiClient` (endpoint `POST /api/chat` do Ollama, `stream: false` e
`format: "json"`). A implementação `AnthropicAiClient` (Claude) continua no
projeto como alternativa: a escolha é feita pela propriedade
`legal-analyzer.ai.provider` (`ollama` — padrão — ou `anthropic`), via
`@ConditionalOnProperty`, sem que nenhum agente precise mudar.

## Estrutura do projeto

```
src/main/java/com/rafaelvianna/legalanalyzer/
├── LegalAnalyzerApplication.java
├── config/             AppProperties, AsyncConfig, WebConfig (CORS)
├── async/              Jobs em memória: análise base e análise especializada
├── web/                ProcessoAnaliseController, GlobalExceptionHandler
│   └── dto/            Records de request/response (partes, pedidos, decisões, ...)
├── pdf/                PdfTextExtractionService, PdfTextChunker
├── ai/                 AiClient, OllamaAiClient (padrão), AnthropicAiClient, AiJsonSupport
├── analysis/
│   ├── LegalAnalysisOrchestrator.java   (orquestra a pipeline completa)
│   ├── agents/          Um agente por tarefa (Extraction, Consolidation,
│   │                    Resumo, Inconsistencia, Evidencia, Perguntas,
│   │                    RelatorioExecutivo)
│   ├── specialized/     Análise especializada: SpecializedAnalysisOrchestrator
│   │   └── agents/      Process, Contract, Document, LegalResearch, Deadline,
│   │                    Evidence, Drafting e SeniorLawyer Agent
│   ├── research/        LegalSourceProvider + AllowlistLegalSourceProvider
│   │                    (pesquisa restrita a domínios autorizados)
│   └── prompts/         PromptTemplates e SpecializedPromptTemplates
└── exception/          Exceções de domínio (PDF inválido, arquivo grande, erro de IA)
```

## Pré-requisitos

- JDK 17+
- Maven 3.9+
- [Ollama](https://ollama.com) instalado e em execução, com um modelo baixado:

```bash
ollama serve                 # sobe o servidor em http://localhost:11434
ollama pull llama3.1:8b      # ou qwen2.5:14b, mistral-nemo, gemma2:27b, etc.
```

Recomenda-se um modelo com boa aderência a JSON e contexto grande, já que os
agentes recebem trechos longos de processos. Não há chave de API nem custo por
chamada — mas o desempenho depende da sua máquina (GPU/VRAM).

## Configuração

Variáveis de ambiente (todas com defaults sensatos em `application.yml`):

```bash
export AI_PROVIDER="ollama"                              # opcional (padrão: ollama)
export OLLAMA_MODEL="llama3.1:8b"                        # opcional
export OLLAMA_BASE_URL="http://localhost:11434/api/chat" # opcional
export OLLAMA_MAX_TOKENS="4096"                          # opcional (num_predict)
export OLLAMA_TEMPERATURE="0.2"                          # opcional
export OLLAMA_TIMEOUT_SECONDS="600"                      # opcional (modelo local é mais lento)
export OLLAMA_CONTEXT_WINDOW="16384"                     # opcional (num_ctx)
export OLLAMA_JSON_MODE="true"                           # opcional (format: "json")
export OLLAMA_KEEP_ALIVE="30m"                           # opcional (mantém o modelo carregado)
export OLLAMA_API_KEY=""                                 # só se houver proxy com bearer token
```

### Voltar para a Anthropic (Claude)

O `AnthropicAiClient` segue disponível; basta trocar o provedor e apontar as
mesmas propriedades genéricas de IA para a API da Anthropic:

```bash
export AI_PROVIDER="anthropic"
export OLLAMA_API_KEY="sk-ant-..."   # lida em legal-analyzer.ai.api-key
export OLLAMA_MODEL="claude-sonnet-4-5"
export OLLAMA_BASE_URL="https://api.anthropic.com/v1/messages"
```

### Notas de desempenho com modelo local

- `chunk-char-size` foi reduzido para **20000** caracteres (era 45000), porque
  modelos locais costumam ter janela de contexto menor que a do Claude. Se usar
  um modelo com contexto grande, aumente `chunk-char-size` e `context-window`
  juntos.
- `timeout-seconds` subiu para **600**: inferência local em CPU pode levar
  minutos por chamada, e a pipeline faz de 5 a N+5 chamadas.
- `keep-alive` evita recarregar o modelo em memória entre as chamadas da
  pipeline.

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
    "modeloIaUtilizado": "llama3.1:8b",
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

## Análise especializada (8 agentes)

Concluída a análise base, a resposta de `GET /api/v1/processos/analises/{id}`
passa a trazer o bloco `analiseEspecializada`, com o endpoint e as opções
disponíveis. É uma etapa **opcional**, executada por escolha do advogado.

| # | Agente | O que faz |
|---|--------|-----------|
| 1 | `ProcessAgent` | Analisa o processo completo: fase, teses, pontos controvertidos, forças, fragilidades, estratégia e prognóstico |
| 2 | `ContractAgent` | Analisa contratos: cláusulas de risco, obrigações, multas, prazos, condições e inconsistências |
| 3 | `DocumentAgent` | Classifica os documentos automaticamente (natureza do material + categoria de cada peça) |
| 4 | `LegalResearchAgent` | Pesquisa legislação/jurisprudência **somente em fontes autorizadas e rastreáveis**, apresentando as referências utilizadas |
| 5 | `DeadlineAgent` | Extrai datas e eventos importantes (prazos, audiências, vencimentos) |
| 6 | `EvidenceAgent` | Relaciona cada alegação com os documentos que podem sustentá-la e aponta lacunas probatórias |
| 7 | `DraftingAgent` | Gera rascunhos de parecer, manifestação, relatório, petição e e-mail ao cliente — **sempre para revisão do advogado** |
| 8 | `SeniorLawyerAgent` | Agente **orquestrador**: recebe o trabalho dos demais e produz o resultado final |

### Fluxo

```
Análise base concluída (AnaliseJob guarda texto extraído + resultado)
   │
   ▼
DocumentAgent  (classifica e decide o roteamento)
   │
   ├──► ProcessAgent    (se o material parecer processo, ou forcarProcesso=true)
   └──► ContractAgent   (se o material parecer contrato,  ou forcarContrato=true)
   │
   ▼
DeadlineAgent ──► EvidenceAgent ──► LegalResearchAgent (opcional)
   │
   ▼
DraftingAgent  (rascunhos solicitados, opcional)
   │
   ▼
SeniorLawyerAgent  ──►  AnaliseEspecializadaResponse (JSON)
```

A falha de um agente especialista não interrompe o fluxo: o erro entra em
`avisos` e o Senior Lawyer trata o item como lacuna.

### Endpoints

```bash
# 1. dispara a análise especializada sobre uma análise base já concluída
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
# -> 202 Accepted + { "id": "...", "status": "RECEBIDO", ... }

# 2. acompanha o progresso / obtém o resultado
curl http://localhost:8080/api/v1/processos/analises-especializadas/{id}
```

Campos do corpo (todos opcionais): `parteRepresentada`, `contextoAdicional`,
`pesquisaJuridica`, `consultaPesquisa` (se vazia, é derivada do caso),
`forcarProcesso`, `forcarContrato`, `rascunhos` (`PARECER`, `MANIFESTACAO`,
`RELATORIO`, `PETICAO`, `EMAIL_CLIENTE`).

O resultado (`AnaliseEspecializadaResponse`) traz `classificacaoDocumental`,
`analiseProcessual`, `analiseContratual`, `agendaPrazos`, `matrizEvidencias`,
`pesquisaJuridica`, `rascunhos`, `parecerSenior`, além de `agentesExecutados`
e `avisos`.

### Pesquisa jurídica: só fontes autorizadas e rastreáveis

O `LegalResearchAgent` nunca cita de memória. O conteúdo vem do
`AllowlistLegalSourceProvider`, e há três travas:

1. **Allowlist de domínios** — só URLs cujo host esteja em
   `legal-analyzer.legal-research.dominios-autorizados` são baixadas; a
   verificação é refeita **após cada redirecionamento**.
2. **O modelo só vê o texto baixado** — o prompt recebe apenas os trechos
   recuperados, com nome da fonte e URL.
3. **Validação da saída** — toda referência cuja URL não corresponda a uma
   das URLs efetivamente consultadas é **descartada**, e o descarte é
   registrado em `lacunas`. As referências mantidas vêm com `url`,
   `trechoRelevante` literal, `consultadoEm` e `verificada: true`.

A pesquisa vem **desabilitada por padrão** (`LEGAL_RESEARCH_ENABLED=false`).
Desligada, o agente responde `pesquisaRealizada: false` com o motivo — e
nenhuma legislação ou jurisprudência é citada. Fontes e domínios padrão
(LexML, STF, STJ, Planalto, CNJ, Senado, Câmara, DOU) ficam em
`application.yml`; para usar uma base própria, inclua o domínio na allowlist
e a `url-template` (com `{consulta}`) na lista de fontes.

### Rascunhos: revisão obrigatória

Todo rascunho sai com `avisoRevisao` explícito, `pontosDeAtencao` e
`lacunasParaPreencher`. O prompt proíbe inventar número de processo, vara,
valores, datas e fundamentos legais — nesses pontos o texto usa marcadores
`[CONFERIR]`, `[COMPLETAR]` e `[FUNDAMENTO A VALIDAR]`.

## Briefing de assunção do caso + chat com o processo (RAG)

O entregável desta etapa **não é "resuma este PDF"**. É responder à pergunta:

> "Explique este processo para um advogado que acabou de assumir o caso."

Depois da análise, o caso é indexado e passam a existir dois recursos: o
**briefing de assunção** (estruturado, previsível, com ponteiro de página) e um
**chat ancorado**, que só responde citando documento e página.

### Como o caso é indexado

O índice de cada caso combina duas fontes:

| Origem | Conteúdo | Citação gerada |
|---|---|---|
| `TEXTO_PROCESSO` | texto do PDF, **página por página** (`PdfTextExtractionService.extractPages`) | "Documento — página 42" |
| `FICHA_ANALISE` | fatos já apurados pelos agentes: partes, cronologia, pedidos, decisões, prazos, inconsistências, cláusulas de risco, matriz de evidências, parecer sênior | "Análise — cronologia" |

Indexar as fichas junto com o texto é o que permite ao chat responder
"quais são os pontos controvertidos?" sem depender de o modelo reler o
processo inteiro a cada pergunta.

**Busca híbrida** (`IndiceProcesso`): similaridade de cosseno sobre embeddings
do Ollama (`nomic-embed-text`, via `POST /api/embeddings`) somada a uma busca
léxica ponderada por IDF. A parte léxica é essencial no jurídico, onde a
pergunta traz o termo exato ("cláusula 7.2", "Documento 17", nome da parte).

Se o modelo de embeddings não estiver disponível, o sistema **registra o aviso
e continua em modo léxico** — a análise nunca falha por causa do RAG:

```bash
ollama pull nomic-embed-text     # habilita a busca semântica
# sem esse modelo, o índice funciona apenas em modo léxico
```

### Briefing de assunção

`GET /api/v1/processos/analises/{id}/briefing` (JSON)
`GET /api/v1/processos/analises/{id}/briefing.md` (Markdown pronto para a pasta do caso)

Estrutura fixa, na ordem em que o advogado lê:

1. **Processo** — numeração única CNJ extraída por regex do próprio documento
   (`0001234-56.2026.8.26.0000`); se não houver, "não identificado" — nunca um
   número inferido.
2. **Partes** — nome, papel e qualificação.
3. **Situação** — resumo executivo de ~1 página escrito para quem está
   assumindo o caso agora, mais "onde estamos", "o que está em jogo" e
   "próxima ação".
4. **Linha do tempo** — tabela `Data | Evento | Onde conferir`, em ordem
   cronológica, com as decisões incluídas.
5. **Pontos de atenção** — classificados: `CONTRADICAO`
   ("Documento X contradiz a petição inicial"), `ALEGACAO_SEM_DOCUMENTO`,
   `DECISAO_RELEVANTE`, `PRAZO_CRITICO`, `CLAUSULA_DE_RISCO`, `LACUNA`.
   Ordenados por gravidade.
6. **Evidências** — o rastro `Alegação → Documento 17 → página 42`.
7. **Perguntas para o advogado** — o contrato original está disponível? houve
   notificação extrajudicial? existe comprovante de pagamento? existe
   comunicação posterior ao evento? (mais as lacunas específicas apuradas
   pelos agentes).

**Somente a "situação" é escrita pelo modelo.** Tabelas, ponteiros de página,
classificação de pontos de atenção e perguntas são montados de forma
determinística a partir do que os agentes apuraram. Se o modelo falhar, o
briefing continua sendo entregue com o resumo da análise base e um aviso.

A página em "Onde conferir" e na coluna `Página` só aparece quando o termo foi
**efetivamente localizado no texto do PDF**. Quando não foi, o campo vem nulo e
o status é `NAO_LOCALIZADO_NO_PDF` — uma lacuna explícita vale mais que um
ponteiro inventado.

### Chat com o processo

```bash
# pergunta (sessaoId opcional; omita para iniciar uma conversa)
curl -X POST http://localhost:8080/api/v1/processos/analises/{id}/chat \
  -H "Content-Type: application/json" \
  -d '{"pergunta":"Existe comprovante de pagamento da terceira parcela?"}'
```

```json
{
  "sessaoId": "3f2a...",
  "resposta": "Sim. O Documento 17 comprova o pagamento da terceira parcela [T1], e o prazo de réplica vence em 20/05/2026 [T2].",
  "citacoes": [
    { "rotulo": "Documento — página 42", "pagina": 42, "origem": "TEXTO_PROCESSO", "trecho": "Documento 17 - comprovante..." },
    { "rotulo": "Análise — prazos", "pagina": null, "origem": "FICHA_ANALISE", "trecho": "20/05/2026: prazo para réplica..." }
  ],
  "fundamentada": true,
  "modoRecuperacao": "semantica+lexica",
  "perguntasSugeridas": ["Há comprovante das demais parcelas?"],
  "aviso": "Resposta de apoio gerada automaticamente..."
}
```

Travas contra citação falsa (`ValidadorAncoragem`):

- o modelo recebe os trechos numerados `[T1]...[Tn]` e é obrigado a citar o
  marcador de cada afirmação;
- marcadores que **não existem** no contexto recuperado são apagados da
  resposta;
- se não restar nenhum marcador válido, a resposta é **descartada** e
  substituída por "Não consta no material analisado.", com a indicação do que
  precisaria ser localizado nos autos;
- o prompt proíbe citar legislação, súmula, valor ou data fora dos trechos.

Outros endpoints:

```bash
# histórico da conversa (para recarregar a tela)
GET /api/v1/processos/chats/{sessaoId}

# diagnóstico do índice: passagens, páginas e se a busca semântica está ativa
GET /api/v1/processos/analises/{id}/indice
```

O índice é montado ao final da análise base e **reconstruído** quando a análise
especializada termina — a partir daí o chat também cita cláusulas de risco,
prazos detalhados e a matriz de evidências.

### Configuração do RAG

```yaml
legal-analyzer:
  rag:
    embeddings-habilitados: true      # RAG_EMBEDDINGS_ENABLED
    embedding-model: nomic-embed-text # RAG_EMBEDDING_MODEL
    embedding-base-url: http://localhost:11434/api/embeddings
    tamanho-passagem-chars: 1200      # tamanho de cada passagem indexada
    max-passagens-por-resposta: 8     # trechos no contexto de cada resposta
    score-minimo: 0.05                # corte de relevância
    max-mensagens-historico: 6        # histórico reenviado ao modelo
```

## Limitações e próximos passos (para uso em produção)

Este projeto é um ponto de partida sólido, mas antes de ir para produção com
documentos jurídicos reais, considere:

- **Persistência dos jobs**: as análises (base e especializada) rodam de forma
  assíncrona, mas o estado fica em memória (`ConcurrentHashMap`). Um restart
  perde os jobs em andamento e os resultados — para produção, mova para banco
  ou cache distribuído.
- **Armazenamento vetorial em memória**: o índice do RAG (`IndiceProcesso`)
  vive no processo e a busca é linear sobre as passagens. Funciona bem para
  processos individuais; para uma base com milhares de casos, migre para um
  banco vetorial (pgvector, Qdrant) mantendo a mesma interface
  `EmbeddingClient` + busca híbrida.
- **Sessões de chat em memória**: o histórico das conversas se perde no
  restart; persistir também serve de trilha de auditoria do que foi respondido
  ao advogado.
- **PDFs escaneados / sem camada de texto**: `PdfTextExtractionService`
  lança erro se não conseguir extrair texto. Adicionar OCR (ex.: Tesseract)
  como fallback é recomendado para digitalizações.
- **Segurança e sigilo profissional**: documentos jurídicos são sensíveis
  (segredo de justiça, dados pessoais). Rodar o modelo localmente via Ollama
  resolve a parte de não enviar o documento a terceiros, mas ainda é
  recomendado adicionar autenticação/autorização (ex.: Spring Security +
  OAuth2) e criptografia em trânsito/repouso.
- **Persistência**: atualmente nada é salvo — a resposta é devolvida e
  descartada. Se quiser histórico/auditoria, adicione um banco de dados
  (ex.: PostgreSQL) e uma tabela de análises.
- **Capacidade e concorrência**: cada análise dispara de 5 a N+5 chamadas ao
  modelo (N = número de chunks). Com Ollama não há custo por token, mas há
  um gargalo de GPU/CPU — implemente uma fila, retry/backoff e limite de
  análises simultâneas para uso em escala.
- **Qualidade do modelo**: modelos locais menores erram mais em extração
  estruturada que modelos de fronteira. Vale testar 2-3 modelos
  (`llama3.1:8b`, `qwen2.5:14b`, `gemma2:27b`) com processos reais antes de
  fixar um.
- **Validação jurídica**: as respostas dos agentes são geradas por IA e
  podem conter erros ou omissões — este sistema é uma ferramenta de apoio,
  não substitui a revisão de um advogado.

## Testes

```bash
mvn test
```

Cobertura atual:

- `PdfTextChunkerTest` — divisão de texto com sobreposição.
- `IndiceProcessoTest` — recuperação em modo léxico (sem embeddings),
  normalização de acentos, ordenação por cosseno e rastreio de página
  (inclusive a garantia de **não** apontar página para termo inexistente).
- `ValidadorAncoragemTest` — a trava contra citação falsa: marcador inventado
  é removido e resposta sem citação válida é rebaixada para não fundamentada.
- `BriefingAssuncaoServiceTest` — montagem do dossiê completo, ordem
  cronológica, alegação sem documento virando ponto de atenção, ausência de
  página quando o documento não é localizado e entrega do briefing mesmo com o
  modelo fora do ar.
- `OllamaAiClientTest` — servidor HTTP local simulando o `/api/chat` do Ollama:
  payload enviado e tratamento de erros, sem precisar de modelo carregado.
- `AiProviderSelectionTest` / `AnthropicProviderSelectionTest` — seleção do
  provedor via `legal-analyzer.ai.provider`.
- `AllowlistLegalSourceProviderTest` — allowlist de domínios, recusa de host
  não autorizado, limpeza de HTML, truncamento e limite de fontes.
- `SpecializedAnalysisOrchestratorTest` — roteamento entre Process e Contract
  Agent, `forcarContrato`, pesquisa desabilitada sem citações, descarte de
  referência com URL não rastreável, aviso de revisão nos rascunhos e
  isolamento de falha de agente.
