# API — Leads Hunter

Esta documentação descreve somente a API REST existente no backend atual.

## Informações gerais

- **URL base local atual:** `http://localhost:8080`
- **Prefixo da API:** `/api`
- **Content-Type predominante:** `application/json`. As exceções são as exportações CSV e Excel.
- **Formato das respostas JSON:** objetos ou listas diretas, sem envelope comum. Campos sem valor podem aparecer como `null`. Datas e horas usam o formato ISO-8601 local, por exemplo `2026-08-22T10:30:00`, sem fuso horário.
- **Autenticação:** ainda não foi implementada. O projeto não possui Spring Security nem configuração de autenticação/autorização; portanto, todos os endpoints documentados estão públicos e não exigem header `Authorization`.
- **Paginação:** `GET /api/leads/pagina` possui paginação por status para o Kanban. As demais listagens e exportações continuam processando todos os registros que correspondem aos filtros.
- **Rate limiting:** existe apenas sobre chamadas externas reais à Google Places feitas durante `POST /api/buscas`. Por padrão, o bucket em memória permite 10 chamadas e repõe essa capacidade gradualmente em 60 segundos. Um resultado atendido pelo cache não consome o limite. Os demais endpoints não possuem rate limiting próprio.
- **Cache de buscas:** o resultado externo da Google é mantido em memória por 30 minutos, com até 100 entradas por padrão. A chave considera latitude e longitude arredondadas para quatro casas decimais, raio e categorias distintas ordenadas; `enderecoBase` não participa da chave. Mesmo em cache hit, uma nova busca e seus vínculos com leads são persistidos.

## Formato padrão dos erros

As exceções tratadas pelo `ApiExceptionHandler` retornam JSON neste formato:

```json
{
  "timestamp": "2026-08-25T13:40:12.345Z",
  "status": 400,
  "codigo": "VALIDACAO_INVALIDA",
  "mensagem": "latitude: não deve ser nulo",
  "path": "/api/buscas"
}
```

Campos:

- `timestamp`: instante UTC em que o erro foi montado;
- `status`: código HTTP numérico;
- `codigo`: código estável definido pela aplicação;
- `mensagem`: explicação segura do erro;
- `path`: caminho da requisição, sem os query params.

Os códigos atualmente previstos são:

| Código | Status | Situação |
|---|---:|---|
| `VALIDACAO_INVALIDA` | 400 | Bean Validation rejeitou o request body. |
| `REQUISICAO_INVALIDA` | 400 | Corpo ausente/malformado, enum inválido no JSON ou path/query param com tipo inválido. |
| `BUSCA_NAO_ENCONTRADA` | 404 | O ID de busca não existe. |
| `LEAD_NAO_ENCONTRADO` | 404 | O ID de lead não existe. |
| `GOOGLE_PLACES_RATE_LIMIT` | 429 | O limite local temporário de chamadas externas foi atingido. |
| `GOOGLE_PLACES_QUOTA_EXCEEDED` | 429 | A Google informou que a cota externa foi excedida. |
| `GOOGLE_PLACES_INVALID_RESPONSE` | 502 | A resposta externa não pôde ser interpretada com segurança. |
| `GOOGLE_PLACES_REQUEST_REJECTED` | 502 | A Google rejeitou a consulta com outro erro HTTP 4xx. |
| `GOOGLE_PLACES_CONFIGURATION` | 503 | A chave está ausente ou foi rejeitada pela Google. |
| `GOOGLE_PLACES_UNAVAILABLE` | 503 | Houve falha de rede ou indisponibilidade da Google. |
| `ERRO_INTERNO` | 500 | Ocorreu uma exceção inesperada. A mensagem pública é genérica. |

Para erros de validação, somente o primeiro erro de campo encontrado é colocado em `mensagem`.

## Valores aceitos pelos enums

Os valores são enviados exatamente como abaixo, em maiúsculas.

- `CategoriaNegocio`: `MERCADO`, `PADARIA`, `DOCERIA`, `RESTAURANTE`, `DISTRIBUIDORA`, `ACOUGUE`, `FARMACIA`, `OUTROS`.
- `StatusFunil`: `NOVO`, `QUALIFICADO`, `CONTATADO`, `GANHO`, `PERDIDO`.
- `Temperatura`: `QUENTE`, `MORNO`, `FRIO`.

