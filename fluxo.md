# Fluxo do Leads Hunter

Este documento descreve o fluxo real do projeto no estado atual. O backend está concluído e os sprints FE-00 a FE-15B, além da melhoria prioritária FE-100, foram entregues no frontend; o próximo passo é o polimento integrado, a responsividade e a acessibilidade da FE-16.

## Visão geral

```text
Cliente HTTP
    |
    | POST /api/buscas
    v
BuscaController.java
    |
    v
BuscaService.java
    |
    +--> BuscaCacheKey.java
    |        |
    |        v
    |    BuscaPlacesCache.java
    |        |
    |        +--> cache hit: reutiliza PlacesSearchResponse
    |        |
    |        +--> cache miss
    |                 |
    |                 v
    +------------> PlacesSearchRequest.java
    |        |
    |        v
    |    PlacesApiClient.java
    |        |
    |        v
    |    PlacesRateLimiter.java
    |        |
    |        | POST /v1/places:searchNearby
    |        v
    |    Google Places API (New)
    |        |
    |        v
    |    PlacesResponseMapper.java
    |        |
    |        v
    |    PlacesSearchResponse.java
    |
    +--> TelefoneNormalizer.java
    |
    +--> ScoringService.java
    |        |
    |        +--> calcula score de 0 a 95
    |        +--> classifica FRIO, MORNO ou QUENTE
    |
    +--> Busca.java --> BuscaRepository.java --> MySQL
    |
    +--> LeadRepository.java
    |        |
    |        +--> cria Lead com status NOVO
    |        +--> ou atualiza dados externos do Lead existente
    |        v
    |      MySQL
    |
    +--> BuscaLead.java --> BuscaLeadRepository.java --> MySQL
    |
    +--> WhatsAppLinkGenerator.java
    |        |
    |        +--> gera URL somente para telefone normalizado válido
    |
    v
BuscaResponse.java
    |
    v
Resposta HTTP 201 Created
```

O fluxo de consulta dos leads persistidos é independente de uma nova busca:

```text
GET /api/leads, GET /api/leads/pagina, GET /api/leads/{id} ou PATCH /api/leads/{id}
    |
    v
LeadController.java
    |
    v
LeadService.java --> LeadRepository.java --> MySQL
    |
    +--> WhatsAppLinkGenerator.java
    |
    v
LeadResponse.java --> Resposta HTTP 200 OK
```

O histórico das buscas também pode ser consultado sem chamar a Google novamente:

```text
GET /api/buscas ou GET /api/buscas/{id}
    |
    v
BuscaController.java
    |
    v
BuscaService.java
    |
    +--> BuscaRepository.java --> resumo da busca
    |
    +--> BuscaLeadRepository.java --> Lead atual + score/temperatura da execução
    |
    +--> WhatsAppLinkGenerator.java
    v
BuscaResumoResponse.java ou BuscaDetalheResponse.java --> Resposta HTTP 200 OK
```

A exportação CSV e Excel consulta os mesmos leads e filtros do Kanban:

```text
GET /api/exportacao/leads.csv?status=...&categoria=...&temperatura=...
    |
    v
ExportController.java
    |
    v
ExportService.java --> LeadService.java --> LeadRepository.java --> MySQL
    |
    +--> escaping CSV e URL manual de WhatsApp
    v
Arquivo UTF-8 leads.csv --> Resposta HTTP 200 OK

GET /api/exportacao/leads.xlsx?status=...&categoria=...&temperatura=...
    |
    v
ExportController.java --> ExportService.java --> Apache POI
    |
    v
Arquivo leads.xlsx --> Resposta HTTP 200 OK
```

## Fluxo atual, arquivo por arquivo

### 1. `BuscaController.java`

É o ponto de entrada HTTP. Recebe `POST /api/buscas`, converte o JSON em `BuscaRequest`, executa as validações e delega para `BuscaService.criar`. Quando o fluxo termina, responde com HTTP `201 Created` e um `BuscaResponse`.

Também expõe `GET /api/buscas`, que lista os resumos do histórico do mais recente para o mais antigo, e `GET /api/buscas/{id}`, que abre uma execução com os leads encontrados. Uma busca inexistente retorna HTTP `404 Not Found` por meio de `BuscaNaoEncontradaException`.

### 2. `BuscaRequest.java`

Representa os parâmetros recebidos: endereço-base, latitude, longitude, raio em quilômetros e categorias. Valida coordenadas geográficas, exige pelo menos uma categoria e limita o raio a 20 km.

### 3. `BuscaService.java`

