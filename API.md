# API — Leads Hunter

Esta documentação descreve somente a API REST existente no backend atual.

## Informações gerais

- **URL base local atual:** `http://localhost:8080`
- **Prefixo da API:** `/api`
- **Content-Type predominante:** `application/json`. As exceções são a camada municipal em `application/geo+json` e as exportações CSV e Excel.
- **Formato das respostas JSON:** objetos ou listas diretas, sem envelope comum. Campos sem valor podem aparecer como `null`. Datas e horas usam o formato ISO-8601 local, por exemplo `2026-08-22T10:30:00`, sem fuso horário.
- **Autenticação:** ainda não foi implementada. O projeto não possui Spring Security nem configuração de autenticação/autorização; portanto, todos os endpoints documentados estão públicos e não exigem header `Authorization`.
- **Paginação:** `GET /api/leads/pagina` possui paginação por status para o Kanban. As demais listagens e exportações continuam processando todos os registros que correspondem aos filtros.
- **Rate limiting:** o bucket em memória limita somente chamadas externas reais à Google Places feitas durante `POST /api/buscas`. Por padrão, permite 10 chamadas e repõe essa capacidade gradualmente em 60 segundos; cache hit não consome o limite. As ações de pesquisa e e-mail têm limites próprios de volume e capacidade do worker, descritos em suas rotas.
- **Cache de buscas:** o resultado externo da Google é mantido em memória por 30 minutos, com até 100 entradas por padrão. A chave considera latitude e longitude arredondadas para quatro casas decimais, raio e categorias distintas ordenadas; `enderecoBase` não participa da chave. Mesmo em cache hit, uma nova busca e seus vínculos com leads são persistidos.
- **Pesquisa inteligente:** tem worker próprio, uma execução em processamento e uma aguardando, limite padrão de 150 leads e bloqueio de duplicatas por busca. A fonte é a API oficial do Brave Search (`BRAVE_SEARCH_API_KEY`). O scraping Bing → Google → DuckDuckGo → Brave permanece no código, desativado por padrão. Sem chave, a descoberta pelo Brave fica indisponível. A execução pode desativar o Brave pelo campo `usarBrave`; nesse caso, somente evidências já disponíveis, incluindo o site oficial salvo, são avaliadas. Não consome API da Google nem Places.

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
| `VALIDACAO_INVALIDA` | 400 | Restrições de validação do corpo ou dos parâmetros foram violadas. |
| `REQUISICAO_INVALIDA` | 400 | Corpo ausente/malformado, enum ou parâmetro inválido, ou `bbox` inválido. |
| `BLOQUEIO_INVALIDO` | 400 | O termo informado não atende às regras de tamanho após a normalização. |
| `TERMO_BLOQUEADO_DUPLICADO` | 400 | Já existe um bloqueio equivalente após normalização. |
| `BUSCA_NAO_ENCONTRADA` | 404 | O ID de busca não existe. |
| `LEAD_NAO_ENCONTRADO` | 404 | O ID de lead não existe. |
| `BLOQUEIO_NAO_ENCONTRADO` | 404 | O ID de bloqueio não existe. |
| `GOOGLE_PLACES_RATE_LIMIT` | 429 | O limite local temporário de chamadas externas foi atingido. |
| `PESQUISA_LIMITE_EXCEDIDO` | 429 | Worker indisponível/ocupado ou busca acima do limite configurado de leads. Nenhuma nova execução é criada. |
| `EMAIL_LIMITE_EXCEDIDO` | 429 | Worker de e-mails ocupado ou busca acima do limite configurado de leads. |
| `GOOGLE_PLACES_QUOTA_EXCEEDED` | 429 | A Google informou que a cota externa foi excedida. |
| `GOOGLE_PLACES_INVALID_RESPONSE` | 502 | A resposta externa não pôde ser interpretada com segurança. |
| `GOOGLE_PLACES_REQUEST_REJECTED` | 502 | A Google rejeitou a consulta com outro erro HTTP 4xx. |
| `GOOGLE_PLACES_CONFIGURATION` | 503 | A chave está ausente ou foi rejeitada pela Google. |
| `GOOGLE_PLACES_UNAVAILABLE` | 503 | Houve falha de rede ou indisponibilidade da Google. |
| `ERRO_INTERNO` | 500 | Ocorreu uma exceção inesperada. A mensagem pública é genérica. |

Para erros de validação, somente o primeiro erro de campo encontrado é colocado em `mensagem`.

## Valores aceitos pelos enums

Os valores são enviados exatamente como abaixo, em maiúsculas.