# Buscas

## POST /api/buscas

### Objetivo

Executa uma busca de estabelecimentos próximos usando a Google Places API, ou reutiliza um resultado externo recente do cache. A operação sempre registra um novo histórico, deduplica leads pelo `googlePlaceId`, calcula score e temperatura e relaciona os leads à busca.

Um lead novo começa com status `NOVO`. Quando um lead já existente reaparece, os dados externos e o scoring podem ser atualizados, mas `status`, `observacoes` e `ultimoContatoEm` são preservados.

### Autenticação

Público atualmente. Autenticação ainda não foi implementada.

### Parâmetros

- **Path params:** nenhum.
- **Query params:** nenhum.
- **Header relevante:** `Content-Type: application/json` para o request body.
- **Header de autenticação:** nenhum.

### Request body

DTO real: `BuscaRequest`.

```json
{
  "enderecoBase": "Centro, Curitiba - PR",
  "latitude": -25.4284,
  "longitude": -49.2733,
  "raioKm": 5,
  "categorias": ["PADARIA", "MERCADO", "RESTAURANTE"]
}
```

| Campo | Tipo | Obrigatório | Finalidade e validações |
|---|---|---:|---|
| `enderecoBase` | string ou `null` | Não | Texto descritivo armazenado no histórico. Pode ter no máximo 255 caracteres. Não é usado para localizar a busca e não participa da chave do cache. String vazia é aceita. |
| `latitude` | número decimal | Sim | Latitude do centro da busca. Valor entre `-90.0` e `90.0`, inclusive. |
| `longitude` | número decimal | Sim | Longitude do centro da busca. Valor entre `-180.0` e `180.0`, inclusive. |
| `raioKm` | inteiro | Sim | Raio em quilômetros. Deve ser maior que zero e no máximo `20`. |
| `categorias` | array de `CategoriaNegocio` | Sim | Deve conter pelo menos um item e não aceita itens `null`. Não existe limite explícito de quantidade, e categorias repetidas não são rejeitadas pelo DTO. |

### Resultado esperado

Retorna um `BuscaResponse` com o ID da busca persistida, os parâmetros recebidos, a quantidade bruta de estabelecimentos retornada pela integração e os leads únicos persistidos.

`totalEncontrados` conta os itens da resposta externa antes da deduplicação por `googlePlaceId`; por isso, em uma resposta externa com IDs repetidos, ele pode ser maior que o tamanho de `leads`.

```json
{
  "id": 10,
  "enderecoBase": "Centro, Curitiba - PR",
  "latitude": -25.4284,
  "longitude": -49.2733,
  "raioKm": 5,
  "categorias": ["PADARIA", "MERCADO", "RESTAURANTE"],
  "totalEncontrados": 1,
  "criadoEm": "2026-08-22T10:00:00",
  "leads": [
    {
      "id": 20,
      "nome": "Padaria Central",
      "categoria": "PADARIA",
      "enderecoFormatado": "Rua Central, 100",
      "telefone": "(41) 3333-4444",
      "whatsappUrl": "https://wa.me/554133334444",
      "score": 95,
      "temperatura": "QUENTE"
    }
  ]
}
```

O máximo solicitado à Google por chamada é 20 estabelecimentos. Uma resposta externa vazia é aceita: a busca é persistida com `totalEncontrados: 0` e `leads: []`.

### Status HTTP

- `201 Created` — busca processada e persistida. O controller não adiciona header `Location`.
- `400 Bad Request` — validação falhou, o JSON está ausente/malformado ou contém valor de enum desconhecido.
- `429 Too Many Requests` — limite local de chamadas externas ou cota da Google excedido.
- `502 Bad Gateway` — consulta rejeitada pela Google ou resposta externa inválida.
- `503 Service Unavailable` — chave da Google ausente/rejeitada, falha de rede ou serviço externo indisponível.
- `500 Internal Server Error` — falha inesperada de processamento ou persistência.

### Possíveis erros

