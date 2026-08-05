---
name: leadradar-roadmap
description: Use esta skill para planejar implementação, escolher próximas tarefas, organizar fases e evitar sair do escopo do MVP do LeadRadar Cartão.
---

# Roadmap — LeadRadar Cartão

## Objetivo do roadmap

Guiar a implementação do MVP sem adicionar complexidade desnecessária.

Sempre priorize o que permite validar o fluxo principal:

```text
buscar leads -> salvar leads -> visualizar no Kanban -> contatar manualmente -> exportar
```

## Fase 0 — Setup

Tarefas:

- criar projeto Spring Boot 4.x com Java 25;
- criar projeto Angular 17+;
- configurar Docker Compose com MySQL local;
- configurar Flyway;
- configurar variáveis de ambiente;
- criar `.env.example` sem chave real;
- garantir que chave da Google Places API nunca seja commitada.

Entregável:

- backend sobe localmente;
- frontend sobe localmente;
- MySQL sobe localmente;
- migration inicial executa.

## Fase 1 — Banco e domínio

Tarefas:

- criar entidades `Busca`, `Lead` e `BuscaLead`;
- criar enums `CategoriaNegocio`, `StatusFunil` e `Temperatura`;
- criar repositories JPA;
- criar migration inicial;
- configurar índices e unique key de `google_place_id`.

Entregável:

- schema inicial correto;
- aplicação validando schema com Flyway/JPA.

## Fase 2 — Backend de busca

Tarefas:

- criar `BuscaRequest` e `BuscaResponse`;
- criar `BuscaController`;
- criar `BuscaService`;
- criar `PlacesApiClient`;
- criar `PlacesResponseMapper`;
- integrar Google Places API;
- implementar deduplicação por `googlePlaceId`;
- persistir `Busca`, `Lead` e `BuscaLead`.

Entregável:

- `POST /api/buscas` funcional de ponta a ponta.

## Fase 3 — Scoring, cache e rate limit

Tarefas:

- criar `ScoringService`;
- implementar regra fixa de score 0 a 100;
- calcular temperatura;
- configurar Bucket4j;
- configurar Caffeine;
- evitar chamadas repetidas para parâmetros iguais.

Entregável:

- busca com score, temperatura, cache e proteção de cota.

## Fase 4 — Backend do Kanban

Tarefas:

- criar `LeadController`;
- criar endpoints de listagem;
- criar filtros por status/categoria/temperatura;
- criar `PATCH /api/leads/{id}/status`;
- criar endpoint para observações;
- criar endpoint para último contato;
- gerar URL de WhatsApp quando possível.

Entregável:

- backend pronto para alimentar Kanban.

## Fase 5 — Frontend de mapa e busca

Tarefas:

- criar tela de busca;
- integrar Leaflet;
- implementar marcador arrastável;
- implementar círculo de raio;
- implementar seletor de categorias;
- chamar `POST /api/buscas`;
- exibir retorno inicial.

Entregável:

- usuário consegue buscar leads pela interface.

## Fase 6 — Frontend Kanban

Tarefas:

- criar board Kanban;
- criar colunas Novo, Qualificado, Contatado, Ganho e Perdido;
- criar card de lead;
- implementar drag-and-drop com Angular CDK;
- persistir mudança de status via PATCH;
- exibir botão de WhatsApp manual;
- exibir score e temperatura.

Entregável:

- funil comercial visual e funcional.

## Fase 7 — Histórico, observações e exportação

Tarefas:

- criar tela de buscas anteriores;
- permitir abrir leads de uma busca antiga;
- permitir editar observações do lead;
- permitir registrar último contato;
- implementar exportação CSV;
- implementar exportação Excel.

Entregável:

- MVP utilizável no dia a dia.

## Fase 8 — Testes e polimento

Tarefas:

- testar `ScoringService`;
- testar `PlacesResponseMapper`;
- testar normalização de telefone;
- testar deduplicação;
- testar atualização de status;
- tratar erros da Google Places API;
- melhorar mensagens de erro no frontend;
- revisar UX.

Entregável:

- MVP estável para portfólio e uso local.

## Backlog futuro

Não priorizar antes do MVP:

- enriquecimento via Instagram;
- disparo automatizado de WhatsApp;
- autenticação;
- multiusuário;
- deploy remoto;
- Redis;
- fila/mensageria;
- filtro por faixa de renda/região;
- dashboards avançados;
- CRM completo.

## Regra de decisão

Quando o usuário pedir “qual o próximo passo?”, escolha a primeira tarefa incompleta na ordem do roadmap.

Quando o usuário pedir uma feature fora do roadmap atual, responda se ela deve entrar agora ou ficar no backlog.
