# Equipe 3 — Inteligência Externa / Validação Processual

## Objetivo

A Equipe 3 é uma camada independente de validação processual. Ela não deve simplesmente repetir a leitura do PDF. Sua função é confrontar o que as Equipes 1 e 2 concluíram com dados públicos oficiais disponíveis no DataJud/CNJ e produzir confirmações, informações adicionais, divergências e lacunas.

## Regra principal

Nenhum agente pode declarar que uma informação é verdadeira apenas porque ela veio de uma consulta externa. O resultado externo precisa manter origem, data da consulta e contexto para posterior reconciliação.

## Mapa da integração atual

A integração existente está centralizada em `DataJudService`.

Fluxo atual:

```text
número CNJ
   |
   v
resolverTribunal()
   |
   +-- Justiça Estadual -> tjac ... tjto
   +-- Justiça Federal -> trf1 ... trf6
   +-- Justiça do Trabalho -> trt1 ... trt24
   +-- STF / STJ
   |
   v
<baseUrl>/api_publica_<tribunal>/_search
   |
   +-- Authorization: APIKey <chave configurada>
   +-- POST
   +-- match(numeroProcesso)
   |
   v
DataJudInfo
   |
   +-- status
   +-- tribunal
   +-- endpoint
   +-- encontrado
   +-- quantidadeMovimentos
   +-- ultimaMovimentacao
   +-- classeProcessual
   +-- orgaoJulgador
   +-- grau
   +-- consultadoEm
   +-- movimentos[]
```

A implementação atual consulta um processo pelo número CNJ e extrai do primeiro hit informações de classe, órgão julgador, grau e movimentações. O código existente também preserva o endpoint e o horário da consulta para auditoria.

## Endpoint efetivamente implementado

| Operação | Endpoint | Agente | Estado |
|---|---|---|---|
| Buscar processo por CNJ | `/api_publica_<tribunal>/_search` | `ProcessSearchAgent` | Implementado |
| Extrair movimentações | mesmo payload `_search` | `MovementAgent` | Implementado |
| Metadados do tribunal/órgão/grau/classe | mesmo payload `_search` | `CourtAgent` | Implementado |
| Linha do tempo externa | derivada de `movimentos[]` | `TimelineAgent` | Implementado |
| Evidência externa | derivada dos dados retornados | `ExternalEvidenceAgent` | Base inicial |
| Partes | endpoint/campos adicionais necessários | `PartiesAgent` | Preparado, não inventado |
| Decisões | endpoint/campos adicionais necessários | `DecisionsAgent` | Preparado, não inventado |
| Reconciliação | resultados dos 7 agentes | `JusReconciliationAgent` | Implementado como primeira camada |

## Por que não inventamos endpoints

A API pública do DataJud pode possuir diferentes conjuntos de dados, índices e capacidades conforme a fonte. O contrato que realmente conhecemos no código é o `_search` usado pelo `DataJudService`. Por isso, a primeira implementação da Equipe 3 reutiliza apenas campos comprovadamente disponíveis no payload atual.

Quando forem adicionados endpoints/campos para partes e decisões, eles devem ser incorporados ao `DataJudService` ou a uma camada cliente especializada, e não acessados diretamente pelos agentes.

## Agentes

### 1. ProcessSearchAgent

Responsável pela consulta inicial e identificação do processo.

Entrada: número CNJ.

Saída: `DataJudInfo` e `ExternalValidationResult`.

### 2. MovementAgent

Responsável por normalizar os eventos públicos retornados pelo processo.

Não consulta novamente a API. Reutiliza o resultado do `ProcessSearchAgent`, evitando chamadas duplicadas.

### 3. PartiesAgent

Responsável pela validação de partes.

Atualmente está preparado como contrato de equipe, mas não infere partes de campos inexistentes no modelo. A implementação definitiva depende do mapeamento dos campos/endpoints oficiais correspondentes.

### 4. DecisionsAgent