- Coordenadas ausentes ou fora dos limites geográficos.
- Raio ausente, zero, negativo ou superior a 20 km.
- Lista de categorias ausente, vazia, com item `null` ou com nome de enum desconhecido.
- Corpo ausente ou JSON inválido.
- Variável `GOOGLE_PLACES_API_KEY` ausente, vazia ou chave sem permissão.
- Rate limit local ou cota externa esgotados.
- Google Places indisponível, consulta rejeitada ou resposta inválida.
- Estabelecimento externo sem `googlePlaceId`; nesse caso ocorre erro interno e a transação é revertida.
- Falha inesperada no banco; a operação transacional é revertida.

### Fluxo interno resumido

Controller
→ desserializa e valida `BuscaRequest`
→ delega ao `BuscaService`
→ monta chave e consulta o cache
→ em cache miss, valida configuração, consome o rate limit e chama Google Places
→ normaliza a resposta externa
→ persiste o histórico da busca
→ deduplica estabelecimentos por `googlePlaceId`
→ cria ou atualiza leads preservando os dados comerciais
→ normaliza telefone e calcula score/temperatura
→ persiste os vínculos `BuscaLead` com o snapshot do scoring
→ retorna `BuscaResponse` com HTTP 201.

## GET /api/buscas

### Objetivo

Lista o histórico de buscas já persistidas, da mais recente para a mais antiga. Não consulta cache nem Google Places.

### Autenticação

Público atualmente. Autenticação ainda não foi implementada.

### Parâmetros

- **Path params:** nenhum.
- **Query params:** nenhum.
- **Headers relevantes:** nenhum.

Não há paginação ou limite de quantidade.

### Request body

Não possui.

### Resultado esperado

Retorna uma lista de `BuscaResumoResponse`:

```json
[
  {
    "id": 22,
    "enderecoBase": "Centro de Vitória",
    "latitude": -20.3155,
    "longitude": -40.3128,
    "raioKm": 5,
    "categorias": ["PADARIA"],
    "totalEncontrados": 18,
    "criadoEm": "2026-08-18T11:00:00"
  }
]
```

Sem histórico, retorna `200 OK` com `[]`.

### Status HTTP

- `200 OK` — histórico retornado, inclusive quando vazio.
- `500 Internal Server Error` — falha inesperada na consulta ou conversão dos dados persistidos.

### Possíveis erros

- Falha de acesso ao banco.
- Categorias persistidas em formato incompatível com `CategoriaNegocio`, tratadas como erro interno.

### Fluxo interno resumido

Controller
→ delega ao `BuscaService`
→ consulta todas as buscas ordenadas por `criadoEm` decrescente
→ converte as categorias persistidas para enums
→ retorna a lista de `BuscaResumoResponse`.

## GET /api/buscas/{id}

### Objetivo

Retorna o detalhe de uma busca já persistida e os leads relacionados a ela. O score e a temperatura vêm do snapshot gravado naquela execução; status, observações, último contato e demais dados do estabelecimento refletem o estado atual do lead.

### Autenticação

Público atualmente. Autenticação ainda não foi implementada.

### Parâmetros

| Local | Nome | Tipo | Obrigatório | Finalidade e validações |
|---|---|---|---:|---|
| Path | `id` | inteiro de 64 bits (`Long`) | Sim | Identificador da busca. Não possui validação explícita de valor positivo. Um número sem registro correspondente retorna 404. |

- **Query params:** nenhum.
- **Headers relevantes:** nenhum.

### Request body

Não possui.

### Resultado esperado

Retorna um `BuscaDetalheResponse`. Os leads são ordenados por `scoreNaBusca` decrescente.

```json
{
  "id": 22,
  "enderecoBase": "Centro de Vitória",
  "latitude": -20.3155,
  "longitude": -40.3128,
  "raioKm": 5,
  "categorias": ["PADARIA"],
  "totalEncontrados": 1,
  "criadoEm": "2026-08-18T11:00:00",
  "leads": [
    {
      "id": 35,
      "nome": "Padaria Central",
      "categoria": "PADARIA",
      "enderecoFormatado": "Rua Sete, 100",
      "telefone": "(27) 99999-0000",
      "whatsappUrl": "https://wa.me/5527999990000",
      "scoreNaBusca": 55,
      "temperaturaNaBusca": "MORNO",
      "status": "CONTATADO",
      "observacoes": "Retornar amanhã",
      "ultimoContatoEm": "2026-08-18T14:00:00"
    }
  ]
}
```

