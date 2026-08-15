# Equipe 3 — Inteligência Externa / Validação Processual

## Objetivo

A Equipe 3 é uma camada independente de validação processual. Ela não deve simplesmente repetir a leitura do PDF. Sua função é confrontar o que as Equipes 1 e 2 concluíram com dados públicos oficiais disponíveis no DataJud/CNJ e produzir confirmações, informações adicionais, divergências e lacunas.

## Regra principal

Nenhum agente pode declarar que uma informação é verdadeira apenas porque veio de uma consulta externa. O resultado externo mantém origem, data da consulta e contexto para posterior reconciliação.

## Mapa da integração atual

A integração está centralizada em `DataJudService`.

```text
número CNJ
   |
   v
resolverTribunal()
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
   +-- status / tribunal / endpoint
   +-- encontrado / classe / órgão / grau
   +-- consultadoEm / movimentos[]
```

## Agentes

1. `ProcessSearchAgent` — identifica e confirma o processo e metadados.
2. `MovementAgent` — normaliza movimentações.
3. `PartiesAgent` — contrato para validação externa de partes; aguarda campos/endpoints oficiais específicos.
4. `DecisionsAgent` — contrato para decisões; aguarda campos/endpoints oficiais específicos.
5. `CourtAgent` — valida tribunal, órgão, grau e classe.
6. `TimelineAgent` — organiza a linha do tempo externa.
7. `ExternalEvidenceAgent` — prepara evidências externas para confronto com o contexto interno.
8. `JusReconciliationAgent` — faz a reunião e compara o contexto consolidado com o DataJud.

## O que está efetivamente implementado

`ExternalValidationTeam` é o orquestrador da Equipe 3. Ele:

1. identifica o primeiro número CNJ no texto;
2. faz **uma única consulta** ao `DataJudService`;
3. distribui o mesmo `DataJudInfo` aos especialistas, evitando chamadas duplicadas;
4. entrega o `ExtractionResult` consolidado ao `JusReconciliationAgent`;
5. compara a timeline documental com `movimentos[]` do DataJud;
6. identifica confirmações, divergências e informações novas;
7. calcula um **índice operacional de consistência** baseado apenas nas comparações realizadas;
8. devolve o resultado na resposta da análise como `validacaoExterna`.

O índice de consistência não representa verdade jurídica. Ele é somente uma métrica de cobertura/consistência das informações comparadas.

## Reconciliação

Exemplo:

```text
Equipe 1
"Última movimentação identificada: 15/03/2026"
             |
             v
DataJud
"Última movimentação: 18/03/2026"
             |
             v
JusReconciliationAgent
             |
             +-- DIVERGÊNCIA
             |   Documento: 15/03/2026
             |   DataJud:   18/03/2026
             |   Severidade: MEDIA/ALTA conforme o tipo
             |
             +-- INFORMAÇÃO NOVA
                 Evento de 18/03/2026 não encontrado no documento
```

Eventos são comparados primeiro por data e depois por similaridade textual. Se a data coincide mas a descrição não, a equipe registra conflito. Se um evento oficial não existe no contexto interno, registra como novo dado. Se o evento interno não aparece externamente, registra como divergência, sem afirmar qual fonte está errada.

## Saída da reunião

`JusReconciliationAgent` produz:

- `status` da reconciliação;
- processo e tribunal;
- índice operacional de consistência;
- confirmações;
- divergências com severidade;
- novos dados encontrados;
- fonte externa;
- quantidade de agentes participantes.

## Integração no pipeline

A validação já foi integrada ao `LegalAnalysisOrchestrator`.

```text
PDF
 |
 +--> Equipe 1 / análise documental
 |        |
 |        +--> ExtractionResult consolidado
 |                         |
 |                         v
 |                 Equipe 3 / DataJud
 |                         |
 |                 JusReconciliation
 |                         |
 +-------------------------+-----> resposta final
```

Nesta primeira integração a Equipe 3 ocorre após a consolidação da análise base. A evolução planejada é iniciar a consulta externa assim que o CNJ for identificado e executá-la em paralelo com a Equipe 2, aguardando apenas a reunião final para cruzar todos os resultados.

## Contexto entre equipes

O `ExtractionResult` consolidado da Equipe 1 já é entregue à reunião da Equipe 3. A próxima evolução deve ampliar esse contrato para receber também claims estruturadas da Equipe 2, como conclusões jurídicas, prazos, evidências e riscos.

A arquitetura pretendida é:

```text
Equipe 1 ─────────────┐
                      ├── contexto compartilhado ──> Equipe 3
Equipe 2 ─────────────┘                              |
                                                     v
                                         JusReconciliationAgent
```

## Observabilidade

A execução gera logs específicos com:

- equipe = `EQUIPE_3_DATAJUD`;
- processo;
- agente;
- status;
- resultado da reconciliação;
- quantidade de divergências/confirmações.

A chave DataJud nunca deve aparecer nos logs.

## Limitações deliberadas

Ainda não foram inventados endpoints para partes ou decisões. A integração atual usa somente campos comprovadamente retornados pelo payload existente. Quando esses dados forem mapeados, devem entrar no `DataJudService` ou em cliente especializado, nunca em chamadas HTTP duplicadas dentro dos agentes.

## Persistência

O resultado da Equipe 3 agora acompanha a resposta da análise em `validacaoExterna`. A persistência definitiva por etapa/agente deve ser adicionada ao armazenamento do processamento assíncrono para permitir histórico, download e reprocessamento independente.

## Próximas evoluções

- [x] oito agentes definidos;
- [x] contrato de resultado comum;
- [x] cliente DataJud reutilizado;
- [x] consulta única por processo;
- [x] movimentações normalizadas;
- [x] metadados normalizados;
- [x] reconciliação inicial real;
- [x] comparação com `ExtractionResult` consolidado;
- [x] integração ao pipeline;
- [x] resultado exposto em `validacaoExterna`;
- [x] teste unitário de divergência;
- [ ] mapear oficialmente dados de partes;
- [ ] mapear oficialmente dados de decisões;
- [ ] receber contexto estruturado completo da Equipe 2;
- [ ] persistir resultado por agente;
- [ ] integrar logs detalhados à Central de Processos;
- [ ] iniciar DataJud em paralelo com a Equipe 2;
- [ ] incluir validação externa no relatório final para download;
- [ ] testes de integração com processos reais.
