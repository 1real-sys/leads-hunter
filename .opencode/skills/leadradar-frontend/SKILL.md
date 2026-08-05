---
name: leadradar-frontend
description: Use esta skill ao criar ou alterar frontend Angular do LeadRadar Cartão, incluindo mapa, formulário de busca, Kanban, cards, WhatsApp manual, histórico e exportação.
---

# Frontend — LeadRadar Cartão

## Stack

Use:

- Angular 17+;
- componentes standalone quando fizer sentido;
- Angular CDK para drag-and-drop;
- Leaflet ou ngx-leaflet para mapa;
- RxJS para HTTP e estado assíncrono;
- Reactive Forms para formulários.

## Estrutura sugerida

```text
src/app
├── core
│   ├── api
│   ├── interceptors
│   └── config
├── shared
│   ├── models
│   ├── components
│   └── utils
├── mapa
│   ├── mapa-busca.component.ts
│   └── mapa-busca.component.html
├── kanban
│   ├── kanban-board.component.ts
│   ├── kanban-column.component.ts
│   └── lead-card.component.ts
├── buscas-anteriores
│   └── buscas-anteriores.component.ts
└── exportacao
    └── exportacao.component.ts
```

## Tela principal do MVP

A tela principal deve permitir:

- selecionar endereço ou ponto no mapa;
- arrastar marcador;
- ajustar raio de busca;
- selecionar múltiplas categorias;
- clicar em “Buscar leads”;
- ver leads encontrados;
- acessar o Kanban.

## Mapa

O componente de mapa deve:

- exibir um ponto central;
- permitir clique no mapa para alterar ponto;
- permitir marcador arrastável;
- exibir círculo representando o raio;
- atualizar círculo ao mudar o slider de raio;
- enviar latitude, longitude e raio para o backend.

## Formulário de busca

Use Reactive Forms.

Campos:

```text
enderecoBase
latitude
longitude
raioKm
categorias[]
```

Validações:

- latitude obrigatória;
- longitude obrigatória;
- raio obrigatório;
- raio maior que zero;
- categorias não vazias.

## Kanban

Colunas obrigatórias:

```text
Novo
Qualificado
Contatado
Ganho
Perdido
```

Mapeamento com enum do backend:

```text
NOVO
QUALIFICADO
CONTATADO
GANHO
PERDIDO
```

Use Angular CDK Drag and Drop.

Ao mover card:

1. atualizar visualmente;
2. chamar `PATCH /api/leads/{id}/status`;
3. em caso de erro, reverter movimentação ou avisar usuário.

## Card de lead

O card deve exibir:

- nome;
- categoria;
- endereço resumido;
- telefone, quando existir;
- rating Google, quando existir;
- total de reviews, quando existir;
- score;
- temperatura;
- status;
- botão WhatsApp, se houver telefone válido;
- campo ou botão para observações.

## WhatsApp manual

O botão de WhatsApp deve:

- abrir link `wa.me` em nova aba;
- existir apenas quando houver telefone normalizado;
- não disparar mensagem automática;
- não integrar com Baileys;
- não usar WhatsApp Business API no MVP.

Se não houver telefone:

- ocultar botão; ou
- exibir botão desabilitado com tooltip “Telefone indisponível”.

## Observações comerciais

Permitir que o usuário registre observações do lead, como:

```text
Falei com o dono, pediu retorno semana que vem.
Não usa máquina de cartão.
Já possui conciliador.
Telefone não atende.
```

Essas observações devem ser salvas no backend.

## Histórico de buscas

A tela de buscas anteriores deve:

- listar buscas por data;
- exibir endereço base;
- exibir categorias buscadas;
- exibir raio;
- exibir total encontrado;
- permitir abrir leads daquela busca.

## Exportação

Criar botões para:

- exportar CSV;
- exportar Excel.

A exportação pode apenas abrir ou baixar a resposta do backend.

## UX esperada

Priorizar:

- interface simples;
- visual claro;
- poucos passos;
- foco no uso real do usuário;
- evitar telas complexas demais no MVP.

Não implementar dashboard avançado no MVP, a menos que pedido explicitamente.