Uma busca existente sem vínculos retorna `leads: []`. `whatsappUrl` é `null` quando não existe telefone normalizado brasileiro válido.

### Status HTTP

- `200 OK` — detalhe retornado.
- `400 Bad Request` — `id` não pôde ser convertido para `Long`.
- `404 Not Found` — busca não encontrada (`BUSCA_NAO_ENCONTRADA`).
- `500 Internal Server Error` — falha inesperada na consulta ou conversão dos dados.

### Possíveis erros

- ID textual, decimal ou fora do formato aceito para `Long`.
- Nenhuma busca existente com o ID informado.
- Falha de acesso ao banco ou dados históricos incompatíveis com os enums atuais.

### Fluxo interno resumido

Controller
→ converte o path param
→ delega ao `BuscaService`
→ carrega a busca ou lança `BuscaNaoEncontradaException`
→ consulta os vínculos e os leads associados
→ combina scoring histórico com dados comerciais atuais
→ retorna `BuscaDetalheResponse`.

# Leads

## GET /api/leads

### Objetivo

Lista os leads persistidos e permite filtrar simultaneamente por status do funil, categoria e temperatura.

### Autenticação

Público atualmente. Autenticação ainda não foi implementada.

### Parâmetros

| Local | Nome | Tipo | Obrigatório | Finalidade e validações |
|---|---|---|---:|---|
| Query | `status` | `StatusFunil` | Não | Filtra por `NOVO`, `QUALIFICADO`, `CONTATADO`, `GANHO` ou `PERDIDO`. |
| Query | `categoria` | `CategoriaNegocio` | Não | Filtra por um dos valores de categoria documentados no início. |
| Query | `temperatura` | `Temperatura` | Não | Filtra por `QUENTE`, `MORNO` ou `FRIO`. |

Os filtros fazem correspondência exata e podem ser combinados. Um valor desconhecido falha na conversão e retorna 400.

- **Path params:** nenhum.
- **Headers relevantes:** nenhum.

Não há paginação nem limite de quantidade.

### Request body

Não possui.

### Resultado esperado

Retorna uma lista de `LeadResponse`, ordenada por score decrescente, com scores nulos no final; em empate, por nome crescente, com nomes nulos no final; persistindo o empate, por ID crescente.

```json
[
  {
    "id": 35,
    "googlePlaceId": "place-35",
    "nome": "Padaria Central",
    "categoria": "PADARIA",
    "enderecoFormatado": "Rua Central, 100",
    "telefone": "(27) 99999-0000",
    "telefoneNormalizado": "5527999990000",
    "whatsappUrl": "https://wa.me/5527999990000",
    "latitude": -20.3155,
    "longitude": -40.3128,
    "ratingGoogle": 4.8,
    "totalReviews": 120,
    "score": 95,
    "temperatura": "QUENTE",
    "status": "CONTATADO",
    "observacoes": "Retornar amanhã",
    "ultimoContatoEm": "2026-08-22T10:30:00",
    "criadoEm": "2026-08-20T09:00:00",
    "atualizadoEm": "2026-08-22T10:30:00"
  }
]
```

Sem correspondências, retorna `200 OK` com `[]`. `whatsappUrl` é somente um link manual e fica `null` quando o telefone normalizado é ausente ou inválido.

### Status HTTP

- `200 OK` — lista retornada, inclusive quando vazia.
- `400 Bad Request` — algum filtro não corresponde ao enum esperado.
- `500 Internal Server Error` — falha inesperada na consulta ou no mapeamento.

### Possíveis erros

- `status`, `categoria` ou `temperatura` com valor desconhecido ou capitalização diferente dos enums.
- Falha de acesso ao banco.

### Fluxo interno resumido

Controller
→ converte os filtros opcionais
→ delega ao `LeadService`
→ monta uma consulta por exemplo com os filtros não nulos
→ consulta e ordena os leads
→ calcula `whatsappUrl` a partir do telefone normalizado
→ retorna a lista de `LeadResponse`.

