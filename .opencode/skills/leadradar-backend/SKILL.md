---
name: leadradar-backend
description: Use esta skill ao criar ou alterar backend Java/Spring Boot do LeadRadar Cartão, incluindo controllers, services, repositories, DTOs, integração com Google Places, cache, rate limit e exportação.
---

# Backend — LeadRadar Cartão

## Stack obrigatória

Use:

- Java 25 LTS;
- Spring Boot 4.x;
- Spring Web;
- Spring Data JPA;
- Hibernate/JPA;
- Bean Validation / Jakarta Validation;
- Jackson;
- Bucket4j;
- Caffeine;
- Virtual Threads para chamadas I/O-bound quando fizer sentido.

## Organização por feature

Use package-by-feature, não package-by-layer genérico.

Base sugerida:

```text
com.projeto.leadradar
├── busca
│   ├── BuscaController
│   ├── BuscaService
│   ├── BuscaRepository
│   ├── Busca
│   ├── BuscaRequest
│   └── BuscaResponse
├── lead
│   ├── LeadController
│   ├── LeadService
│   ├── LeadRepository
│   ├── Lead
│   ├── LeadResponse
│   ├── AtualizarStatusRequest
│   ├── CategoriaNegocio
│   ├── StatusFunil
│   └── Temperatura
├── scoring
│   └── ScoringService
├── integracao
│   └── places
│       ├── PlacesApiClient
│       ├── PlacesResponseMapper
│       ├── PlacesSearchRequest
│       └── PlacesSearchResponse
├── exportacao
│   └── ExportService
└── config
    ├── CacheConfig
    ├── RateLimitConfig
    ├── WebConfig
    └── JacksonConfig
```

## Regras de Controller

Controllers devem:

- receber requisições HTTP;
- validar payload com Bean Validation;
- delegar para services;
- retornar DTOs;
- não conter regra de negócio;
- não chamar repositories diretamente;
- não chamar Google Places API diretamente.

## Regras de Service

Services devem conter os fluxos de negócio.

`BuscaService` deve:

1. receber categoria(s), latitude, longitude e raio;
2. verificar cache de busca recente;
3. chamar `PlacesApiClient` somente se necessário;
4. mapear resposta externa para modelo interno;
5. normalizar telefone quando existir;
6. calcular score e temperatura;
7. deduplicar lead por `googlePlaceId`;
8. persistir `Busca`, `Lead` e `BuscaLead`;
9. retornar DTOs ao frontend.

`LeadService` deve:

- listar leads;
- filtrar por status, categoria e temperatura;
- atualizar status do funil;
- atualizar observações;
- atualizar último contato;
- montar URL de WhatsApp quando houver telefone normalizado.

## Endpoints do MVP

Use estes endpoints como base:

```text
POST   /api/buscas
GET    /api/buscas
GET    /api/buscas/{id}
GET    /api/leads
GET    /api/leads/{id}
PATCH  /api/leads/{id}/status
PATCH  /api/leads/{id}/observacoes
PATCH  /api/leads/{id}/contato
GET    /api/exportacao/leads.csv
GET    /api/exportacao/leads.xlsx
```

## Payload de busca

`POST /api/buscas` deve receber algo próximo de:

```json
{
  "enderecoBase": "Centro, Curitiba - PR",
  "latitude": -25.4284,
  "longitude": -49.2733,
  "raioKm": 5,
  "categorias": ["PADARIA", "MERCADO", "RESTAURANTE"]
}
```

Validações:

- `latitude` obrigatória;
- `longitude` obrigatória;
- `raioKm` obrigatório e positivo;
- limitar raio máximo no MVP, por exemplo 20 km;
- `categorias` obrigatória e não vazia.

## Integração com Google Places API

Toda chamada externa deve ficar em:

```text
integracao.places.PlacesApiClient
```

Não espalhe detalhes da API externa pelo domínio.

O mapper deve converter DTO externo para modelo interno:

```text
integracao.places.PlacesResponseMapper
```

O domínio não deve depender diretamente do formato bruto da Google Places API.

## Rate limiting

Use Bucket4j para proteger chamadas à Google Places API.

Objetivo:

- evitar estouro de cota;
- evitar custo inesperado;
- impedir várias buscas idênticas em sequência.

## Cache

Use Caffeine para cache local de buscas recentes.

Chave de cache sugerida:

```text
latitude arredondada + longitude arredondada + raioKm + categorias ordenadas
```

O cache deve evitar chamadas pagas repetidas para parâmetros iguais ou muito semelhantes.

## Scoring

Sempre use `ScoringService`.

Não recalcule score dentro de Controller, Mapper ou Repository.

Regra inicial:

```text
Score total: 0 a 100

Categoria aderente ao produto: +30
Possui telefone: +25
Reviews:
  0 a 10: +5
  11 a 50: +10
  51 ou mais: +15
Rating:
  abaixo de 3.5: +5
  3.5 a 4.4: +10
  4.5 ou mais: +15
Aberto/em funcionamento, se disponível: +10
Site: neutro
```

Temperatura:

```text
70 a 100: QUENTE
40 a 69: MORNO
0 a 39: FRIO
```

## WhatsApp

Nunca implemente automação de WhatsApp no MVP.

O backend pode expor uma URL calculada:

```text
https://wa.me/55DDDNUMERO
```

A URL só deve existir quando houver `telefoneNormalizado` válido.

## Tratamento de erro

Prever erros para:

- Google Places API fora do ar;
- cota estourada;
- chave ausente;
- resposta sem telefone;
- resposta sem categoria clara;
- busca sem resultados;
- raio inválido;
- categoria inválida.

## Testes prioritários

Criar testes para:

- `ScoringService`;
- `PlacesResponseMapper`;
- normalização de telefone;
- deduplicação por `googlePlaceId`;
- atualização de status do Kanban;
- endpoint `POST /api/buscas` com mock da Places API.