- `CategoriaNegocio`: `MERCADO`, `PADARIA`, `DOCERIA`, `RESTAURANTE`, `DISTRIBUIDORA`, `ACOUGUE`, `FARMACIA`, `INFORMATICA`, `VESTUARIO`, `PETSHOP`, `OUTROS`.
- `StatusFunil`: `NOVO`, `QUALIFICADO`, `CONTATADO`, `GANHO`, `PERDIDO`.
- `Temperatura`: `QUENTE`, `MORNO`, `FRIO`.

## Bloqueios por nome

Os termos desta área são persistidos no banco e comparados com o nome de cada estabelecimento já retornado pela Google. A normalização remove acentos, converte para minúsculas e desconsidera espaços nas bordas. A correspondência é por trecho: cadastrar `Supermercados BH` bloqueia, por exemplo, `Supermercados BH Centro`.

O bloqueio é aplicado somente depois da chamada externa e da deduplicação por `googlePlaceId`. Portanto, não reduz chamadas nem consumo da Google, não solicita resultados adicionais e não remove retroativamente leads de buscas anteriores.

### GET /api/bloqueios

Lista os termos bloqueados na ordem de criação e, em caso de empate, por ID. Uma lista sem cadastros retorna `200 OK` com `[]`.

```json
[
  {
    "id": 7,
    "termo": "Supermercados BH",
    "criadoEm": "2026-09-08T10:30:00"
  }
]
```

### POST /api/bloqueios

Cadastra um termo e retorna o registro persistido com `201 Created`.

```json
{
  "termo": "Supermercados BH"
}
```

`termo` é obrigatório. Depois de remover os espaços das bordas, deve conter de 3 a 120 caracteres. A unicidade considera a versão normalizada, de modo que variações apenas de acento, caixa ou espaços retornam `400 TERMO_BLOQUEADO_DUPLICADO`. Payload inválido retorna `400 VALIDACAO_INVALIDA`; uma inconsistência detectada somente pelo serviço retorna `400 BLOQUEIO_INVALIDO`.

### DELETE /api/bloqueios/{id}

Remove o termo identificado pelo ID e retorna `204 No Content`. O bloqueio deixa de valer nas próximas buscas; leads que já existem permanecem inalterados. Um ID inexistente retorna `404 BLOQUEIO_NAO_ENCONTRADO`, e um valor que não pode ser convertido para `Long` retorna `400 REQUISICAO_INVALIDA`.

## Buscas

### POST /api/buscas

#### Objetivo

Executa uma busca de estabelecimentos próximos usando a Google Places API, ou reutiliza um resultado externo recente do cache. A operação sempre registra um novo histórico, deduplica os resultados pelo `googlePlaceId`, ignora nomes correspondentes aos bloqueios cadastrados, calcula score e temperatura e relaciona à busca somente os leads permitidos.

Um lead novo começa com status `NOVO`. Quando um lead já existente reaparece, os dados externos e o scoring podem ser atualizados, mas `status`, `observacoes` e `ultimoContatoEm` são preservados.

#### Request body

Envie `Content-Type: application/json` com `BuscaRequest`:

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

#### Resultado esperado

Retorna um `BuscaResponse` com o ID da busca persistida, os parâmetros recebidos, a quantidade bruta de estabelecimentos retornada pela integração e os leads únicos persistidos.

`totalEncontrados` conta os itens da resposta externa antes da deduplicação e da aplicação dos bloqueios; por isso, pode ser maior que o tamanho de `leads`. `totalBloqueados` conta os estabelecimentos únicos ignorados depois da deduplicação por `googlePlaceId`.

```json
{
  "id": 10,
  "enderecoBase": "Centro, Curitiba - PR",
  "latitude": -25.4284,
  "longitude": -49.2733,
  "raioKm": 5,
  "categorias": ["PADARIA", "MERCADO", "RESTAURANTE"],
  "totalEncontrados": 1,
  "totalBloqueados": 0,
  "criadoEm": "2026-08-22T10:00:00",
  "leads": [
    {
      "id": 20,
      "nome": "Padaria Central",
      "categoria": "PADARIA",
      "enderecoFormatado": "Rua Central, 100",
      "website": "https://padariacentral.example/",
      "telefone": "(41) 3333-4444",
      "whatsappUrl": "https://wa.me/554133334444",
      "score": 95,
      "temperatura": "QUENTE"
    }
  ]
}
```

O máximo solicitado à Google por chamada é 20 estabelecimentos. Uma resposta externa vazia é aceita: a busca é persistida com `totalEncontrados: 0`, `totalBloqueados: 0` e `leads: []`. A contagem de bloqueados pertence apenas ao retorno imediato; o histórico conserva `totalEncontrados` e os vínculos dos leads efetivamente persistidos.