## GET /api/leads/pagina

### Objetivo

Retorna uma página de leads de um único status para permitir paginação independente por coluna no Kanban. O endpoint legado `GET /api/leads` permanece inalterado para os fluxos que precisam da lista completa.

### Autenticação

Público atualmente. Autenticação ainda não foi implementada.

### Parâmetros

| Local | Nome | Tipo | Obrigatório | Finalidade e validações |
|---|---|---|---:|---|
| Query | `status` | `StatusFunil` | Sim | Seleciona uma das cinco etapas reais do funil. |
| Query | `categoria` | `CategoriaNegocio` | Não | Aplica o filtro exato de categoria à coluna consultada. |
| Query | `temperatura` | `Temperatura` | Não | Aplica o filtro exato de temperatura à coluna consultada. |
| Query | `page` | inteiro | Não | Índice da página iniciado em zero. O padrão é `0`; aceita valores de `0` a `10000`. |
| Query | `size` | inteiro | Não | Quantidade por página. O padrão é `25`; aceita valores de `1` a `25`. |

- **Path params:** nenhum.
- **Headers relevantes:** nenhum.
- Os filtros podem ser combinados e são aplicados no banco antes da paginação.

### Request body

Não possui.

### Resultado esperado

Retorna somente os leads da página solicitada, ordenados por score decrescente, nome crescente e ID crescente como desempate estável. `totalElementos` informa o total real do status após os filtros, não apenas a quantidade da página atual.

```json
{
  "leads": [
    {
      "id": 35,
      "googlePlaceId": "place-35",
      "nome": "Padaria Central",
      "categoria": "PADARIA",
      "enderecoFormatado": "Rua Central, 100",
      "telefone": "(27) 99999-0000",
      "telefoneNormalizado": "5527999990000",
      "whatsappUrl": "https://wa.me/5527999990000",
      "latitude": -20.3155,
      "longitude": -40.3128,
      "ratingGoogle": 4.8,
      "totalReviews": 120,
      "score": 95,
      "temperatura": "QUENTE",
      "status": "QUALIFICADO",
      "observacoes": null,
      "ultimoContatoEm": null,
      "criadoEm": "2026-08-20T09:00:00",
      "atualizadoEm": "2026-08-22T10:30:00"
    }
  ],
  "pagina": 0,
  "tamanho": 25,
  "totalElementos": 63,
  "totalPaginas": 3
}
```

Sem correspondências, retorna `200 OK` com `leads` vazio, `totalElementos` igual a `0` e `totalPaginas` igual a `0`.

### Status HTTP

- `200 OK` — página retornada, inclusive quando vazia.
- `400 Bad Request` — status ausente ou inválido, página fora do intervalo de 0 a 10000 ou tamanho fora do intervalo de 1 a 25.
- `500 Internal Server Error` — falha inesperada na consulta ou no mapeamento.

### Fluxo interno resumido

Controller
→ valida status, página e tamanho
→ delega ao `LeadService`
→ monta a consulta por exemplo com status e filtros opcionais
→ pagina e ordena no banco
→ calcula `whatsappUrl` para os registros retornados
→ devolve conteúdo e metadados da página.

## GET /api/leads/{id}

### Objetivo

Consulta um lead persistido pelo ID.

### Autenticação

Público atualmente. Autenticação ainda não foi implementada.

### Parâmetros

| Local | Nome | Tipo | Obrigatório | Finalidade e validações |
|---|---|---|---:|---|
| Path | `id` | inteiro de 64 bits (`Long`) | Sim | Identificador do lead. Não possui validação explícita de valor positivo. Um número sem registro correspondente retorna 404. |

- **Query params:** nenhum.
- **Headers relevantes:** nenhum.

### Request body

Não possui.

### Resultado esperado

Retorna um `LeadResponse` com a mesma estrutura apresentada em `GET /api/leads`.