Coordena o caso de uso. Gera uma `BuscaCacheKey` e consulta `BuscaPlacesCache`. Se não houver resultado recente, converte o request em `PlacesSearchRequest` e chama `PlacesApiClient.buscarProximos`; se houver, reutiliza o `PlacesSearchResponse` sem nova chamada à Google. Depois conta os estabelecimentos, monta a entidade `Busca`, serializa as categorias e persiste o resumo por `BuscaRepository.saveAndFlush`.

O cache guarda somente a resposta externa. Cada requisição continua criando uma nova `Busca`, processando os leads e registrando os vínculos `BuscaLead`, inclusive quando ocorre cache hit.

Para cada estabelecimento, consolida resultados repetidos pelo `googlePlaceId` e consulta `LeadRepository`. Se o lead não existir, cria um registro com status `NOVO`. Se já existir, atualiza apenas nome, categoria, endereço, coordenadas, rating e total de reviews quando houver valores novos. Quando a Google fornece um telefone brasileiro válido, salva o valor original e a versão normalizada. Depois chama `ScoringService`, atualiza score e temperatura e preserva `status`, `observacoes` e `ultimoContatoEm`.

Por fim, cria um `BuscaLead` para relacionar a nova busca ao lead e registra nele o score e a temperatura daquela execução. Em seguida, converte os leads persistidos em `BuscaResponse`. Todo o processo ocorre na mesma transação; um resultado sem `googlePlaceId` interrompe e reverte a operação.

Nas consultas do histórico, `listarHistorico` lê as buscas já ordenadas por `criadoEm` decrescente. `buscarHistoricoPorId` combina o resumo persistido em `Busca` com os vínculos de `BuscaLead`. O detalhe usa `scoreNaBusca` e `temperaturaNaBusca` para preservar o retrato daquela execução, enquanto status, observações e último contato refletem o estado comercial atual do `Lead`. Essas operações usam transações somente de leitura e não acionam cache nem Google Places.

### 4. `BuscaCacheKey.java` e `BuscaPlacesCache.java`

`BuscaCacheKey` identifica buscas equivalentes usando latitude e longitude arredondadas para quatro casas decimais, raio e categorias distintas em ordem alfabética. O texto de `enderecoBase` não participa da chave.

`BuscaPlacesCache` usa Caffeine para manter `PlacesSearchResponse` em memória. Por padrão, cada entrada expira após 30 minutos e o cache aceita até 100 buscas. Os valores podem ser alterados por `BUSCA_CACHE_EXPIRACAO_MINUTOS` e `BUSCA_CACHE_TAMANHO_MAXIMO`. Falhas do carregador não são armazenadas.

### 5. `PlacesSearchRequest.java`

É o contrato interno da integração. Transporta latitude, longitude, raio e categorias sem expor o formato JSON específico da Google ao restante da aplicação.

### 6. `PlacesApiClient.java`

Isola a comunicação com o Google Places API (New). Ele:

- lê a chave de `${GOOGLE_PLACES_API_KEY}`;
- converte categorias internas em tipos da Google, como `PADARIA -> bakery`;
- transforma o raio de quilômetros para metros;
- limita a resposta a 20 estabelecimentos;
- ordena por popularidade;
- solicita uma permissão ao `PlacesRateLimiter`;
- faz um `POST` para `places:searchNearby`;
- usa uma Field Mask para pedir somente ID, nome, endereço, telefones, coordenadas, avaliação, quantidade de avaliações, situação e tipos.

Se a chave estiver ausente, a chamada é interrompida com `PlacesApiConfigurationException`, explicando qual variável deve ser configurada. Respostas HTTP da Google são traduzidas para exceções do domínio da integração: `429` vira cota excedida, `401/403` viram falha de configuração, outros `4xx` viram consulta rejeitada e respostas `5xx` ou falhas de rede viram indisponibilidade. Se o corpo não puder ser convertido, o cliente sinaliza resposta inválida. Os detalhes brutos da resposta externa permanecem somente na causa interna da exceção.

Os campos de telefone são `internationalPhoneNumber` e `nationalPhoneNumber`. Eles pertencem ao Nearby Search Enterprise, assim como o campo `rating` que a busca já solicitava.

### 7. `PlacesRateLimiter.java`

Usa Bucket4j para limitar chamadas HTTP reais à Google. Por padrão, permite uma capacidade de 10 requisições com reposição gradual ao longo de 60 segundos. Os valores podem ser alterados por `GOOGLE_PLACES_RATE_LIMIT_REQUISICOES` e `GOOGLE_PLACES_RATE_LIMIT_PERIODO_SEGUNDOS`.

Quando não há permissão disponível, lança `PlacesRateLimitExceededException` e a API responde com HTTP `429 Too Many Requests`. Cache hit não chega ao `PlacesApiClient` e, portanto, não consome permissão. O bucket fica apenas em memória e é reiniciado junto com a aplicação; ele protege contra rajadas, não controla sozinho um orçamento mensal.

