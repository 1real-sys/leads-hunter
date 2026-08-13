# Fluxo do Leads Hunter

Este documento descreve o fluxo real do backend no estado atual do projeto. Também diferencia o que já funciona do fluxo completo planejado para o MVP.

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
    +--> PlacesSearchRequest.java
    |        |
    |        v
    |    PlacesApiClient.java
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
    v
BuscaResponse.java
    |
    v
Resposta HTTP 201 Created
```

## Fluxo atual, arquivo por arquivo

### 1. `BuscaController.java`

É o ponto de entrada HTTP. Recebe `POST /api/buscas`, converte o JSON em `BuscaRequest`, executa as validações e delega para `BuscaService.criar`. Quando o fluxo termina, responde com HTTP `201 Created` e um `BuscaResponse`.

### 2. `BuscaRequest.java`

Representa os parâmetros recebidos: endereço-base, latitude, longitude, raio em quilômetros e categorias. Valida coordenadas geográficas, exige pelo menos uma categoria e limita o raio a 20 km.

### 3. `BuscaService.java`

Coordena o caso de uso. Converte o request em `PlacesSearchRequest`, chama `PlacesApiClient.buscarProximos`, conta os estabelecimentos retornados e monta uma entidade `Busca`. Depois serializa as categorias e persiste o resumo por `BuscaRepository.saveAndFlush`.

Para cada estabelecimento, consolida resultados repetidos pelo `googlePlaceId` e consulta `LeadRepository`. Se o lead não existir, cria um registro com status `NOVO`. Se já existir, atualiza apenas nome, categoria, endereço, coordenadas, rating e total de reviews quando houver valores novos. Quando a Google fornece um telefone brasileiro válido, salva o valor original e a versão normalizada. Depois chama `ScoringService`, atualiza score e temperatura e preserva `status`, `observacoes` e `ultimoContatoEm`.

Por fim, cria um `BuscaLead` para relacionar a nova busca ao lead e registra nele o score e a temperatura daquela execução. Em seguida, converte os leads persistidos em `BuscaResponse`. Todo o processo ocorre na mesma transação; um resultado sem `googlePlaceId` interrompe e reverte a operação.

### 4. `PlacesSearchRequest.java`

É o contrato interno da integração. Transporta latitude, longitude, raio e categorias sem expor o formato JSON específico da Google ao restante da aplicação.

### 5. `PlacesApiClient.java`

Isola a comunicação com o Google Places API (New). Ele:

- lê a chave de `${GOOGLE_PLACES_API_KEY}`;
- converte categorias internas em tipos da Google, como `PADARIA -> bakery`;
- transforma o raio de quilômetros para metros;
- limita a resposta a 20 estabelecimentos;
- ordena por popularidade;
- faz um `POST` para `places:searchNearby`;
- usa uma Field Mask para pedir somente ID, nome, endereço, telefones, coordenadas, avaliação, quantidade de avaliações, situação e tipos.

Se a chave estiver ausente, a chamada é interrompida com uma exceção explicando qual variável deve ser configurada.

Os campos de telefone são `internationalPhoneNumber` e `nationalPhoneNumber`. Eles pertencem ao Nearby Search Enterprise, assim como o campo `rating` que a busca já solicitava.

### 6. `PlacesResponseMapper.java`

Converte a resposta externa para o modelo interno. Mapeia os campos da Google, trata resposta vazia e infere `CategoriaNegocio` a partir dos tipos recebidos. Por exemplo, `supermarket` vira `MERCADO` e `candy_store` vira `DOCERIA`. Para telefone, prefere o formato internacional e usa o nacional como alternativa.

### 7. `PlacesSearchResponse.java`

Representa o resultado interno da integração. Cada `PlaceResult` contém `googlePlaceId`, nome, categoria, endereço, telefone, coordenadas, rating, total de reviews, situação operacional e tipos originais da Google.

### 8. `Busca.java` e `BuscaRepository.java`

`Busca` representa o histórico da pesquisa. O repository persiste no MySQL o endereço-base, coordenadas, raio, categorias pesquisadas, total encontrado e data de criação. O schema é criado e validado pela migration `V1__criar_tabelas.sql`, com Flyway e `ddl-auto: validate`.

### 9. `TelefoneNormalizer.java`

Remove a formatação do telefone e produz somente dígitos no padrão `55 + DDD + número`. Aceita telefone fixo ou celular brasileiro em formato nacional, `+55` ou `0055`. Números ausentes, incompletos, com DDI estrangeiro, 0800 ou DDD inválido são ignorados. Um resultado inválido não apaga um telefone válido salvo anteriormente.

### 10. `ScoringService.java`

Centraliza toda a regra de pontuação. Categorias específicas recebem 30 pontos, telefone válido recebe 25, reviews podem somar até 15, rating até 15 e um estabelecimento `OPERATIONAL` recebe 10. `OUTROS` não recebe pontos de categoria. A soma máxima da regra atual é 95.

A temperatura é calculada pelo resultado: `FRIO` de 0 a 39, `MORNO` de 40 a 69 e `QUENTE` de 70 a 100.

### 11. `Lead.java` e `LeadRepository.java`

`Lead` representa um estabelecimento único. `LeadRepository.findByGooglePlaceId` é usado como chave de deduplicação. Um lead novo começa em `NOVO`; um lead existente mantém os dados comerciais definidos pelo usuário quando reaparece em outra busca.

### 12. `BuscaLead.java` e `BuscaLeadRepository.java`

Representam e persistem o relacionamento N:N. Assim, uma busca pode encontrar vários leads e o mesmo lead pode aparecer em várias buscas sem ser duplicado. `scoreNaBusca` e `temperaturaNaBusca` guardam uma cópia do resultado calculado naquela execução, mesmo que o lead seja recalculado futuramente.

### 13. `BuscaResponse.java`

É a resposta pública do endpoint. Retorna os dados da busca e uma lista resumida dos leads persistidos. O campo `id` contém o identificador do `Lead`; telefone, score e temperatura são retornados quando disponíveis.

## Estrutura relacionada

```text
src/main/java/dev/jlm/leadshunter/
├── busca/                 # Endpoint, service, entidade e histórico das buscas
├── integracao/places/     # Cliente Google, contratos e mapper externo
├── lead/                  # Entidade, repository e normalização de telefone
├── scoring/               # Cálculo centralizado de score e temperatura
├── exportacao/            # Estrutura futura de exportação
└── config/                # Endpoints e configurações gerais