#### Status HTTP

- `201 Created` — busca processada e persistida. O controller não adiciona header `Location`.
- `400 Bad Request` — validação falhou, o JSON está ausente/malformado ou contém valor de enum desconhecido.
- `429 Too Many Requests` — limite local de chamadas externas ou cota da Google excedido.
- `502 Bad Gateway` — consulta rejeitada pela Google ou resposta externa inválida.
- `503 Service Unavailable` — chave da Google ausente/rejeitada, falha de rede ou serviço externo indisponível.
- `500 Internal Server Error` — falha inesperada de processamento ou persistência.

### GET /api/buscas

#### Objetivo

Lista o histórico de buscas já persistidas, da mais recente para a mais antiga. Não consulta cache nem Google Places.

#### Resultado esperado

Retorna uma lista de `BuscaResumoResponse`, sem paginação ou limite de quantidade:

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

#### Status HTTP

- `200 OK` — histórico retornado, inclusive quando vazio.
- `500 Internal Server Error` — falha inesperada na consulta ou conversão dos dados persistidos.

### GET /api/buscas/{id}

#### Objetivo

Retorna o detalhe de uma busca já persistida e os leads relacionados a ela. O score e a temperatura vêm do snapshot gravado naquela execução; status, observações, último contato e demais dados do estabelecimento refletem o estado atual do lead.

#### Parâmetros

| Local | Nome | Tipo | Obrigatório | Finalidade e validações |
|---|---|---|---:|---|
| Path | `id` | inteiro de 64 bits (`Long`) | Sim | Identificador da busca. Não possui validação explícita de valor positivo. Um número sem registro correspondente retorna 404. |

#### Resultado esperado

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
      "website": "https://padariacentral.example/",
      "email": "contato@padariacentral.example",
      "cnpj": "12345678000190",
      "razaoSocial": "Padaria Central LTDA",
      "cnpjOrigem": "ENDERECO_EXATO",
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

Uma busca existente sem vínculos retorna `leads: []`. `whatsappUrl` é `null` quando não existe telefone normalizado brasileiro válido. `email` é o contato atual do `Lead` capturado no site oficial, ou `null` quando ainda não encontrado. `cnpj` e `razaoSocial` refletem os valores atuais do `Lead`, com os 14 dígitos da unidade e o nome empresarial quando houver correspondência segura; ficam `null` quando ela não existir. `cnpjOrigem` informa `ENDERECO_EXATO` ou `NOME_ENDERECO` quando o CNPJ foi preenchido nesta versão; permanece `null` para CNPJs históricos sem proveniência registrada.

#### Status HTTP

- `200 OK` — detalhe retornado.
- `400 Bad Request` — `id` não pôde ser convertido para `Long`.
- `404 Not Found` — busca não encontrada (`BUSCA_NAO_ENCONTRADA`).
- `500 Internal Server Error` — falha inesperada na consulta ou conversão dos dados.

### POST /api/buscas/{id}/cnpj

Tenta identificar localmente o CNPJ dos leads vinculados à busca por `BuscaLead` que possuem `cnpj = null`. Leads já preenchidos são ignorados, mesmo que sua competência seja antiga. A ação não chama Google, Receita ou outras APIs, não cria uma busca e não possui novo rate limit. Endpoint público no estágio atual do MVP, sem autenticação.

O parâmetro `id` deve ser convertível para `Long`; não há request body nem query params. Busca inexistente retorna `404` com `BUSCA_NAO_ENCONTRADA`; ID não numérico retorna `400`. Busca existente, inclusive vazia, retorna `200`:

```json
{
  "totalLeads": 10,
  "ignoradosJaComCnpj": 3,
  "encontrados": 5,
  "semCorrespondencia": 2
}
```

`totalLeads` conta os vínculos persistidos, não o total bruto capturado pela Google. As outras três contagens somam esse total. Sem município/endereço suficiente, sem candidato confiável ou com ambiguidade, o lead permanece sem CNPJ. Quando há correspondência, a transação persiste CNPJ, razão social, data da correspondência, competência e confiança. Dados comerciais e snapshots permanecem intactos. Repetir a ação ignora os leads anteriormente preenchidos. Falhas inesperadas usam o tratamento padrão de erro `500` da API.

Após o POST, consulte `GET /api/buscas/{id}` para obter os dados atuais. CNPJ e razão social também aparecem nos contratos de lead e nas exportações.

### POST /api/buscas/{id}/informacoes

Inicia por ação explícita a pesquisa de site próprio e Instagram dos leads vinculados à busca. O ID deve ser positivo. O corpo é opcional:

```json
{"usarBrave": true}
```