### 8. `PlacesResponseMapper.java`

Converte a resposta externa para o modelo interno. Mapeia os campos da Google, trata resposta vazia e infere `CategoriaNegocio` a partir dos tipos recebidos. Por exemplo, `supermarket` vira `MERCADO` e `candy_store` vira `DOCERIA`. Para telefone, prefere o formato internacional e usa o nacional como alternativa.

### 9. `PlacesSearchResponse.java`

Representa o resultado interno da integração. Cada `PlaceResult` contém `googlePlaceId`, nome, categoria, endereço, telefone, coordenadas, rating, total de reviews, situação operacional e tipos originais da Google.

### 10. `Busca.java` e `BuscaRepository.java`

`Busca` representa o histórico da pesquisa. O repository persiste no MySQL o endereço-base, coordenadas, raio, categorias pesquisadas, total encontrado e data de criação. O schema é criado e validado pela migration `V1__criar_tabelas.sql`, com Flyway e `ddl-auto: validate`.

### 11. `TelefoneNormalizer.java` e `WhatsAppLinkGenerator.java`

Remove a formatação do telefone e produz somente dígitos no padrão `55 + DDD + número`. Aceita telefone fixo ou celular brasileiro em formato nacional, `+55` ou `0055`. Números ausentes, incompletos, com DDI estrangeiro, 0800 ou DDD inválido são ignorados. Um resultado inválido não apaga um telefone válido salvo anteriormente.

`WhatsAppLinkGenerator` recebe apenas o telefone já normalizado e gera uma URL manual no formato `https://wa.me/55DDDNUMERO`. Para telefone ausente, formatado ou inválido, retorna `null`. A aplicação somente entrega o link para o usuário abrir; não existe disparo automático ou em massa.

### 12. `ScoringService.java`

Centraliza toda a regra de pontuação. Categorias específicas recebem 30 pontos, telefone válido recebe 25, reviews podem somar até 15, rating até 15 e um estabelecimento `OPERATIONAL` recebe 10. `OUTROS` não recebe pontos de categoria. A soma máxima da regra atual é 95.

A temperatura é calculada pelo resultado: `FRIO` de 0 a 39, `MORNO` de 40 a 69 e `QUENTE` de 70 a 100.

### 13. `Lead.java` e `LeadRepository.java`

`Lead` representa um estabelecimento único. `LeadRepository.findByGooglePlaceId` é usado como chave de deduplicação. Um lead novo começa em `NOVO`; um lead existente mantém os dados comerciais definidos pelo usuário quando reaparece em outra busca.

### 14. `LeadController.java`, `LeadService.java` e `LeadResponse.java`

Expõem a leitura e a atualização comercial dos leads já persistidos. `GET /api/leads` lista todos e aceita os filtros opcionais `status`, `categoria` e `temperatura`, que podem ser combinados. `GET /api/leads/pagina` exige um status e pagina essa etapa no banco com `page`, `size` limitado a 25 e os filtros opcionais de categoria e temperatura, retornando conteúdo, página e totais reais. As consultas usam Query by Example e ordenam por maior score, nome e ID como desempate estável. `GET /api/leads/{id}` retorna um lead específico ou HTTP `404 Not Found` por meio de `LeadNaoEncontradoException`.

`PATCH /api/leads/{id}` recebe `AtualizarLeadRequest` e altera somente os campos informados entre `status`, `observacoes` e `ultimoContatoEm`. O payload vazio é rejeitado por Bean Validation. Os demais atributos do lead são preservados, e a resposta contém o estado persistido atualizado.

`LeadResponse` mantém a entidade JPA fora do contrato HTTP e apresenta os dados externos, a classificação, os campos comerciais e `whatsappUrl` quando houver telefone normalizado válido. As consultas são executadas em transações somente de leitura, enquanto a atualização usa uma transação de escrita.

### 15. `BuscaLead.java` e `BuscaLeadRepository.java`

Representam e persistem o relacionamento N:N. Assim, uma busca pode encontrar vários leads e o mesmo lead pode aparecer em várias buscas sem ser duplicado. `scoreNaBusca` e `temperaturaNaBusca` guardam uma cópia do resultado calculado naquela execução, mesmo que o lead seja recalculado futuramente. A consulta do detalhe carrega o `Lead` junto com cada vínculo por `EntityGraph` e ordena os resultados pelo score histórico decrescente.

### 16. `BuscaResponse.java`

É a resposta pública do endpoint. Retorna os dados da busca e uma lista resumida dos leads persistidos. O campo `id` contém o identificador do `Lead`; telefone, `whatsappUrl`, score e temperatura são retornados quando disponíveis.

