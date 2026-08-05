# Testes — LeadRadar Cartão

Este arquivo define a melhor estratégia de testes para o projeto **LeadRadar Cartão**, considerando que a aplicação roda localmente, usa backend Java 25 + Spring Boot 4.x, frontend Angular, MySQL local e integração externa com Google Places API.

A ideia não é testar tudo de forma exagerada desde o início. O foco deve ser garantir que o fluxo principal funcione bem, que chamadas pagas à API externa não sejam desperdiçadas e que o Kanban preserve corretamente o estado comercial dos leads.

---

## Objetivo dos testes

Os testes devem garantir que:

- uma busca por localização, raio e categorias retorna leads válidos;
- leads repetidos não sejam duplicados no banco;
- o vínculo entre `busca` e `lead` seja preservado via `busca_lead`;
- o score e a temperatura sejam calculados sempre da mesma forma;
- o status do funil seja atualizado corretamente;
- dados comerciais, como observações e último contato, não sejam perdidos;
- o botão/link de WhatsApp só apareça quando houver telefone válido;
- cache e rate limiting evitem chamadas desnecessárias à Google Places API;
- exportação CSV/Excel gere dados coerentes;
- frontend e backend conversem corretamente nos fluxos principais.

---

## Estratégia geral

A melhor abordagem é dividir os testes em quatro níveis:

1. **Testes unitários**  
   Testam regras isoladas, sem banco, sem HTTP real e sem Google Places real.

2. **Testes de integração do backend**  
   Testam banco, repositories, migrations, services e endpoints usando ambiente controlado.

3. **Testes do frontend**  
   Testam componentes, services Angular, formulários, Kanban e comportamento visual importante.

4. **Testes ponta a ponta / manuais guiados**  
   Validam a aplicação rodando localmente com backend, frontend e MySQL juntos.

Não chame a Google Places API real em testes automatizados comuns. Use mocks/fakes para evitar custo, instabilidade e dependência externa.

---

## Prioridade de testes no MVP

### Prioridade alta

Esses testes devem existir desde cedo:

- `ScoringService`
- `PlacesResponseMapper`
- normalização de telefone
- geração de link `wa.me`
- deduplicação por `googlePlaceId`
- persistência de `busca`, `lead` e `busca_lead`
- endpoint `POST /api/buscas`
- endpoint `PATCH /api/leads/{id}/status`
- endpoint `GET /api/leads`

### Prioridade média

Podem entrar após o fluxo principal estar funcionando:

- cache com Caffeine
- rate limiting com Bucket4j
- exportação CSV/Excel
- histórico de buscas
- tratamento de erro da API externa
- filtros por categoria/status/temperatura

### Prioridade baixa no começo

Não precisa gastar energia demais no início:

- testes complexos de layout visual;
- testes de performance avançados;
- testes de carga;
- testes de segurança/autenticação, pois o MVP é local e single-user;
- testes de multiusuário, pois está fora do escopo.

---

## Testes unitários do backend

### `ScoringService`

É um dos pontos mais importantes do sistema, porque define se o lead é `QUENTE`, `MORNO` ou `FRIO`.

Testar cenários como:

- lead com categoria aderente, telefone, muitos reviews e rating alto deve ser `QUENTE`;
- lead sem telefone e com poucos reviews deve receber score menor;
- lead com dados incompletos não deve quebrar o cálculo;
- score nunca deve ficar abaixo de 0 nem acima de 100;
- limites de temperatura devem ser respeitados:
  - `70–100`: `QUENTE`
  - `40–69`: `MORNO`
  - `0–39`: `FRIO`

Exemplos de casos:

```text
Dado um mercado com telefone, 80 reviews e rating 4.6
Quando calcular o score
Então deve retornar temperatura QUENTE
```

```text
Dado um lead sem telefone, com 2 reviews e rating 3.2
Quando calcular o score
Então deve retornar temperatura FRIO ou MORNO, conforme regra definida
```

---

### `PlacesResponseMapper`

Esse mapper deve converter a resposta da Google Places API para o modelo interno do projeto.

Testar:

- conversão de nome;
- conversão de endereço;
- conversão de latitude/longitude;
- conversão de rating;
- conversão de quantidade de reviews;
- captura do `googlePlaceId`;
- comportamento quando telefone vier ausente;
- comportamento quando rating/reviews vierem ausentes;
- mapeamento de categoria externa para `CategoriaNegocio` interna.

O teste deve usar JSON fake salvo em arquivo de teste, por exemplo:

```text
src/test/resources/places/nearby-search-padarias.json
src/test/resources/places/nearby-search-mercados.json
src/test/resources/places/place-sem-telefone.json
```

---

### Normalização de telefone

Criar um serviço pequeno, por exemplo `TelefoneService` ou `WhatsappLinkService`.

Testar entradas como:

```text
(11) 99999-9999       -> 5511999999999
+55 11 99999-9999    -> 5511999999999
11 99999-9999        -> 5511999999999
Telefone ausente     -> null
Telefone inválido    -> null ou erro controlado
```

A regra do projeto deve ser:

- se não houver telefone válido, não gerar link de WhatsApp;
- se houver telefone válido, gerar link manual `https://wa.me/55...`;
- nunca implementar disparo automático.

---

### Deduplicação de leads

Testar regra central:

```text
Se dois resultados da Places API tiverem o mesmo googlePlaceId,
o sistema não deve criar dois leads.
```

Também testar:

```text
Se o mesmo lead aparecer em uma nova busca,
deve criar novo registro em busca_lead,
mas não deve duplicar o lead.
```

Esse teste pode ser unitário no service ou de integração com banco.

---

## Testes de integração do backend

Use testes de integração para validar comportamento real com banco.

Recomendação:

- usar `@SpringBootTest` para fluxos completos do backend;
- usar `@DataJpaTest` para repositories e queries;
- usar Testcontainers com MySQL para testar próximo do ambiente real;
- rodar Flyway nas migrations durante os testes;
- evitar `ddl-auto: update`.

---

## Testes de migrations/Flyway

Toda alteração de banco deve vir com migration.

Testar se:

- migrations sobem em banco limpo;
- constraints são criadas corretamente;
- `lead.google_place_id` é `UNIQUE`;
- `busca_lead` tem chave composta ou constraint única para evitar duplicação do mesmo lead na mesma busca;
- índices principais existem.

Índices esperados:

```text
lead.google_place_id UNIQUE
lead.status
lead.categoria
lead.temperatura
busca.criado_em
busca_lead.busca_id
busca_lead.lead_id
```

---

## Testes dos repositories

### `LeadRepository`

Testar:

- buscar por `googlePlaceId`;
- filtrar por status;
- filtrar por categoria;
- filtrar por temperatura;
- não permitir dois leads com mesmo `googlePlaceId`.

### `BuscaRepository`

Testar:

- listar buscas anteriores ordenadas por `criadoEm DESC`;
- salvar busca com parâmetros corretos;
- recuperar busca com seus leads via `busca_lead`.

### `BuscaLeadRepository`

Testar:

- associar lead existente a nova busca;
- impedir duplicidade da mesma associação;
- buscar leads de uma busca específica.

---

## Testes dos endpoints principais

Use `MockMvc` ou equivalente no Spring Boot para testar a API.

### `POST /api/buscas`

Testar:

- request válido retorna `201` ou `200`, conforme decisão do projeto;
- request com raio inválido retorna `400`;
- request sem latitude/longitude retorna `400`;
- request sem categorias retorna `400`;
- resposta contém leads persistidos;
- resposta contém score e temperatura;
- não duplica lead com mesmo `googlePlaceId`;
- cria associação em `busca_lead`.

Exemplo de request:

```json
{
  "latitude": -23.55052,
  "longitude": -46.633308,
  "raioKm": 3,
  "categorias": ["PADARIA", "MERCADO"]
}
```

---

### `GET /api/leads`

Testar:

- retorna todos os leads;
- filtra por status;
- filtra por categoria;
- filtra por temperatura;
- paginação, se implementada;
- ordenação por score, se implementada.

Exemplos:

```text
GET /api/leads?status=NOVO
GET /api/leads?categoria=PADARIA
GET /api/leads?temperatura=QUENTE
```

---

### `PATCH /api/leads/{id}/status`

Testar:

- muda status de `NOVO` para `QUALIFICADO`;
- muda status de `QUALIFICADO` para `CONTATADO`;
- rejeita status inexistente;
- retorna `404` para lead inexistente;
- atualiza `atualizado_em`;
- não altera score, telefone, endereço ou dados vindos do Google.

Exemplo:

```json
{
  "status": "CONTATADO"
}
```

---

### `PATCH /api/leads/{id}/observacao`

Caso seja implementado:

Testar:

- adiciona observação comercial;
- edita observação existente;
- aceita texto vazio ou trata como remoção, conforme decisão do projeto;
- não sobrescreve dados externos do lead.

---