`usarBrave` ausente ou `null` equivale a `true`. Com `false`, a execução não consulta o Brave: usa somente o site oficial já salvo e links encontrados nas páginas permitidas. A resposta é `202 Accepted`, com `Location: /api/buscas/{id}/informacoes`, `Cache-Control: no-store` e o estado persistido da execução:

```json
{
  "id": 42,
  "buscaId": 10,
  "status": "PENDENTE",
  "criadoEm": "2026-09-12T14:00:00",
  "iniciadoEm": null,
  "atualizadoEm": "2026-09-12T14:00:00",
  "terminadoEm": null,
  "totalLeads": 25,
  "progresso": 0,
  "processados": 0,
  "ignoradosJaCompletos": 0,
  "comInstagram": 0,
  "comSite": 0,
  "comAmbos": 0,
  "semInformacoes": 0,
  "falhas": 0,
  "erroCodigo": null,
  "erroMensagem": null,
  "usarBrave": true
}
```

Se já houver execução `PENDENTE` ou `EM_ANDAMENTO` para a busca, o POST retorna essa execução com `202`, sem criar outra. Uma execução encerrada permite novo POST. Leads com ambos os links válidos são ignorados; falha técnica não grava ausência conclusiva nem altera observações manuais.

O worker admite uma execução em processamento e uma aguardando. O limite padrão é de 150 leads vinculados à busca, configurável entre 1 e 1.000. Volume ou capacidade excedidos retornam `429 PESQUISA_LIMITE_EXCEDIDO`, sem criar execução. Busca inexistente retorna `404 BUSCA_NAO_ENCONTRADA`; ID não numérico ou não positivo retorna `400`.

A pesquisa consulta a API oficial do Brave no máximo três vezes por lead e abre no máximo três páginas candidatas para confirmar telefone, endereço ou CNPJ. O site oficial salvo é avaliado primeiro e sua leitura conta nesse teto. Não há scraping de motores de busca no fluxo padrão, acesso a destinos privados ou seguimento de redirecionamentos. Candidatos sem confirmação de identidade são recusados. A pesquisa não chama a Google Places. Quando `usarBrave=true` e a fonte de descoberta está indisponível, os leads que dependem dela registram falha técnica; a leitura do site oficial salvo continua possível.

### GET /api/buscas/{id}/informacoes

Consulta a execução ativa ou, na ausência dela, a mais recente. Retorna `200 OK` com o mesmo DTO e `Cache-Control: no-store`; busca existente sem execução retorna `204 No Content`. A consulta não inicia trabalho. Busca inexistente retorna `404 BUSCA_NAO_ENCONTRADA`; ID inválido retorna `400`.

`progresso = processados + ignoradosJaCompletos + falhas`; é uma contagem, não um percentual. `processados` inclui resultados conclusivos com ou sem links. `comInstagram` e `comSite` incluem os casos com ambos.

| Estado | Significado |
| --- | --- |
| `PENDENTE` | Aguardando o worker local. |
| `EM_ANDAMENTO` | Processamento iniciado. |
| `CONCLUIDA` | Todos os leads contabilizados sem falhas. |
| `CONCLUIDA_COM_FALHAS` | Houve falhas e ao menos um resultado conclusivo ou lead já completo. |
| `FALHA` | Falha total, inesperada ou interrupção; pode haver progresso parcial. |

Falha do trabalho aparece no DTO com HTTP `200`, pois o GET foi atendido. `erroCodigo` pode ser `PESQUISA_BLOQUEADA`, `PESQUISA_TIMEOUT`, `PESQUISA_FORMATO_INVALIDO`, `PESQUISA_INDISPONIVEL`, `PESQUISA_OCUPADA`, `PESQUISA_INTERROMPIDA` ou `PESQUISA_ERRO_INTERNO`. Reinício marca execuções ativas como interrompidas; não há retomada automática. Resultados já confirmados permanecem salvos.

### POST /api/buscas/{id}/emails

Inicia por ação explícita a captura de e-mails dos leads vinculados à busca. Não recebe corpo. Retorna `202 Accepted`, `Location: /api/buscas/{id}/emails`, `Cache-Control: no-store` e o DTO persistido, sem aguardar a leitura dos sites. Uma execução ativa da mesma busca é retornada com `202`; depois de encerrada, outro POST pode iniciar nova execução. Leads que já têm e-mail são ignorados.

O worker processa uma busca por vez e admite uma em espera. O limite padrão é de 150 leads por busca, configurável entre 1 e 1.000. Volume ou capacidade excedidos retornam `429 EMAIL_LIMITE_EXCEDIDO`. Busca inexistente retorna `404 BUSCA_NAO_ENCONTRADA`; ID inválido retorna `400`.