### 17. `BuscaResumoResponse.java` e `BuscaDetalheResponse.java`

São os contratos públicos do histórico. O resumo contém os parâmetros, total e data da busca. O detalhe acrescenta os leads vinculados, o link manual de WhatsApp, o score e a temperatura daquela execução, além dos campos comerciais atuais. As categorias persistidas como texto são novamente apresentadas como valores de `CategoriaNegocio`.

### 18. `ExportController.java` e `ExportService.java`

Expõem `GET /api/exportacao/leads.csv` e `GET /api/exportacao/leads.xlsx`. Ambos aceitam os filtros opcionais `status`, `categoria` e `temperatura`, reutilizam a ordenação e o mapeamento de `LeadService` e exportam as colunas externas e comerciais do `Lead`. O CSV é UTF-8 com escaping de vírgulas, aspas e quebras de linha; o Excel é gerado com Apache POI, cabeçalho em negrito, filtro automático, primeira linha congelada, autoajuste de colunas e células tipadas para números e datas. O link de WhatsApp exportado continua sendo apenas manual.

### 19. `ApiExceptionHandler.java` e `ApiErrorResponse.java`

`ApiExceptionHandler` centraliza a conversão das falhas de integração, validação de payload, parâmetros inválidos e exceções de recurso não encontrado em um contrato JSON único. Toda resposta contém `timestamp`, `status`, `codigo`, `mensagem` e `path`. Falhas de cota ou do rate limit local retornam `429`; chave ausente ou indisponibilidade retornam `503`; resposta inválida ou consulta rejeitada pela Google retornam `502`; validações e requisições malformadas retornam `400`; buscas e leads inexistentes retornam `404`. Exceções inesperadas são registradas apenas com método, rota e tipo da exceção e retornam `500 ERRO_INTERNO` com mensagem genérica. O contrato não expõe stack trace, corpo bruto da Google, mensagens internas ou credenciais.

## Estrutura relacionada

```text
src/main/java/dev/jlm/leadshunter/
├── busca/                 # Endpoint, service, cache, entidades e histórico
├── integracao/places/     # Cliente Google, rate limit, contratos e mapper
├── lead/                  # Gestão de leads, telefone e link manual de WhatsApp
├── scoring/               # Cálculo centralizado de score e temperatura
├── exportacao/            # Exportação CSV e Excel
└── config/                # Endpoints, configurações e tratamento HTTP de erros

src/main/resources/
├── application.yml        # Banco, Flyway e variável da chave Google
└── db/migration/          # Schema versionado

src/test/java/dev/jlm/leadshunter/
├── busca/                 # Testes do fluxo de BuscaService e integração JPA
├── integracao/places/     # Testes do cliente, mapper e rate limit da Google
├── config/                # Testes do contrato HTTP de erros
├── lead/                  # Testes de consulta, atualização e telefone
└── scoring/               # Testes das regras de score e temperatura
```

## O que já está implementado e testado