### `GET /api/buscas`

Testar:

- lista buscas anteriores;
- ordena da mais recente para a mais antiga;
- retorna total de encontrados;
- retorna filtros usados na busca.

---

### `GET /api/buscas/{id}/leads`

Se existir:

Testar:

- retorna os leads daquela busca;
- retorna `404` para busca inexistente;
- não retorna leads de outras buscas.

---

### Exportação CSV/Excel

Testar:

- arquivo é gerado com cabeçalhos corretos;
- export respeita filtros atuais;
- campos principais aparecem:
  - nome;
  - categoria;
  - telefone;
  - endereço;
  - score;
  - temperatura;
  - status;
  - observações;
  - último contato;
- não quebra com caracteres especiais em nomes/endereço;
- não quebra quando telefone/rating/reviews forem nulos.

---

## Testes da integração com Google Places API

A Google Places API não deve ser chamada diretamente em testes automatizados comuns.

Criar uma interface ou classe isolada:

```text
PlacesApiClient
```

Nos testes, usar:

```text
FakePlacesApiClient
MockPlacesApiClient
WireMock
MockWebServer
```

Testar:

- resposta válida da API;
- resposta vazia;
- erro HTTP 400;
- erro HTTP 403 por chave inválida;
- erro HTTP 429 por cota/rate limit;
- timeout;
- dados incompletos;
- paginação da Places API, se usada.

O objetivo é garantir que o sistema falhe de forma controlada, sem quebrar a aplicação inteira.

---

## Testes de cache

O cache deve evitar chamadas pagas repetidas à Places API.

Testar:

```text
Dada uma busca com mesmos parâmetros dentro do tempo de cache
Quando o usuário repetir a busca
Então o sistema deve reaproveitar o resultado cacheado
E não deve chamar novamente a Places API
```

Também testar:

- parâmetros diferentes não devem reaproveitar cache errado;
- categorias em ordem diferente devem ser normalizadas, se a regra do projeto permitir;
- cache expirado deve chamar a API novamente;
- cache não deve impedir persistência/histórico de nova busca, se a decisão for registrar nova busca mesmo reaproveitando resultados.

Chave de cache recomendada:

```text
latitude aproximada + longitude aproximada + raioKm + categorias ordenadas
```

---

## Testes de rate limiting

Testar:

- número máximo de chamadas permitido por janela de tempo;
- chamadas acima do limite são bloqueadas ou adiadas conforme regra;
- erro retornado ao usuário é compreensível;
- rate limit protege apenas a integração externa, não necessariamente todos os endpoints locais.

---

## Testes do frontend Angular

### Services HTTP

Testar services que chamam o backend:

- `BuscaService`
- `LeadService`
- `ExportService`

Validar:

- URL correta;
- método HTTP correto;
- payload correto;
- tratamento de erro básico;
- tipagem dos retornos.

---

### Componente de busca/mapa

Testar:

- formulário inicia com valores padrão;
- usuário consegue selecionar categorias;
- raio muda ao mover slider;
- botão de buscar fica desabilitado quando formulário está inválido;
- ao clicar em buscar, chama o service com latitude, longitude, raio e categorias;
- exibe erro se backend retornar falha.

Não precisa testar profundamente o Leaflet em si. Teste a lógica ao redor dele.

---

### Kanban

Testar:

- leads aparecem na coluna correta conforme status;
- card mostra nome, categoria, score, temperatura e telefone;
- se não houver telefone, botão de WhatsApp fica oculto ou desabilitado;
- ao arrastar card para outra coluna, chama `PATCH /api/leads/{id}/status`;
- se o backend falhar ao atualizar status, o card volta para a coluna anterior ou exibe erro.

---

### Link de WhatsApp no frontend

Testar:

- telefone válido gera link `wa.me`;
- telefone ausente não gera botão clicável;
- link abre em nova aba ou janela;
- o frontend não tenta enviar mensagem automaticamente.

---

## Testes ponta a ponta / manuais guiados

No MVP, testes manuais guiados são suficientes para validar experiência geral.

Criar um checklist manual:

### Cenário 1 — Primeira busca

1. Subir MySQL local.
2. Subir backend.
3. Subir frontend.
4. Abrir tela inicial.
5. Selecionar uma localização no mapa.
6. Escolher raio de busca.
7. Selecionar categorias.
8. Clicar em buscar.
9. Confirmar que leads aparecem.
10. Confirmar que leads foram salvos no banco.
11. Confirmar que score/temperatura aparecem.

Resultado esperado:

```text
Busca criada, leads persistidos e exibidos no Kanban ou lista inicial.
```

---

### Cenário 2 — Kanban

1. Buscar leads.
2. Abrir Kanban.
3. Mover lead de `NOVO` para `QUALIFICADO`.
4. Recarregar a página.
5. Confirmar que o lead continua em `QUALIFICADO`.

Resultado esperado:

```text
Status persistido corretamente no backend e banco.
```

---

### Cenário 3 — Deduplicação

1. Fazer uma busca em uma região.
2. Fazer outra busca sobreposta.
3. Verificar leads repetidos.
4. Conferir banco.

Resultado esperado:

```text
Mesmo estabelecimento não deve duplicar em lead.
Deve existir novo vínculo em busca_lead quando aplicável.
```

---

### Cenário 4 — WhatsApp

1. Abrir lead com telefone.
2. Clicar em WhatsApp.
3. Confirmar abertura do link manual.
4. Abrir lead sem telefone.
5. Confirmar que não há botão ativo.

Resultado esperado:

```text
Sistema apenas abre link manual, sem automação/disparo em massa.
```

---

### Cenário 5 — Exportação

1. Buscar leads.
2. Filtrar por status ou categoria.
3. Exportar CSV/Excel.
4. Abrir arquivo.
5. Conferir campos exportados.

Resultado esperado:

```text
Arquivo gerado corretamente com os leads esperados.
```

---

## Dados fake para desenvolvimento e testes

Criar massa de dados local para testar sem depender da Google Places API.

Exemplo de leads fake:

```text
Padaria Pão Bom — PADARIA — telefone válido — rating 4.7 — 120 reviews
Mercado Central — MERCADO — telefone válido — rating 4.2 — 80 reviews
Doceria Mel — DOCERIA — sem telefone — rating 4.8 — 35 reviews
Açougue Boi Forte — ACOUGUE — telefone válido — rating 3.9 — 12 reviews
Restaurante Sabor Caseiro — RESTAURANTE — sem telefone — rating null — reviews null
```

Esses dados podem ser usados em:

- seed local de desenvolvimento;
- testes do frontend;
- testes de mapper;
- testes do Kanban;
- testes de exportação.

---

## O que não testar agora

Não priorizar no MVP:

- autenticação;
- autorização;
- multiusuário;
- filas;
- Redis;
- deploy remoto;
- scraping de Instagram;
- automação de WhatsApp;
- testes de carga com milhares de leads.

Esses itens estão fora do escopo atual e só devem entrar se o projeto evoluir para SaaS, multiusuário ou produção.

---

## Ordem recomendada de implementação dos testes

1. Testar `ScoringService`.
2. Testar normalização de telefone e link de WhatsApp.
3. Testar `PlacesResponseMapper` com JSON fake.
4. Testar migrations e repositories com MySQL/Testcontainers.
5. Testar deduplicação no `BuscaService`.
6. Testar endpoint `POST /api/buscas` com client fake da Places API.
7. Testar `PATCH /api/leads/{id}/status`.
8. Testar `GET /api/leads` com filtros.
9. Testar frontend services.
10. Testar Kanban.
11. Testar exportação.
12. Fazer checklist manual ponta a ponta.

---

## Stack sugerida para testes

### Backend

- JUnit 5
- AssertJ
- Mockito
- Spring Boot Test
- MockMvc
- Testcontainers com MySQL
- WireMock ou MockWebServer para simular Google Places API
- Flyway executando em ambiente de teste

### Frontend

- Jasmine/Karma ou Jest, conforme setup do Angular
- Angular Testing Library, se quiser testes mais próximos do comportamento do usuário
- HttpTestingController para services HTTP
- Cypress ou Playwright apenas se quiser E2E depois

### Testes manuais

- Checklist em Markdown
- Massa de dados fake
- Collection HTTP no Bruno, Insomnia ou Postman, se quiser testar endpoints manualmente

---

## Regra prática para este projeto

O projeto não precisa começar com cobertura altíssima.

A meta inicial boa é:

```text
Cobrir muito bem regras de negócio e integração com banco.
Cobrir moderadamente endpoints principais.
Cobrir o frontend no fluxo crítico do Kanban e busca.
Não gastar tempo com testes frágeis de layout.
```

Em outras palavras:

```text
Teste forte: scoring, mapper, deduplicação, banco, status do Kanban.
Teste médio: endpoints, exportação, cache, rate limit.
Teste leve/manual: layout, mapa, UX visual.
```