Para cada lead sem e-mail, a ação lê somente o site oficial (`websiteUri`): a página inicial e, se necessário, uma página de contato do mesmo host. Telefone, endereço numerado ou CNPJ devem confirmar o estabelecimento sem conflito. Somente e-mails do host normalizado ou de seus subdomínios são gravados; domínio-pai e terceiros são recusados. Não há chamada à Google ou ao Brave, redirecionamento, acesso a host privado ou envio de e-mail. Falha de leitura não apaga um e-mail existente nem registra ausência conclusiva. Quando uma atualização do Places muda o host do site salvo, e-mail, data e host de origem são invalidados.

### GET /api/buscas/{id}/emails

Consulta a execução ativa ou, na ausência dela, a mais recente. Retorna `200 OK` com `Cache-Control: no-store`; busca existente sem execução retorna `204 No Content`. O GET não inicia trabalho nem acessa sites. Busca inexistente retorna `404 BUSCA_NAO_ENCONTRADA`; ID inválido retorna `400`.

```json
{
  "id": 7,
  "buscaId": 10,
  "status": "CONCLUIDA_COM_FALHAS",
  "criadoEm": "2026-09-12T14:00:00",
  "iniciadoEm": "2026-09-12T14:00:01",
  "atualizadoEm": "2026-09-12T14:01:30",
  "terminadoEm": "2026-09-12T14:01:30",
  "totalLeads": 18,
  "progresso": 18,
  "ignoradosJaComEmail": 3,
  "ignoradosSemSite": 4,
  "processados": 11,
  "encontrados": 6,
  "semEmailElegivel": 4,
  "descartadosDominioExterno": 1,
  "falhas": 1,
  "erroCodigo": null,
  "erroMensagem": null
}
```

`progresso = ignoradosJaComEmail + ignoradosSemSite + processados`; `processados = encontrados + semEmailElegivel + falhas`. `descartadosDominioExterno` conta leads com ao menos um contato de domínio externo recusado, não endereços. Estados: `PENDENTE`, `EM_ANDAMENTO`, `CONCLUIDA`, `CONCLUIDA_COM_FALHAS` e `FALHA`. Interrupção/reinício marca execuções ativas como `FALHA/EMAIL_INTERROMPIDO`, sem retry automático. Erros internos podem aparecer como `EMAIL_ERRO_INTERNO` ou `EMAIL_OCUPADO`. O GET de uma execução falha continua retornando `200`.

## Leads

### GET /api/leads

#### Objetivo

Lista os leads persistidos e permite filtrar simultaneamente por status do funil, categoria, temperatura e execução de busca.

#### Parâmetros

| Local | Nome | Tipo | Obrigatório | Finalidade e validações |
|---|---|---|---:|---|
| Query | `status` | `StatusFunil` | Não | Filtra por `NOVO`, `QUALIFICADO`, `CONTATADO`, `GANHO` ou `PERDIDO`. |
| Query | `categoria` | `CategoriaNegocio` | Não | Filtra por um dos valores de categoria documentados no início. |
| Query | `temperatura` | `Temperatura` | Não | Filtra por `QUENTE`, `MORNO` ou `FRIO`. |
| Query | `buscaId` | `Long` positivo | Não | Inclui somente leads vinculados à execução indicada em `busca_lead`. |

Os filtros fazem correspondência exata e podem ser combinados. `buscaId` inexistente retorna lista vazia; zero ou negativo retorna 400. Os atributos do lead são os atuais, inclusive quando a busca é antiga.

Não há paginação nem limite de quantidade.

#### Resultado esperado

Retorna uma lista de `LeadResponse`, ordenada por score decrescente, com scores nulos no final; em empate, por nome crescente, com nomes nulos no final; persistindo o empate, por ID crescente.