- Entidades `Busca`, `Lead` e `BuscaLead`, repositories e migration inicial.
- Relacionamento N:N modelado entre `Busca` e `Lead` por `BuscaLead`.
- Restrição única de `Lead.googlePlaceId` no banco.
- Endpoint `POST /api/buscas` com validação de entrada.
- Chamada real preparada para o Nearby Search da Google Places API (New).
- Chave externa por variável de ambiente, sem segredo no código.
- Conversão de categorias do domínio para tipos da Google.
- Mapeamento da resposta externa para `PlacesSearchResponse`.
- Persistência do resumo da busca e de `totalEncontrados`.
- Persistência de cada estabelecimento como `Lead`.
- Deduplicação de leads e de resultados repetidos por `googlePlaceId`.
- Criação do relacionamento N:N por `BuscaLead`.
- Status inicial `NOVO` para leads inéditos.
- Atualização dos dados externos de leads existentes.
- Preservação de `status`, `observacoes` e `ultimoContatoEm` em novas buscas.
- Obtenção dos telefones nacional e internacional pela Google Places API.
- Normalização de telefone brasileiro para `55 + DDD + número`.
- Preservação do telefone existente quando a nova resposta não contém um número válido.
- Cálculo centralizado de score por categoria, telefone, reviews, rating e funcionamento.
- Classificação de temperatura em `FRIO`, `MORNO` ou `QUENTE`.
- Atualização de score e temperatura no `Lead`.
- Registro do score e da temperatura da execução em `BuscaLead`.
- Cache Caffeine dos resultados recentes da Google Places API.
- Chave de cache por coordenadas arredondadas, raio e categorias ordenadas.
- Persistência de um novo histórico mesmo quando o resultado vem do cache.
- Rate limit Bucket4j aplicado somente às chamadas externas reais.
- Resposta HTTP 429 quando a capacidade temporária é esgotada.
- Retorno dos leads persistidos, com seus IDs, na resposta HTTP.
- Listagem do histórico por `GET /api/buscas`, em ordem decrescente de criação.
- Consulta detalhada por `GET /api/buscas/{id}`, com resposta 404 para ID inexistente.
- Uso do score e da temperatura de `BuscaLead` no detalhe histórico.
- Exposição dos dados comerciais atuais e do link manual de WhatsApp nos leads do histórico.
- Exportação CSV por `GET /api/exportacao/leads.csv`.
- Exportação Excel por `GET /api/exportacao/leads.xlsx` com Apache POI.
- Filtros de status, categoria e temperatura aplicados também na exportação.
- Geração de CSV UTF-8 com escaping de vírgulas, aspas e quebras de linha.
- Header de download `Content-Disposition` com o arquivo `leads.csv`.
- Planilha Excel com cabeçalho formatado, filtro, congelamento e dados tipados.
- Tratamento centralizado das falhas da Google Places e contrato JSON uniforme de erros.
- Distinção entre rate limit local, cota externa, configuração rejeitada, indisponibilidade e resposta inválida.
- Listagem dos leads persistidos por `GET /api/leads`.
- Paginação dos leads por status em `GET /api/leads/pagina`, com páginas de até 25 registros e total filtrado.
- Filtros combináveis por status, categoria e temperatura.
- Consulta individual por `GET /api/leads/{id}`, com resposta 404 para ID inexistente.
- Atualização parcial de status, observações e último contato por `PATCH /api/leads/{id}`.
- Preservação dos campos omitidos no payload de atualização.
- Validação que rejeita uma atualização sem nenhum campo informado.
- Contrato HTTP próprio em `LeadResponse`, sem exposição direta da entidade JPA.
- Geração de `whatsappUrl` somente para telefone brasileiro normalizado válido.
- Exposição do link manual tanto nas respostas de busca quanto nas respostas de lead.
- Ausência de qualquer envio automático ou em massa pelo WhatsApp.
- Teste de contexto Spring com MySQL e Flyway.
- Testes do `BuscaService` para criação, deduplicação, vínculo e preservação dos dados comerciais.
- Teste de integração JPA com MySQL e Flyway para persistência N:N, deduplicação e rollback transacional.
- Validação da preservação comercial e do snapshot de score/temperatura em buscas repetidas.
- Validação da restrição única de `googlePlaceId` diretamente no banco.
- Testes de resposta completa e vazia do `PlacesResponseMapper`.
- Testes de formatos nacionais, internacionais, ausentes e inválidos de telefone.
- Testes das faixas de score, reviews, rating e limites de temperatura.
- Testes da chave, configuração e reutilização do cache sem perda de histórico.
- Testes de capacidade, bloqueio e status HTTP do rate limit.
- Testes da passagem obrigatória do `PlacesApiClient` pelo rate limit.
- Testes do serviço de leads para filtros, ordenação, mapeamento e ID inexistente.
- Testes da atualização comercial e da validação de `AtualizarLeadRequest`.
- Testes de geração e rejeição do link manual do WhatsApp.
- Testes do histórico para ordenação, conversão de categorias, snapshot de scoring e busca inexistente.
- Testes HTTP de listagem, detalhe e resposta 404 dos endpoints de histórico.
- Testes HTTP do `POST /api/buscas`, incluindo resposta `201` e rejeição de payload inválido.
- Testes HTTP do `LeadController` para filtros, consulta, atualização parcial, 404 e validações.
- Testes HTTP do contrato de erro para cota excedida, indisponibilidade, resposta inválida e recurso inexistente.
- Tratamento HTTP uniforme para Bean Validation, JSON ilegível e parâmetros de enum inválidos.
- Fallback HTTP seguro para exceções inesperadas, com resposta `500 ERRO_INTERNO` sem detalhes internos.
- Testes HTTP para rate limit local, configuração ausente, consulta rejeitada, erro inesperado, JSON malformado, corpo ausente e enum inválido no payload.
- Testes do cliente Places com respostas HTTP simuladas para cota, autorização, indisponibilidade e payload inválido.
- Testes do conteúdo CSV, escaping, filtros, arquivo vazio e headers HTTP de download.
- Testes de leitura da planilha Excel gerada, tipos de célula e headers HTTP de download.
- Chamada externa controlada com Google Places API (New), retornando e persistindo leads reais.

A última execução de `./mvnw test`, com um agente Byte Buddy informado somente em runtime para compatibilidade do Mockito com a JVM Java 25 do ambiente de validação, concluiu 98 testes sem falhas, incluindo o contexto Spring com MySQL, Flyway e a integração JPA. Os testes automatizados não abrem o WhatsApp nem consomem a API da Google.