Responsável por decisões e atos relevantes.

Atualmente está preparado sem fabricar resultados. Será conectado quando o payload oficial correspondente estiver mapeado.

### 5. CourtAgent

Valida metadados processuais já disponíveis: tribunal, órgão julgador, grau e classe.

### 6. TimelineAgent

Cria a linha do tempo externa a partir das movimentações públicas, ordenando-as por data quando possível.

### 7. ExternalEvidenceAgent

Transforma dados externos em evidências auditáveis. Não marca uma evidência como confirmada sem comparação com o contexto interno.

### 8. JusReconciliationAgent

É a reunião da equipe. Recebe os resultados dos sete agentes anteriores e produz uma visão consolidada. Na próxima evolução, deverá receber também as claims/contexto das Equipes 1 e 2 para classificar:

- confirmado;
- conflito;
- informação nova;
- não verificável;
- fonte indisponível.

## Execução

`ExternalValidationTeam` é o orquestrador da equipe.

Ele executa uma única consulta externa para obter `DataJudInfo` e distribui esse resultado aos agentes que dependem dele. Isso evita que cada agente faça sua própria chamada HTTP.

```text
ExternalValidationTeam
       |
       +--> ProcessSearchAgent --> DataJudInfo
                                    |
              +---------------------+----------------------+
              |          |          |          |           |
          Movement   Parties   Decisions    Court      Timeline
              |          |          |          |           |
              +----------+----------+----------+-----------+
                                    |
                         ExternalEvidenceAgent
                                    |
                                    v
                         JusReconciliationAgent
```

## Execução futura em paralelo

A Equipe 3 deve iniciar assim que a Equipe 1 identificar um número CNJ confiável, podendo trabalhar em paralelo com a Equipe 2.

```text
Equipe 1
   |
   +---- CNJ identificado ----+
   |                           |
   v                           v
Equipe 2                   Equipe 3
Análise jurídica           DataJud / Jus
   |                           |
   +-------------+-------------+
                 v
        Reconciliação global
```

## Contexto entre equipes

A próxima evolução deverá introduzir um contexto estruturado compartilhado, contendo pelo menos:

- identificação do processo;
- partes;
- fatos;
- pedidos;
- evidências;
- inconsistências;
- perguntas;
- timeline documental;
- conclusões jurídicas;
- prazos;
- dados externos;
- conflitos encontrados.

A Equipe 3 receberá o contexto relevante das Equipes 1 e 2 e adicionará seus resultados, em vez de começar do zero.

## Observabilidade

Cada etapa deve gerar log específico com:

- equipe = `EQUIPE_3_DATAJUD`;
- agente;
- processo;
- etapa;
- início/fim;
- duração;
- status;
- endpoint (sem segredo);
- quantidade de resultados;
- erro, quando houver;
- resumo do resultado.

A chave DataJud nunca deve aparecer nos logs.

## Persistência futura

Os resultados da equipe devem ser persistidos como parte do processamento assíncrono do processo, permitindo:

- fechar o navegador sem perder o processamento;
- acompanhar novamente pelo menu Processos;
- consultar logs depois;
- baixar o relatório quando concluído;
- reprocessar apenas uma etapa em caso de falha, quando essa capacidade for adicionada.

## Critérios para considerar a Equipe 3 pronta

- [x] oito agentes definidos;
- [x] contrato de resultado comum;
- [x] cliente DataJud existente reutilizado;
- [x] consulta única por processo;
- [x] movimentações normalizadas;
- [x] metadados processuais normalizados;
- [x] reconciliação inicial;
- [x] documentação do mapa atual;
- [ ] mapear oficialmente dados de partes;
- [ ] mapear oficialmente dados de decisões;
- [ ] integrar contexto das Equipes 1 e 2;
- [ ] persistir resultado por agente;
- [ ] integrar logs/progresso à Central de Processos;
- [ ] iniciar em paralelo após identificação do CNJ;
- [ ] testes de integração com processos reais.
