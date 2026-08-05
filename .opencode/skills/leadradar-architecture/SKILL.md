---
name: leadradar-architecture
description: Use esta skill ao tomar decisões arquiteturais no LeadRadar Cartão, estruturar fluxos, definir camadas, separar responsabilidades ou evitar complexidade desnecessária.
---

# Arquitetura — LeadRadar Cartão

## Visão geral

Aplicação local com dois processos principais:

```text
[Angular App] <--REST/JSON--> [Spring Boot API] <--HTTPS--> [Google Places API]
                                      |
                                 [MySQL local]
```

Não há deploy remoto no MVP.

Não há autenticação no MVP.

Não há multiusuário no MVP.

## Princípios arquiteturais

Siga estes princípios:

- simplicidade antes de escala;
- clareza para portfólio;
- separação de responsabilidades;
- domínio protegido de detalhes externos;
- controllers finos;
- services com regra de negócio;
- clients isolando APIs externas;
- migrations controlando schema;
- frontend consumindo REST simples.

## Organização do backend

Use package-by-feature.

Estrutura base:

```text
com.projeto.leadradar
├── busca
├── lead
├── scoring
├── integracao
│   └── places
├── exportacao
└── config
```

Não use organização puramente por camada como:

```text
controller
service
repository
model
```

O objetivo é manter arquivos relacionados próximos.

## Fluxo principal — busca de leads

Fluxo esperado:

1. Frontend envia `POST /api/buscas` com latitude, longitude, raio e categorias.
2. `BuscaController` valida entrada e delega ao `BuscaService`.
3. `BuscaService` monta chave de cache.
4. `BuscaService` verifica Caffeine.
5. Se não houver cache, `PlacesApiClient` chama Google Places API respeitando Bucket4j.
6. `PlacesResponseMapper` converte resposta externa para objetos internos.
7. `ScoringService` calcula score e temperatura.
8. Sistema normaliza telefone, quando existir.
9. Sistema deduplica por `googlePlaceId`.
10. Sistema persiste `Busca`, `Lead` e `BuscaLead`.
11. Backend retorna lista de leads ao frontend.

## Fluxo secundário — Kanban

Fluxo esperado:

1. Frontend carrega leads via `GET /api/leads`.
2. Frontend agrupa por status ou recebe já filtrado.
3. Usuário arrasta card entre colunas.
4. Frontend envia `PATCH /api/leads/{id}/status`.
5. Backend atualiza apenas o status.
6. Backend não recalcula score por movimentação manual.

Status possíveis:

```text
NOVO -> QUALIFICADO -> CONTATADO -> GANHO
NOVO -> QUALIFICADO -> CONTATADO -> PERDIDO
```

Não precisa bloquear transições no MVP, a menos que o usuário peça.

## Fluxo de contato manual

WhatsApp deve ser manual.

Fluxo:

1. Lead possui telefone normalizado.
2. Frontend exibe botão WhatsApp.
3. Clique abre `https://wa.me/55DDDNUMERO`.
4. Usuário escreve e envia a mensagem manualmente.
5. Opcionalmente, usuário marca lead como `CONTATADO`.
6. Usuário pode preencher `observacoes` e `ultimo_contato_em`.

Não implementar envio automático.

## Decisões do MVP

### Sem autenticação

Motivo:

- uso pessoal;
- execução local;
- single-user;
- evita complexidade desnecessária.

### Sem fila/mensageria

Motivo:

- volume baixo;
- buscas esporádicas;
- exportação pequena;
- não há processamento assíncrono necessário no MVP.

Não usar:

- Kafka;
- RabbitMQ;
- SQS.

### Cache local com Caffeine

Motivo:

- aplicação single-instance;
- cache distribuído não é necessário;
- evita custo repetido com Google Places API.

Não usar Redis no MVP.

### Rate limit com Bucket4j

Motivo:

- proteger cota da Google Places API;
- evitar custo desnecessário;
- impedir chamadas repetidas.

### Exportação síncrona

Motivo:

- volume pequeno;
- geração CSV/Excel pode ocorrer direto na requisição.

Não criar jobs assíncronos para exportação no MVP.

## Pontos de extensão futura

A arquitetura pode evoluir depois para:

- autenticação;
- multiusuário;
- Redis;
- fila para automações;
- deploy remoto;
- enriquecimento externo;
- integração oficial de mensageria.

Mas essas decisões só entram após validação do MVP.

## Regras para decisões futuras

Quando houver dúvida arquitetural, escolha a opção que:

1. mantém o MVP local;
2. reduz dependências externas;
3. evita custo;
4. é mais fácil de explicar no portfólio;
5. não aumenta complexidade sem necessidade real.