A validação manual de ponta a ponta retornou HTTP `201`, encontrou 18 estabelecimentos, persistiu a busca e os leads e expôs os links manuais de WhatsApp. A leitura posterior de um lead persistido retornou HTTP `200`. Os novos endpoints também foram validados contra o MySQL local: a listagem retornou a busca existente com HTTP `200`, o detalhe retornou seus 18 vínculos ordenados pelo score histórico com HTTP `200` e um ID inexistente retornou HTTP `404`. A exportação CSV filtrada por `status=NOVO` retornou HTTP `200`, `Content-Type: text/csv;charset=UTF-8`, nome de download `leads.csv` e 18 linhas de dados. A exportação Excel com o mesmo filtro retornou HTTP `200`, `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, nome `leads.xlsx` e arquivo reconhecido como Excel 2007+ com ZIP íntegro.

## Fechamento técnico do backend

O trecho de enriquecimento e scoring da busca agora funciona desta forma:

```text
PlacesSearchResponse
    |
    +--> normalização de telefone
    +--> ScoringService: score e temperatura
    +--> atualização de score/temperatura no Lead
    +--> registro do score/temperatura no BuscaLead
    v
BuscaResponse com leads persistidos e pontuados
```

O tratamento de erros e os principais caminhos de entrada inválida estão cobertos. O fechamento técnico do backend foi concluído após as seguintes validações:

- `./mvnw -DargLine=-javaagent:/home/jlm1real/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test`: 98 testes executados, sem falhas, erros ou testes ignorados.
- `./mvnw -DskipTests package`: build do JAR executável concluído com sucesso.
- Inicialização local da aplicação: Tomcat subiu na porta 8080, conexão com MySQL foi estabelecida, a migration foi validada pelo Flyway e o schema permaneceu atualizado na versão 1.
- Smoke test sem nova chamada à Google Places: `GET /api/buscas`, `GET /api/leads?status=NOVO`, exportação CSV e exportação XLSX retornaram HTTP 200; foram confirmados 18 leads, 19 linhas no CSV e arquivo Excel 2007+ íntegro.

O backend do MVP está concluído e validado para uso local/portfólio. O próximo ciclo do produto é o frontend das fases 5 a 7 do roadmap; não há nova pendência funcional de backend dentro do escopo atual.

## Planejamento do próximo ciclo — frontend

O workspace agora possui a aplicação Angular em `frontend/`, com `angular.json`, `package.json`, `src/app`, routing, SCSS, testes e sem SSR. Node.js `v26.7.0` e npm `12.0.2` estão disponíveis; Angular CLI global não está instalado e a CLI local `22.1.6` foi usada para instalar Angular `22.1.4`. O projeto usa o proxy de desenvolvimento `/api` para `http://localhost:8080`. Depois do bootstrap, o desenvolvimento seguirá `angular-developer` e `leadradar-frontend`.

O planejamento detalhado e os critérios de aceite estão em [FRONTEND_SPRINTS.md](FRONTEND_SPRINTS.md). Os sprints foram divididos para entregar incrementos pequenos, completos e validados:

| Sprint | Entrega | Status |
| --- | --- | --- |
| FE-00 | Bootstrap Angular e execução local | CONCLUÍDO |
| FE-01 | Contratos TypeScript e base HTTP | CONCLUÍDO |
| FE-02 | Shell, navegação e rotas | CONCLUÍDO |
| FE-03 | Mapa Leaflet interativo | CONCLUÍDO |
| FE-04 | Formulário de busca sincronizado ao mapa | CONCLUÍDO |
| FE-05 | Execução da busca pela API | CONCLUÍDO |
| FE-06 | Apresentação dos resultados da busca | CONCLUÍDO |
| FE-07 | Consulta e filtros de leads | CONCLUÍDO |
| FE-08 | Kanban somente leitura e cards | CONCLUÍDO |
| FE-09 | Drag-and-drop com persistência de status | CONCLUÍDO |
| FE-10 | Detalhe do lead e WhatsApp manual | CONCLUÍDO |
| FE-11 | Observações e último contato | CONCLUÍDO |
| FE-12 | Lista do histórico de buscas | CONCLUÍDO |
| FE-13 | Detalhe de uma busca anterior | CONCLUÍDO |
| FE-14 | Downloads CSV e XLSX | CONCLUÍDO |
| FE-15A | Shell operacional e workspace da Busca | CONCLUÍDO |
| FE-15B | Adaptação das áreas ao workspace operacional | CONCLUÍDO |
| FE-100 | Scroll e paginação independentes no Kanban | CONCLUÍDO |
| FE-16 | Polimento integrado, responsividade e acessibilidade | PENDENTE |
| FE-17 | Testes de fluxo e fechamento do MVP | PENDENTE |