```json
{
  "id": 35,
  "googlePlaceId": "place-35",
  "nome": "Padaria Central",
  "categoria": "PADARIA",
  "enderecoFormatado": "Rua Central, 100",
  "telefone": "(27) 99999-0000",
  "telefoneNormalizado": "5527999990000",
  "whatsappUrl": "https://wa.me/5527999990000",
  "latitude": -20.3155,
  "longitude": -40.3128,
  "ratingGoogle": 4.8,
  "totalReviews": 120,
  "score": 95,
  "temperatura": "QUENTE",
  "status": "CONTATADO",
  "observacoes": "Retornar amanhã",
  "ultimoContatoEm": "2026-08-22T10:30:00",
  "criadoEm": "2026-08-20T09:00:00",
  "atualizadoEm": "2026-08-22T10:30:00"
}
```

### Status HTTP

- `200 OK` — lead retornado.
- `400 Bad Request` — `id` não pôde ser convertido para `Long`.
- `404 Not Found` — lead não encontrado (`LEAD_NAO_ENCONTRADO`).
- `500 Internal Server Error` — falha inesperada na consulta ou no mapeamento.

### Possíveis erros

- ID textual, decimal ou fora do formato aceito para `Long`.
- Nenhum lead existente com o ID informado.
- Falha de acesso ao banco.

### Fluxo interno resumido

Controller
→ converte o path param
→ delega ao `LeadService`
→ consulta o lead ou lança `LeadNaoEncontradoException`
→ calcula `whatsappUrl`
→ retorna `LeadResponse`.

## PATCH /api/leads/{id}

### Objetivo

Atualiza parcialmente os campos comerciais de um lead: status do funil, observações e/ou data do último contato. Os demais atributos, como dados da Google, score e temperatura, não são alteráveis por este endpoint.

Esta é a única rota de atualização de lead existente atualmente; não há endpoints separados com sufixos `/status`, `/observacoes` ou `/contato`.

### Autenticação

Público atualmente. Autenticação e autorização por recurso ainda não foram implementadas.

### Parâmetros

| Local | Nome | Tipo | Obrigatório | Finalidade e validações |
|---|---|---|---:|---|
| Path | `id` | inteiro de 64 bits (`Long`) | Sim | Identificador do lead a atualizar. Não possui validação explícita de valor positivo. |

- **Query params:** nenhum.
- **Header relevante:** `Content-Type: application/json` para o request body.
- **Header de autenticação:** nenhum.

### Request body

DTO real: `AtualizarLeadRequest`.

```json
{
  "status": "CONTATADO",
  "observacoes": "Retornar amanhã",
  "ultimoContatoEm": "2026-08-22T10:30:00"
}
```

| Campo | Tipo | Obrigatório | Finalidade e validações |
|---|---|---:|---|
| `status` | `StatusFunil` ou `null` | Não | Novo status. Quando informado, deve ser um valor exato do enum. |
| `observacoes` | string ou `null` | Não | Substitui as observações atuais. Não há limite de tamanho nem validação de conteúdo no DTO. String vazia é aceita. |
| `ultimoContatoEm` | string ISO-8601 local ou `null` | Não | Substitui a data/hora de último contato. Exemplo: `2026-08-22T10:30:00`. Não aceita offset/fuso no tipo atual e não valida datas futuras. |

Ao menos um dos três campos deve possuir valor não nulo. Campos omitidos ou enviados como `null` são preservados, não apagados. Consequentemente, o endpoint atual não permite limpar `observacoes` ou `ultimoContatoEm` enviando `null`; para observações, uma string vazia pode ser gravada.

### Resultado esperado

Persiste somente os campos não nulos recebidos e retorna o `LeadResponse` completo já atualizado:

```json
{
  "id": 35,
  "googlePlaceId": "place-35",
  "nome": "Padaria Central",
  "categoria": "PADARIA",
  "enderecoFormatado": "Rua Central, 100",
  "telefone": "(27) 99999-0000",
  "telefoneNormalizado": "5527999990000",
  "whatsappUrl": "https://wa.me/5527999990000",
  "latitude": -20.3155,
  "longitude": -40.3128,
  "ratingGoogle": 4.8,
  "totalReviews": 120,
  "score": 95,
  "temperatura": "QUENTE",
  "status": "CONTATADO",
  "observacoes": "Retornar amanhã",
  "ultimoContatoEm": "2026-08-22T10:30:00",
  "criadoEm": "2026-08-20T09:00:00",
  "atualizadoEm": "2026-08-25T14:05:00"
}
```