```json
[
  {
    "id": 35,
    "googlePlaceId": "place-35",
    "nome": "Padaria Central",
    "cnpj": "12345678000190",
    "razaoSocial": "Padaria Central Ltda",
    "cnpjOrigem": "ENDERECO_EXATO",
    "categoria": "PADARIA",
    "enderecoFormatado": "Rua Central, 100",
    "website": "https://padariacentral.example/",
    "email": "contato@padariacentral.example",
    "telefone": "(27) 99999-0000",
    "telefoneNormalizado": "5527999990000",
    "whatsappUrl": "https://wa.me/5527999990000",
    "latitude": -20.3155,
    "longitude": -40.3128,
    "municipioCodigoIbge": "3205309",
    "municipioNome": "Vitória",
    "uf": "ES",
    "idhm": 0.845,
    "idhmReferencia": 2010,
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

Sem correspondências, retorna `200 OK` com `[]`. `whatsappUrl` é somente um link manual e fica `null` quando o telefone normalizado é ausente ou inválido. `email` é anulável, capturado somente após ação explícita no site oficial com confirmação de identidade. `cnpj` contém os 14 dígitos da unidade e `razaoSocial` contém o nome empresarial somente quando a correspondência local foi confiável; ambos ficam `null` quando não há identificação segura. `cnpjOrigem` é anulável e informa se o preenchimento veio de endereço exato ou do caminho de nome e endereço; CNPJs históricos podem não ter origem. A correspondência depende do subset local de CNPJ carregado: como a carga mensal é manual (o `R__` inicial é um placeholder vazio), `cnpj`/`razaoSocial` permanecem `null` enquanto não houver base carregada para o município do lead. Data-base e confiança da correspondência permanecem internas. Os cinco campos geográficos ficam `null` para leads ainda não enriquecidos ou sem correspondência no dataset municipal.

#### Status HTTP

- `200 OK` — lista retornada, inclusive quando vazia.
- `400 Bad Request` — algum filtro não corresponde ao enum esperado.
- `500 Internal Server Error` — falha inesperada na consulta ou no mapeamento.

### GET /api/leads/pagina

#### Objetivo

Retorna uma página de leads de um único status para permitir paginação independente por coluna no Kanban. O endpoint legado `GET /api/leads` permanece inalterado para os fluxos que precisam da lista completa.

#### Parâmetros

| Local | Nome | Tipo | Obrigatório | Finalidade e validações |
|---|---|---|---:|---|
| Query | `status` | `StatusFunil` | Sim | Seleciona uma das cinco etapas reais do funil. |
| Query | `categoria` | `CategoriaNegocio` | Não | Aplica o filtro exato de categoria à coluna consultada. |
| Query | `temperatura` | `Temperatura` | Não | Aplica o filtro exato de temperatura à coluna consultada. |
| Query | `buscaId` | `Long` positivo | Não | Restringe a coluna aos leads vinculados à execução indicada. |
| Query | `page` | inteiro | Não | Índice da página iniciado em zero. O padrão é `0`; aceita valores de `0` a `10000`. |
| Query | `size` | inteiro | Não | Quantidade por página. O padrão é `25`; aceita valores de `1` a `25`. |

- Os filtros podem ser combinados e são aplicados no banco antes da paginação.

#### Resultado esperado

Retorna somente os leads da página solicitada, ordenados por score decrescente, nome crescente e ID crescente como desempate estável. `totalElementos` informa o total real do status após os filtros, não apenas a quantidade da página atual.

```json
{
  "leads": [],
  "pagina": 0,
  "tamanho": 25,
  "totalElementos": 0,
  "totalPaginas": 0
}
```

Quando a página contém registros, cada item de `leads` é um `LeadResponse` completo, como no exemplo de `GET /api/leads`.

#### Status HTTP

- `200 OK` — página retornada, inclusive quando vazia.
- `400 Bad Request` — status ausente ou inválido, página fora do intervalo de 0 a 10000 ou tamanho fora do intervalo de 1 a 25.
- `500 Internal Server Error` — falha inesperada na consulta ou no mapeamento.

### GET /api/leads/{id}

#### Objetivo

Consulta um lead persistido pelo ID.

#### Parâmetros

| Local | Nome | Tipo | Obrigatório | Finalidade e validações |
|---|---|---|---:|---|
| Path | `id` | inteiro de 64 bits (`Long`) | Sim | Identificador do lead. Não possui validação explícita de valor positivo. Um número sem registro correspondente retorna 404. |

#### Resultado esperado

Retorna um `LeadResponse` com a mesma estrutura apresentada em `GET /api/leads`.

#### Status HTTP

- `200 OK` — lead retornado.
- `400 Bad Request` — `id` não pôde ser convertido para `Long`.
- `404 Not Found` — lead não encontrado (`LEAD_NAO_ENCONTRADO`).
- `500 Internal Server Error` — falha inesperada na consulta ou no mapeamento.

### PATCH /api/leads/{id}

#### Objetivo

Atualiza parcialmente os campos comerciais de um lead: status do funil, observações e/ou data do último contato. Os demais atributos, como dados da Google, score e temperatura, não são alteráveis por este endpoint.

Esta é a única rota de atualização de lead existente atualmente; não há endpoints separados com sufixos `/status`, `/observacoes` ou `/contato`.

#### Parâmetros

| Local | Nome | Tipo | Obrigatório | Finalidade e validações |
|---|---|---|---:|---|
| Path | `id` | inteiro de 64 bits (`Long`) | Sim | Identificador do lead a atualizar. Não possui validação explícita de valor positivo. |

- **Header relevante:** `Content-Type: application/json` para o request body.

#### Request body

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

#### Resultado esperado

Persiste somente os campos não nulos e retorna o `LeadResponse` completo, com a estrutura mostrada em `GET /api/leads`.

#### Status HTTP

- `200 OK` — atualização persistida e lead atualizado retornado.
- `400 Bad Request` — corpo ausente/malformado, enum/data inválido ou todos os campos nulos/omitidos.
- `404 Not Found` — lead não encontrado (`LEAD_NAO_ENCONTRADO`).
- `500 Internal Server Error` — falha inesperada de processamento ou persistência.

## Exportação

As duas exportações usam a mesma consulta e a mesma ordenação de `GET /api/leads`. Não há paginação nem limite de registros: todo o conjunto filtrado é carregado e transformado em memória.

Ambas aceitam os filtros opcionais `status`, `categoria`, `temperatura` e `buscaId`, com o mesmo significado de `GET /api/leads`. Eles podem ser combinados; valores inválidos retornam `400`.

As colunas, nesta ordem, são:

`id`, `googlePlaceId`, `nome`, `cnpj`, `razaoSocial`, `categoria`, `enderecoFormatado`, `website`, `email`, `telefone`, `telefoneNormalizado`, `whatsappUrl`, `latitude`, `longitude`, `uf`, `municipioNome`, `idhm`, `ratingGoogle`, `totalReviews`, `score`, `temperatura`, `status`, `observacoes`, `ultimoContatoEm`, `criadoEm`, `atualizadoEm`.

O `website` é o `websiteUri` oficial capturado pela Google Places, quando disponível; pode ser `null` no JSON e fica vazio nas exportações. `email` também é anulável/vazio na exportação e sua célula de texto recebe a neutralização de fórmulas já aplicada aos demais dados externos. O CNPJ é serializado com os 14 dígitos; no XLSX, a célula é explicitamente textual e preserva eventuais zeros à esquerda. CNPJ e razão social ficam em branco quando o lead não possui correspondência segura.

### GET /api/exportacao/leads.csv

#### Objetivo

Baixa os leads em um arquivo CSV UTF-8. Campos com vírgula, aspas ou quebra de linha recebem escaping CSV; valores nulos viram células vazias.

#### Resultado esperado

Retorna bytes do arquivo, inclusive quando não há leads. Nesse caso, o CSV contém somente o cabeçalho.

Exemplo do conteúdo:

```csv
id,googlePlaceId,nome,cnpj,razaoSocial,categoria,enderecoFormatado,website,email,telefone,telefoneNormalizado,whatsappUrl,latitude,longitude,uf,municipioNome,idhm,ratingGoogle,totalReviews,score,temperatura,status,observacoes,ultimoContatoEm,criadoEm,atualizadoEm
15,place-15,Padaria Central,12345678000190,Padaria Central Ltda,PADARIA,"Rua Central, 100",https://padariacentral.example/,contato@padariacentral.example,(27) 99999-0000,5527999990000,https://wa.me/5527999990000,-20.3155,-40.3128,ES,Vitória,0.845,4.8,120,95,QUENTE,CONTATADO,Retornar amanhã,2026-08-20T10:30,2026-08-19T09:00,2026-08-20T10:30
```

Headers de resposta:

- `Content-Type: text/csv;charset=UTF-8`
- `Content-Disposition: attachment; filename="leads.csv"`

#### Status HTTP

- `200 OK` — arquivo gerado, mesmo sem registros.
- `400 Bad Request` — algum filtro não corresponde ao enum esperado.
- `500 Internal Server Error` — falha inesperada na consulta ou geração do arquivo.

### GET /api/exportacao/leads.xlsx

#### Objetivo

Baixa os leads em uma planilha Excel no formato XLSX.

#### Resultado esperado

Retorna os bytes de uma pasta de trabalho XLSX com uma planilha chamada `Leads`. O cabeçalho é exibido em negrito, a primeira linha fica congelada, há filtro automático, as colunas são autoajustadas e números/datas são gravados como células tipadas. Campos textuais, inclusive valores que se parecem com fórmulas, são gravados explicitamente como texto.

Quando não há leads, ainda retorna um arquivo válido com o cabeçalho.

Headers de resposta:

- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition: attachment; filename="leads.xlsx"`