Decisões preservadas para o ciclo:

- SPA local, standalone, TypeScript strict e sem SSR;
- proxy de desenvolvimento `/api` para o backend local, sem alterar CORS apenas para desenvolvimento;
- ausência de autenticação, multiusuário e deploy, conforme o escopo atual;
- uso dos endpoints reais documentados em `API.md`;
- um único `PATCH /api/leads/{id}` para status, observações e último contato;
- Leaflet para mapa e Angular CDK para o Kanban;
- WhatsApp somente por abertura manual de `whatsappUrl`;
- downloads CSV/XLSX produzidos pelo backend;
- Signals para estado simples e RxJS somente quando o fluxo assíncrono justificar;
- sem biblioteca de estado global, dashboard avançado ou abstrações sem necessidade.

O FE-01 deixou disponível `provideHttpClient()`, os contratos TypeScript da API em `frontend/src/app/shared/models`, o prefixo relativo `/api` em `frontend/src/app/core/api/api-routes.ts` e o mapeamento seguro de falhas HTTP em `frontend/src/app/core/api/api-error-message.ts`. Após revisão, `AtualizarLeadRequest` passou a exigir em compilação ao menos um campo não nulo, e os testes passaram a exercer os DTOs aninhados das buscas. O FE-02 adicionou o shell navegável, as rotas lazy de Busca, Kanban e Histórico, placeholders acessíveis e o fallback de rota desconhecida. O FE-03 adicionou o mapa Leaflet reutilizável à Busca, com ponto central tipado, marcador arrastável, seleção por clique ou teclado, círculo de raio atualizado incrementalmente, tiles OpenStreetMap atribuídos e cleanup no ciclo de vida. O FE-04 adicionou o formulário com Signal Forms para endereço descritivo, coordenadas, raio e múltiplas categorias, usando as restrições reais do backend e um único estado sincronizado com o mapa. O FE-05 conectou esse formulário ao `POST /api/buscas` por meio de `BuscaApi`, bloqueia chamadas simultâneas, conserva a `BuscaResponse` completa e representa `loading`, sucesso com dados, sucesso vazio e erro. Os códigos `400`, `429`, `502`, `503` e `500` recebem mensagens seguras e permitem nova submissão após a falha. O smoke pelo proxy local confirmou o `503 GOOGLE_PLACES_CONFIGURATION` esperado com a chave explicitamente vazia, sem chamada à Google. O FE-06 transformou a resposta confirmada em um resumo da busca e uma lista responsiva de leads, omite dados opcionais ausentes, identifica a temperatura também por texto, disponibiliza WhatsApp manual somente com a URL recebida do backend e mantém o estado vazio concluído com orientação e acesso ao Kanban. O FE-07 conectou a rota Kanban ao `GET /api/leads` por meio de `LeadApi`, com filtros tipados e combináveis de status, categoria e temperatura. A consulta ocorre ao entrar e ao aplicar, limpar ou repetir filtros; chamadas simultâneas são bloqueadas, a ordem da API é preservada e uma falha mantém a última lista válida visível com feedback explícito. O FE-08 organiza a lista filtrada nas cinco etapas reais do funil, com contadores derivados e cards compactos que omitem dados ausentes. Status nulo ou inesperado fica em uma coluna condicional `Sem etapa`, sem ser confundido com `NOVO`. O quadro conserva os filtros e diferencia a ausência global de resultados de uma etapa vazia, com navegação horizontal em telas estreitas. O FE-09 conecta as colunas com Angular CDK e persiste cada mudança pela rota única `PATCH /api/leads/{id}`. O estado é otimista enquanto a operação está pendente, confirmado pela resposta completa do backend e revertido com feedback seguro em caso de falha. Cada lead fica bloqueado durante seu próprio salvamento, os filtros permanecem selecionados e botões de etapa anterior/próxima oferecem alternativa ao drag por teclado. O FE-10 abre um painel lateral de detalhe ao acionar o título de um card do Kanban, sem nova chamada HTTP: o painel usa o `LeadResponse` completo já carregado, exibe os dados externos, a classificação e os dados comerciais, omite campos nulos e abre o WhatsApp manual em nova aba somente quando o backend fornece `whatsappUrl`. O painel é um diálogo acessível com foco preso, abre/fecha por teclado e devolve o foco ao botão que o abriu. O FE-11 transformou a área de dados comerciais do detalhe em um editor explícito: o usuário edita observações e o último contato, e o painel envia somente os campos alterados para `PATCH /api/leads/{id}`, usando a resposta confirmada para atualizar o próprio detalhe e a lista do Kanban. Observações podem ser limpas com string vazia; limpar o campo de último contato não remove o registro, por limitação do contrato atual, e isso é explicado na interface. Falhas preservam o texto digitado, e tentar fechar com alterações não salvas exibe um aviso e mantém a edição até salvar ou cancelar. O FE-12 conectou a rota Histórico ao `GET /api/buscas`, preserva a ordem do backend e apresenta data local, endereço-base, categorias, raio e total encontrado em uma tabela navegável. A tela diferencia carregamento, lista vazia, erro com nova tentativa e dados disponíveis; endereços ausentes recebem fallback neutro. Cada item abre `/historico/:id`, mantendo o contexto da navegação. O FE-13 substituiu a transição dessa rota pelo detalhe real de `GET /api/buscas/{id}`: o resumo preserva os parâmetros registrados, e a tabela mantém a ordem do backend enquanto separa explicitamente score e temperatura daquela execução dos dados comerciais atuais. O WhatsApp continua manual e só aparece como link quando a API fornece uma URL. ID inválido é rejeitado antes de qualquer chamada, e busca inexistente, execução sem leads e falha genérica possuem estados próprios e retorno ao histórico. O FE-14 conectou o Kanban às exportações binárias de CSV e XLSX usando os filtros selecionados. Os nomes e tipos dos arquivos respeitam os headers do backend com fallbacks seguros, URLs temporárias são sempre liberadas, cada formato possui carregamento independente e respostas de erro em blob são convertidas para a mensagem segura da API sem gerar arquivo. O FE-15A substituiu o shell centralizado por uma sidebar persistente com as três áreas de trabalho e removeu o header e o footer permanentes. Na Busca, os parâmetros ocupam uma região operacional própria e compacta, enquanto o mapa usa a maior parte do workspace e da altura da viewport; mapa, formulário e estados existentes permanecem sincronizados. Os resultados continuam no contexto da Busca, agora em uma grade mais densa, sem mudança nos contratos ou no fluxo HTTP. O FE-15B adaptou Kanban e Histórico à mesma estrutura: cabeçalhos e controles ficaram compactos, o quadro passou a usar toda a largura útil com cinco colunas simultâneas no desktop e rolagem localizada em larguras menores, e as tabelas de histórico foram adensadas. Drag-and-drop, atualização de status, drawer, filtros e exportações foram preservados. A FE-100 passou a consultar cada status por uma página real de até 25 registros, mantém totais e páginas independentes, reconsulta somente origem e destino após uma movimentação e limita o board à altura restante do workspace, com scroll vertical próprio em cada coluna. Como polimento pontual anterior à FE-16 completa, o drawer do Kanban foi reorganizado em blocos semânticos compactos para resumo comercial, estabelecimento, dados comerciais e metadados, preservando abertura, foco, fechamento, edição e WhatsApp.