### Status HTTP

- `200 OK` — atualização persistida e lead atualizado retornado.
- `400 Bad Request` — corpo ausente/malformado, enum/data inválido ou todos os campos nulos/omitidos.
- `404 Not Found` — lead não encontrado (`LEAD_NAO_ENCONTRADO`).
- `500 Internal Server Error` — falha inesperada de processamento ou persistência.

### Possíveis erros

- ID inválido para `Long` ou lead inexistente.
- JSON ausente ou malformado.
- `status` desconhecido.
- `ultimoContatoEm` fora do formato aceito por `LocalDateTime`.
- Payload `{}` ou payload no qual todos os campos são `null`, rejeitado com `VALIDACAO_INVALIDA` e a mensagem `Informe ao menos um campo para atualização` acompanhada do nome da propriedade validada.
- Falha de acesso ao banco.

### Fluxo interno resumido

Controller
→ converte o ID, desserializa e valida `AtualizarLeadRequest`
→ delega ao `LeadService`
→ carrega o lead ou lança `LeadNaoEncontradoException`
→ aplica somente campos não nulos da allowlist comercial
→ persiste e atualiza `atualizadoEm`
→ calcula `whatsappUrl`
→ retorna o `LeadResponse` completo.

# Exportação

As duas exportações usam a mesma consulta e a mesma ordenação de `GET /api/leads`. Não há paginação nem limite de registros: todo o conjunto filtrado é carregado e transformado em memória.

As colunas, nesta ordem, são:

`id`, `googlePlaceId`, `nome`, `categoria`, `enderecoFormatado`, `telefone`, `telefoneNormalizado`, `whatsappUrl`, `latitude`, `longitude`, `ratingGoogle`, `totalReviews`, `score`, `temperatura`, `status`, `observacoes`, `ultimoContatoEm`, `criadoEm`, `atualizadoEm`.

## GET /api/exportacao/leads.csv

### Objetivo

Baixa os leads em um arquivo CSV UTF-8. Campos com vírgula, aspas ou quebra de linha recebem escaping CSV; valores nulos viram células vazias.

### Autenticação

Público atualmente. Autenticação ainda não foi implementada.

### Parâmetros

| Local | Nome | Tipo | Obrigatório | Finalidade e validações |
|---|---|---|---:|---|
| Query | `status` | `StatusFunil` | Não | Filtra pelo status exato. |
| Query | `categoria` | `CategoriaNegocio` | Não | Filtra pela categoria exata. |
| Query | `temperatura` | `Temperatura` | Não | Filtra pela temperatura exata. |

Os filtros podem ser combinados. Valores desconhecidos ou com capitalização diferente dos enums retornam 400.

- **Path params:** nenhum.
- **Headers de request relevantes:** nenhum.

### Request body

Não possui.

### Resultado esperado

Retorna bytes do arquivo, inclusive quando não há leads. Nesse caso, o CSV contém somente o cabeçalho.

Exemplo simplificado do conteúdo:

```csv
id,googlePlaceId,nome,categoria,enderecoFormatado,telefone,telefoneNormalizado,whatsappUrl,latitude,longitude,ratingGoogle,totalReviews,score,temperatura,status,observacoes,ultimoContatoEm,criadoEm,atualizadoEm
15,place-15,Padaria Central,PADARIA,"Rua Central, 100",(27) 99999-0000,5527999990000,https://wa.me/5527999990000,-20.3155,-40.3128,4.8,120,95,QUENTE,CONTATADO,Retornar amanhã,2026-08-20T10:30,2026-08-19T09:00,2026-08-20T10:30
```

Headers de resposta:

- `Content-Type: text/csv;charset=UTF-8`
- `Content-Disposition: attachment; filename="leads.csv"`

### Status HTTP

- `200 OK` — arquivo gerado, mesmo sem registros.
- `400 Bad Request` — algum filtro não corresponde ao enum esperado.
- `500 Internal Server Error` — falha inesperada na consulta ou geração do arquivo.

### Possíveis erros

- Filtro com valor inválido.
- Falha de acesso ao banco.
- Falha inesperada durante a montagem do CSV.

