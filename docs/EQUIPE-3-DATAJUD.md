# Equipe 3 — Inteligência Externa / Validação Processual

## Objetivo

A Equipe 3 é uma camada independente de validação processual. Ela não deve simplesmente repetir a leitura do PDF. Sua função é confrontar o que as Equipes 1 e 2 concluíram com dados públicos oficiais disponíveis no DataJud/CNJ e produzir confirmações, informações adicionais, divergências e lacunas.

## Ordem obrigatória das equipes

Para reduzir carga no sistema e evitar concorrência desnecessária com os agentes de IA, as equipes agora são **sequenciais**:

```text
Equipe 1 — Análise documental
        |
        | conclusão
        v
Equipe 2 — Análise jurídica especializada
        |
        | conclusão
        v
Equipe 3 — Inteligência externa / DataJud
        |
        | conclusão
        v
Relatório final / download
```

A Equipe 3 **não inicia quando o CNJ é encontrado**. O CNJ é identificado pela Equipe 1, mas a consulta DataJud só começa depois que todos os oito agentes da Equipe 2 terminarem.

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
8. `JusReconciliationAgent` — faz a reunião e compara os achados externos com o contexto das equipes anteriores.

## Execução e progresso

A `ExternalValidationTeam` executa os oito agentes em sequência sobre uma única consulta `DataJudInfo`. Ela aceita um callback de progresso para que cada etapa seja persistida e exibida na tela.

A barra de progresso é **por equipe**, e não global:

```text
Equipe 1 — Analisando
████████████████████ 100%

Equipe 2 — Analisando
████████████████████ 100%

Equipe 3 — Validando DataJud/Jus
████████████████████ 100%
```

Quando uma equipe termina, a barra seguinte volta para **0%**. O usuário continua na mesma tela e acompanha qual equipe está atuando.

Na Equipe 3, o progresso esperado é aproximadamente:

```text
0%   preparação
10%  ProcessSearchAgent
22%  MovementAgent
34%  PartiesAgent
46%  DecisionsAgent
58%  CourtAgent
70%  TimelineAgent
82%  ExternalEvidenceAgent
94%  JusReconciliationAgent
100% equipe concluída
```

## Logs

Cada log da análise longa informa explicitamente:

- `equipe` (`EQUIPE_1`, `EQUIPE_2` ou `EQUIPE_3_DATAJUD`);
- agente;
- número do agente e total de agentes;
- progresso da equipe;
- etapa/ação;
- mensagem explicando o que está sendo feito;
- contexto recebido;
- resultado parcial;
- estimativa restante;
- timestamp.

Exemplo:

```text
[PROCESSO:123][EQUIPE_3_DATAJUD][AGENTE:TimelineAgent]
70% | Construindo a linha do tempo externa para confronto.

Contexto:
Equipe 1
Equipe 2
Equipe 3
```

A chave DataJud nunca aparece nos logs.

## Reconciliação

A equipe deve identificar pelo menos:

- confirmação;
- divergência;
- informação nova;
- informação não verificável;
- fonte indisponível.

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
             |
             +-- INFORMAÇÃO NOVA
                 Evento de 18/03/2026 não encontrado no documento
```

O sistema não deve afirmar automaticamente que uma das fontes está errada.

## Contexto entre equipes

A Equipe 3 é executada depois da Equipe 2 justamente para poder receber o trabalho acumulado:

```text
Equipe 1
  └─ fatos, partes, timeline, evidências, inconsistências
          |
          v
Equipe 2
  └─ análise jurídica, prazos, riscos, pesquisa e parecer
          |
          v
Equipe 3
  └─ DataJud + validação + reconciliação
```

A próxima evolução é transformar esse intercâmbio em um `ProcessAnalysisContext` estruturado, além dos relatórios já existentes.

## Persistência e retomada

A análise continua sendo persistida no PostgreSQL. Os logs da Equipe 3 ficam no mesmo job da análise especializada e incluem a equipe atual. Portanto, ao fechar o navegador, o processamento continua no backend e, ao consultar novamente o processo, a tela consegue saber:

- qual equipe está trabalhando;
- qual agente está trabalhando;
- progresso atual da equipe;
- mensagem atual;
- estimativa;
- histórico de logs.

O relatório somente fica disponível para download quando a cadeia completa terminar.

## Limitações deliberadas

Ainda não foram inventados endpoints para partes ou decisões. A integração atual usa somente campos comprovadamente retornados pelo payload existente. Quando esses dados forem mapeados, devem entrar no `DataJudService` ou em cliente especializado, nunca em chamadas HTTP duplicadas dentro dos agentes.

## Critérios de evolução

- [x] oito agentes definidos;
- [x] cliente DataJud reutilizado;
- [x] consulta única por processo;
- [x] callback de progresso da Equipe 3;
- [x] execução sequencial após Equipe 2;
- [x] reset da barra para 0% ao trocar de equipe;
- [x] equipe atual exposta no status do job;
- [x] logs com equipe/agente/progresso;
- [x] persistência do histórico;
- [ ] mapear oficialmente dados de partes;
- [ ] mapear oficialmente dados de decisões;
- [ ] receber `ProcessAnalysisContext` estruturado das Equipes 1 e 2;
- [ ] incluir validação externa no relatório final para download;
- [ ] testes de integração com processos reais.