O refinamento visual posterior do drawer corrigiu o espaçamento interno efetivo, ampliou moderadamente sua largura, removeu divisórias estruturais, alinhou os dados do estabelecimento em linhas label/valor, ampliou a área de observações e ancorou os metadados no rodapé, sem alterar abertura, fechamento, foco, edição, WhatsApp ou contratos.

A tela Busca agora mostra o andamento junto aos parâmetros: o botão fica desabilitado com spinner e texto de carregamento, enquanto os estados de sucesso, vazio e erro recebem mensagens distintas. Sucessos exibem a quantidade retornada e atalhos para Kanban e Histórico; os parâmetros continuam preservados e o `POST /api/buscas` permanece inalterado.

### Próximo passo

Os sprints **FE-00** a **FE-15B** e a melhoria prioritária **FE-100** estão concluídos e validados. O próximo passo é executar o **FE-16 — Polimento integrado, responsividade e acessibilidade**, sem adicionar funcionalidades; o FE-17 permanece pendente.

## Padrão de boilerplate com Lombok

O backend utiliza Lombok de forma seletiva, mantendo a configuração já existente no Maven e sem alterar contratos HTTP ou regras de negócio.

- `Busca`, `BuscaLead` e `Lead` usam `@Getter`, `@Setter` e `@NoArgsConstructor` para substituir os accessors manuais e preservar o construtor exigido pelo JPA.
- `BuscaController`, `BuscaService`, `LeadController`, `LeadService`, `ExportController` e `ExportService` usam `@RequiredArgsConstructor` para injetar dependências obrigatórias `final`.
- `ApiExceptionHandler` usa `@Slf4j` para eliminar a declaração manual do logger.
- DTOs e contratos internos que já são `record` permanecem records.
- Entidades JPA não usam `@Data`, `@Builder`, `@EqualsAndHashCode` ou `@ToString`, evitando recursão em relacionamentos, lazy loading inesperado e comparação por campos mutáveis.