### Fluxo interno resumido

Controller
→ converte os filtros opcionais
→ delega ao `ExportService`
→ reutiliza a listagem filtrada e ordenada do `LeadService`
→ monta cabeçalho e linhas CSV em UTF-8
→ aplica escaping estrutural a vírgulas, aspas e quebras de linha
→ retorna o arquivo como attachment.

## GET /api/exportacao/leads.xlsx

### Objetivo

Baixa os leads em uma planilha Excel no formato XLSX.

### Autenticação

Público atualmente. Autenticação ainda não foi implementada.

### Parâmetros

| Local | Nome | Tipo | Obrigatório | Finalidade e validações |
|---|---|---|---:|---|
| Query | `status` | `StatusFunil` | Não | Filtra pelo status exato. |
| Query | `categoria` | `CategoriaNegocio` | Não | Filtra pela categoria exata. |
| Query | `temperatura` | `Temperatura` | Não | Filtra pela temperatura exata. |

Os filtros podem ser combinados. Valores desconhecidos ou com capitalização diferente dos enums retornam 400.

- **Path params:** nenhum.
- **Headers de request relevantes:** nenhum.

### Request body

Não possui.

### Resultado esperado

Retorna os bytes de uma pasta de trabalho XLSX com uma planilha chamada `Leads`. O cabeçalho é exibido em negrito, a primeira linha fica congelada, há filtro automático, as colunas são autoajustadas e números/datas são gravados como células tipadas.

Quando não há leads, ainda retorna um arquivo válido com o cabeçalho.

Headers de resposta:

- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition: attachment; filename="leads.xlsx"`

### Status HTTP

- `200 OK` — arquivo gerado, mesmo sem registros.
- `400 Bad Request` — algum filtro não corresponde ao enum esperado.
- `500 Internal Server Error` — falha inesperada na consulta ou geração da planilha.

### Possíveis erros

- Filtro com valor inválido.
- Falha de acesso ao banco.
- Erro de I/O durante a criação do XLSX; é convertido pelo tratamento genérico em `ERRO_INTERNO`.

### Fluxo interno resumido

Controller
→ converte os filtros opcionais
→ delega ao `ExportService`
→ reutiliza a listagem filtrada e ordenada do `LeadService`
→ cria workbook e planilha `Leads`
→ preenche cabeçalho e células tipadas
→ serializa o XLSX em memória
→ retorna o arquivo como attachment.

# Outros endpoints

## GET /api/health

### Objetivo

Fornece um health check simples e estático da aplicação.

Este endpoint não testa conexão com banco, Google Places, cache ou outros componentes; portanto, `UP` indica apenas que o controller respondeu.

### Autenticação

Público atualmente. Autenticação ainda não foi implementada.

### Parâmetros

- **Path params:** nenhum.
- **Query params:** nenhum.
- **Headers relevantes:** nenhum.

### Request body

Não possui.

### Resultado esperado

```json
{
  "status": "UP"
}
```

### Status HTTP

- `200 OK` — resposta estática retornada.

### Possíveis erros

O método não possui dependências nem caminho de erro próprio. Falhas da infraestrutura HTTP ou da própria aplicação ainda podem impedir uma resposta.

### Fluxo interno resumido

Controller
→ cria um mapa com `status: UP`
→ retorna JSON com HTTP 200.

# Tabela-resumo

| Método | Endpoint | Função |
|---|---|---|
| POST | `/api/buscas` | Executa uma busca de estabelecimentos, persiste o histórico e os leads. |
| GET | `/api/buscas` | Lista o histórico de buscas. |
| GET | `/api/buscas/{id}` | Consulta uma busca e os leads encontrados naquela execução. |
| GET | `/api/leads` | Lista e filtra os leads persistidos. |
| GET | `/api/leads/{id}` | Consulta um lead pelo ID. |
| PATCH | `/api/leads/{id}` | Atualiza parcialmente status, observações e último contato. |
| GET | `/api/exportacao/leads.csv` | Exporta os leads filtrados em CSV. |
| GET | `/api/exportacao/leads.xlsx` | Exporta os leads filtrados em XLSX. |
| GET | `/api/health` | Retorna um health check estático. |