#### Status HTTP

- `200 OK` — arquivo gerado, mesmo sem registros.
- `400 Bad Request` — algum filtro não corresponde ao enum esperado.
- `500 Internal Server Error` — falha inesperada na consulta ou geração da planilha.

## Geografia

### GET /api/geografia/municipios

#### Objetivo

Retorna as geometrias municipais simplificadas que podem aparecer na região visível do mapa. O endpoint usa somente o dataset offline carregado no backend e não chama IBGE, Atlas Brasil, Google Places ou banco de dados.

#### Parâmetros

| Local | Nome | Tipo | Obrigatório | Finalidade e validações |
|---|---|---|---:|---|
| Query | `bbox` | quatro números separados por vírgula | Sim | Limites na ordem `minLng,minLat,maxLng,maxLat`, com até 160 caracteres. Exige longitudes entre -180 e 180, latitudes entre -90 e 90, valores finitos e mínimos estritamente menores que os máximos. |

- Exemplo: `bbox=-40.4,-20.4,-40.2,-20.2`.

#### Resultado esperado

Retorna `application/geo+json` com uma `FeatureCollection`. Entram no resultado os municípios cujo envelope geográfico intersecta o bbox solicitado; por isso, a geometria pode ultrapassar os limites exatos do viewport. Esse pré-filtro por envelope é deliberado e não calcula a interseção geométrica exata do polígono com o viewport. O tipo original simplificado de cada geometria é preservado como `Polygon` ou `MultiPolygon`.

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "properties": {
        "codigoIbge": "3205309",
        "nome": "Vitória",
        "uf": "ES",
        "idhm": 0.845,
        "idhmReferencia": 2010
      },
      "geometry": {
        "type": "Polygon",
        "coordinates": [[[-40.34, -20.32], [-40.31, -20.29], [-40.34, -20.32]]]
      }
    }
  ]
}
```

Um bbox válido sem envelopes municipais correspondentes retorna `200 OK` com `features: []`. Cada consulta pode retornar no máximo 1.500 municípios; uma região mais ampla é rejeitada para limitar serialização e volume de resposta. O exemplo de coordenadas acima é apenas estrutural e abreviado; a resposta real contém os anéis completos do dataset simplificado.

Header de cache:

- `Cache-Control: max-age=86400, public` — permite reutilizar por 24 horas uma resposta do mesmo bbox, pois o dataset é estático nesta versão da aplicação.

#### Status HTTP

- `200 OK` — coleção retornada, inclusive quando vazia.
- `400 Bad Request` — `bbox` ausente, malformado, não finito, fora dos limites geográficos, em ordem inválida ou abrangendo mais de 1.500 municípios.
- `500 Internal Server Error` — falha inesperada durante o processamento.

## Outros endpoints

### GET /api/health

#### Objetivo

Fornece um health check simples e estático da aplicação.

Este endpoint não testa conexão com banco, Google Places, cache ou outros componentes; portanto, `UP` indica apenas que o controller respondeu.

#### Resultado esperado

```json
{
  "status": "UP"
}
```

#### Status HTTP

- `200 OK` — resposta estática retornada.

## Tabela-resumo

| Método | Endpoint | Função |
|---|---|---|
| GET | `/api/bloqueios` | Lista os termos bloqueados. |
| POST | `/api/bloqueios` | Cadastra um termo bloqueado. |
| DELETE | `/api/bloqueios/{id}` | Remove um termo bloqueado. |
| POST | `/api/buscas` | Executa uma busca de estabelecimentos, persiste o histórico e os leads. |
| GET | `/api/buscas` | Lista o histórico de buscas. |
| GET | `/api/buscas/{id}` | Consulta uma busca e os leads encontrados naquela execução. |
| POST | `/api/buscas/{id}/informacoes` | Inicia ou recupera a pesquisa ativa de site e Instagram. |
| GET | `/api/buscas/{id}/informacoes` | Consulta o progresso persistido da pesquisa. |
| POST | `/api/buscas/{id}/emails` | Inicia ou recupera a execução ativa da captura de e-mails. |
| GET | `/api/buscas/{id}/emails` | Consulta o progresso persistido da captura de e-mails. |
| POST | `/api/buscas/{id}/cnpj` | Tenta identificar localmente o CNPJ dos leads da busca. |
| GET | `/api/leads` | Lista e filtra os leads persistidos. |
| GET | `/api/leads/pagina` | Pagina os leads de uma etapa do Kanban. |
| GET | `/api/leads/{id}` | Consulta um lead pelo ID. |
| PATCH | `/api/leads/{id}` | Atualiza parcialmente status, observações e último contato. |
| GET | `/api/exportacao/leads.csv` | Exporta os leads filtrados em CSV. |
| GET | `/api/exportacao/leads.xlsx` | Exporta os leads filtrados em XLSX. |
| GET | `/api/geografia/municipios` | Retorna os municípios do bbox como GeoJSON. |
| GET | `/api/health` | Retorna um health check estático. |