src/main/resources/
├── application.yml        # Banco, Flyway e variável da chave Google
└── db/migration/          # Schema versionado

src/test/java/dev/jlm/leadshunter/
├── busca/                 # Teste unitário do fluxo de BuscaService
└── integracao/places/     # Testes do mapeamento da resposta Google
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
- Retorno dos leads persistidos, com seus IDs, na resposta HTTP.
- Teste de contexto Spring com MySQL e Flyway.
- Testes do `BuscaService` para criação, deduplicação, vínculo e preservação dos dados comerciais.
- Testes de resposta completa e vazia do `PlacesResponseMapper`.
- Testes de formatos nacionais, internacionais, ausentes e inválidos de telefone.
- Testes das faixas de score, reviews, rating e limites de temperatura.

A última execução de `./mvnw test` concluiu 33 testes sem falhas. Os testes automatizados não consomem a API da Google.

## O que ainda não está feito

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

Ainda falta:

- implementar listagem, filtros e atualização de leads; hoje `LeadService` está vazio e `GET /api/leads` devolve lista vazia;
- gerar somente o link manual de WhatsApp para telefones válidos;
- adicionar cache Caffeine e rate limit Bucket4j;
- implementar consulta ao histórico de buscas;
- implementar exportação CSV/Excel; `ExportService` ainda é um esqueleto;
- tratar de forma centralizada erros HTTP da Google, cota excedida e indisponibilidade;
- criar testes de chamada HTTP do controller, deduplicação, scoring, atualizações e erros;
- realizar uma chamada manual controlada com uma chave válida para validar a integração externa de ponta a ponta.
