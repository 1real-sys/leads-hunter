---
name: leadradar-frontend
description: Use esta skill ao criar ou alterar frontend Angular do LeadRadar Cartão, incluindo mapa, formulário de busca, Kanban, cards, WhatsApp manual, histórico e exportação.
---

# Frontend — LeadRadar Cartão

## Objetivo

Esta skill define os requisitos funcionais e as decisões específicas do frontend do LeadRadar Cartão.

O projeto também possui as skills oficiais:

* `angular-new-app`
* `angular-developer`

Esta skill define **O QUE o LeadRadar deve fazer**.

As skills oficiais do Angular definem **COMO trabalhar corretamente com Angular**.

---

# Skills Angular

## `angular-new-app`

Utilizar `angular-new-app` **somente durante a criação inicial da aplicação Angular**.

Se o frontend Angular ainda não existir:

1. ler `angular-new-app`;
2. seguir suas instruções;
3. verificar Angular CLI;
4. criar a aplicação Angular;
5. configurar o projeto conforme os requisitos desta skill;
6. validar o projeto criado com build.

Depois que a aplicação Angular tiver sido criada, `angular-new-app` deixa de fazer parte do fluxo normal de desenvolvimento.

NÃO utilizar `angular-new-app` posteriormente para:

* implementar features;
* criar telas;
* criar componentes;
* refatorar;
* alterar arquitetura;
* recriar a aplicação;
* executar novamente `ng new`.

Nunca recriar, sobrescrever ou reinicializar um frontend Angular existente sem solicitação explícita.

## `angular-developer`

Depois da criação inicial, utilizar `angular-developer` como principal referência técnica Angular.

Utilizar essa skill e suas referências para:

* componentes;
* Signals;
* inputs e outputs;
* HTTP;
* dependency injection;
* formulários;
* routing;
* styling;
* acessibilidade;
* testes;
* Angular CLI;
* performance;
* migrations;
* APIs modernas;
* boas práticas específicas da versão instalada.

Consultar as referências relevantes para a tarefa atual quando necessário.

## Fluxo das skills

```text
Frontend ainda não existe
        │
        ▼
angular-new-app
        │
        ▼
Criação do projeto
        │
        ▼
angular-new-app deixa de ser utilizada
        │
        ▼
angular-developer
        +
leadradar-frontend
        │
        ▼
Desenvolvimento contínuo
```

Em resumo:

* `angular-new-app` → bootstrap inicial;
* `angular-developer` → boas práticas e desenvolvimento Angular;
* `leadradar-frontend` → requisitos específicos do LeadRadar.

---

# Limites de autorização Git

Um pedido para implementar, corrigir, revisar, validar, concluir ou finalizar um sprint autoriza somente as alterações locais e validações necessárias ao escopo solicitado.

Esse tipo de pedido **não autoriza** executar:

* `git add`;
* `git commit`;
* `git push`;
* criação de tags, branches ou pull requests;
* merge ou qualquer outra publicação de alterações.

Operações Git que alterem o índice, o histórico ou o repositório remoto só podem ser executadas quando o **prompt atual do usuário** as solicitar explicitamente. Uma autorização concedida para outro sprint, tarefa ou mensagem anterior não deve ser reutilizada.

Ao concluir uma implementação sem essa autorização, manter as mudanças locais sem staging e informar o estado no relatório final. Comandos Git somente de leitura, como `git status`, `git diff` e `git log`, continuam permitidos para inspeção e revisão.

---

# Stack

Use:

* Angular 21+;
* TypeScript em modo `strict`;
* componentes standalone por padrão;
* Angular CDK para drag-and-drop;
* Leaflet ou integração Angular compatível com Leaflet para mapa;
* `HttpClient` para comunicação com o backend;
* Signals para estado local e derivado quando apropriado;
* RxJS quando streams, Observables ou composição assíncrona forem apropriados;
* Signal Forms ou Reactive Forms conforme a complexidade e as práticas recomendadas para a versão Angular utilizada.

Evitar adicionar bibliotecas quando recursos nativos do Angular resolverem adequadamente o problema.

---

# Compatibilidade Angular

Antes de implementar ou alterar funcionalidades relevantes:

* verificar a versão Angular instalada;
* seguir `angular-developer`;
* consultar suas referências quando aplicáveis;
* utilizar APIs compatíveis com a versão instalada;
* não realizar downgrade do Angular;
* não introduzir padrões legados quando houver alternativa moderna e estável;
* preservar arquitetura existente quando não houver motivo concreto para alterá-la.

---

# TypeScript

O frontend deve utilizar TypeScript como linguagem padrão.

* Usar TypeScript em todo código da aplicação sempre que possível.
* Não criar `.js` ou `.mjs` para lógica da aplicação quando TypeScript puder ser utilizado.
* Evitar JavaScript puro.
* Manter `strict` habilitado.
* Evitar `any`.
* Preferir tipos específicos, interfaces, generics ou `unknown`.
* Criar interfaces/types para DTOs enviados e recebidos pelo backend.
* Tipar respostas HTTP.
* Tipar estados, eventos e contratos importantes.
* Aproveitar o sistema de tipos do TypeScript.
* Não duplicar tipos equivalentes sem necessidade.

Os tipos do frontend devem refletir os contratos reais do backend.

---

# Estrutura sugerida

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

Essa estrutura é uma referência.

Não criar abstrações ou camadas adicionais sem necessidade concreta.

Priorizar simplicidade no MVP.

---

# Angular CLI

Quando apropriado, utilizar Angular CLI para gerar artefatos.

Exemplos:

```bash
npx ng generate component ...
npx ng generate service ...
npx ng generate interface ...
```

Depois da geração, adaptar o código aos requisitos do LeadRadar.

Não utilizar `--skip-tests` por padrão.

---

# Estado e assincronismo

Preferir Signals para:

* estado local;
* valores derivados;
* seleção atual;
* filtros;
* estados simples de loading;
* informações derivadas de outros estados.

Usar RxJS quando houver necessidade real de:

* composição de streams;
* debounce;
* cancelamento;
* eventos contínuos;
* fluxos assíncronos complexos;
* APIs baseadas em Observable.

Não utilizar RxJS apenas por hábito quando Signals resolverem de forma mais simples.

Não adicionar gerenciamento global complexo de estado no MVP sem necessidade real.

---

# Integração com backend

Antes de implementar uma integração:

* verificar os endpoints reais existentes;
* verificar métodos HTTP;
* verificar payloads;
* verificar DTOs;
* verificar enums;
* verificar respostas;
* verificar códigos de erro relevantes.

Não inventar contratos quando o backend já existir.

Caso frontend e backend sejam incompatíveis:

* não alterar silenciosamente o backend;
* registrar a incompatibilidade;
* explicar qual ajuste seria necessário.

Centralizar comunicação HTTP em services ou abstrações apropriadas.

Não espalhar URLs de endpoints diretamente pelos componentes.

---

# Direção de layout desktop

A aplicação deve priorizar uma estrutura operacional desktop com duas regiões principais:

## Sidebar operacional

Manter uma coluna lateral persistente à esquerda para concentrar, conforme a tela e o contexto:

- configuração da busca;
- categorias;
- raio;
- ações principais;
- filtros;
- acesso rápido às áreas Busca, Kanban e Histórico;
- resumo/lista de leads quando fizer sentido.

A sidebar deve ser compacta e permitir escaneamento rápido.

Evitar transformar cada controle da sidebar em card independente.

## Área principal

O restante da viewport deve ser utilizado como área principal de trabalho.

Na tela de Busca:

- o mapa deve ocupar a maior parte da área disponível;
- evitar colocar o mapa dentro de um card pequeno centralizado;
- priorizar mapa amplo, semelhante a uma ferramenta cartográfica;
- formulário e mapa devem permanecer utilizáveis simultaneamente.

Na tela de Kanban:

- preservar a estrutura geral da aplicação;
- utilizar a área principal para as cinco colunas do funil;
- cards devem ser compactos;
- maximizar espaço horizontal disponível.

Na tela de Histórico:

- utilizar a área principal para a listagem/detalhe;
- preservar navegação e contexto.

## Comportamento da viewport

Em desktop:

- priorizar uso eficiente de toda a largura e altura disponíveis;
- evitar grandes margens externas;
- evitar conteúdo central excessivamente estreito;
- evitar grandes espaços vazios;
- evitar estrutura de landing page;
- evitar hero sections;
- evitar footer ocupando espaço permanente de trabalho.

A aplicação deve lembrar uma ferramenta operacional/CRM leve, não um site institucional.

## Referência conceitual

A composição desejada segue aproximadamente:

sidebar operacional + área de trabalho principal.

Não copiar identidade, textos ou assets de outros produtos, mas preservar essa lógica espacial.

# Tela principal do MVP

A tela principal deve permitir:

* selecionar endereço ou ponto no mapa;
* arrastar marcador;
* ajustar raio de busca;
* selecionar múltiplas categorias;
* clicar em “Buscar leads”;
* ver leads encontrados;
* acessar o Kanban.

Também deve apresentar feedback adequado durante:

* carregamento;
* erro;
* ausência de resultados.

---

# Mapa

O componente de mapa deve:

* exibir um ponto central;
* permitir clique no mapa para alterar ponto;
* permitir marcador arrastável;
* exibir círculo representando o raio;
* atualizar círculo ao mudar o slider de raio;
* enviar latitude, longitude e raio para o backend;
* manter mapa e formulário sincronizados.

Quando o usuário alterar o ponto no mapa:

* atualizar latitude;
* atualizar longitude.

Evitar recriar desnecessariamente a instância inteira do mapa.

---

# Formulário de busca

Campos:

```text
enderecoBase
latitude
longitude
raioKm
categorias[]
```

Validações:

* latitude obrigatória;
* longitude obrigatória;
* raio obrigatório;
* raio maior que zero;
* categorias não vazias.

A implementação deve seguir as práticas recomendadas por `angular-developer`.

Pode utilizar:

* Signal Forms; ou
* Reactive Forms;

conforme a versão Angular, maturidade da API, complexidade do formulário e arquitetura atual.

Não misturar estratégias sem necessidade.

---

# Busca de leads

Ao clicar em “Buscar leads”:

1. validar o formulário;
2. impedir submissão inválida;
3. indicar carregamento;
4. enviar a requisição ao backend;
5. processar a resposta;
6. exibir os leads encontrados;
7. tratar ausência de resultados;
8. tratar erros.

Evitar requisições duplicadas por múltiplos cliques.

---

# Kanban

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

Usar Angular CDK Drag and Drop.

Ao mover card:

1. atualizar visualmente;
2. chamar `PATCH /api/leads/{id}` com o campo `status`;
3. aguardar confirmação do backend;
4. em caso de erro, reverter movimentação ou avisar o usuário.

Não deixar frontend e backend silenciosamente com estados divergentes.

---

# Card de lead

O card deve exibir:

* nome;
* categoria;
* endereço resumido;
* telefone, quando existir;
* rating Google, quando existir;
* total de reviews, quando existir;
* score;
* temperatura;
* status;
* botão WhatsApp, se houver telefone válido;
* campo ou botão para observações.

Não apresentar informações inexistentes como se fossem dados reais.

Evitar cards excessivamente carregados visualmente.

---

# WhatsApp manual

O botão de WhatsApp deve:

* abrir link `wa.me` em nova aba;
* existir apenas quando houver telefone normalizado;
* não disparar mensagem automática;
* não integrar com Baileys;
* não usar WhatsApp Business API no MVP.

Se não houver telefone:

* ocultar botão; ou
* exibir botão desabilitado com tooltip `Telefone indisponível`.

A geração do link deve utilizar telefone normalizado.

Evitar duplicar lógica de normalização em vários componentes.

---

# Observações comerciais

Permitir que o usuário registre observações do lead, como:

```text
Falei com o dono, pediu retorno semana que vem.
Não usa máquina de cartão.
Já possui conciliador.
Telefone não atende.
```

Essas observações devem ser salvas no backend.

A interface deve:

* carregar observações existentes;
* permitir alteração;
* persistir alterações;
* informar erro caso o salvamento falhe.

Evitar salvar a cada tecla sem necessidade.

---

# Histórico de buscas

A tela de buscas anteriores deve:

* listar buscas por data;
* exibir endereço base;
* exibir categorias buscadas;
* exibir raio;
* exibir total encontrado;
* permitir abrir leads daquela busca.

Considerar estados:

* carregando;
* vazio;
* erro;
* dados disponíveis.

---

# Exportação

Criar botões para:

* exportar CSV;
* exportar Excel.

A exportação pode apenas abrir ou baixar a resposta do backend.

O frontend não deve recriar regras de exportação que já existam no backend.

Tratar adequadamente:

* download;
* nome do arquivo;
* tipo MIME quando disponível;
* loading;
* erros.

---

# Loading, erros e estados vazios

Toda operação assíncrona relevante deve considerar:

```text
loading
success
empty
error
```

Não deixar ações sem feedback visual.

Mensagens destinadas ao usuário não devem exibir stack traces ou detalhes internos desnecessários.

---

# UX esperada

Priorizar:

* interface simples;
* visual claro;
* poucos passos;
* foco no uso real do usuário;
* feedback visual;
* consistência;
* acessibilidade básica;
* boa experiência em desktop.

Evitar:

* telas complexas demais;
* dashboards desnecessários;
* gráficos sem utilidade;
* excesso de modais;
* animações pesadas;
* navegação profunda.

Não implementar dashboard avançado no MVP, a menos que pedido explicitamente.

---

# Acessibilidade

Quando aplicável:

* usar HTML semântico;
* associar labels aos campos;
* permitir navegação por teclado;
* manter foco visível;
* fornecer descrição para botões somente com ícones;
* não depender exclusivamente de cores para transmitir estado.

Seguir também as recomendações de acessibilidade da skill `angular-developer`.

---

# Performance

Evitar otimização prematura, mas:

* não realizar HTTP desnecessário;
* evitar recriações desnecessárias do mapa;
* utilizar Signals/computed values adequadamente;
* utilizar `track` corretamente em listas;
* evitar subscriptions manuais sem gerenciamento apropriado;
* considerar lazy loading quando houver benefício concreto.

---

# Testes e validação

Não remover ou ignorar testes existentes.

Criar testes para comportamentos importantes quando apropriado, especialmente:

* validações;
* services;
* transformação de dados;
* regras de status;
* utilitários;
* comportamentos críticos.

Após alterações relevantes, executar:

```bash
npx ng build
```

Também executar os testes apropriados disponíveis no projeto.

Não considerar uma tarefa concluída enquanto houver erro de compilação relacionado às alterações.

---

# Escopo do MVP

Fluxo principal:

```text
Mapa
  ↓
Busca de leads
  ↓
Resultados
  ↓
Kanban
  ↓
Observações
  ↓
WhatsApp manual
  ↓
Histórico
  ↓
Exportação
```

Não adicionar sem solicitação explícita:

* autenticação;
* cadastro de usuário;
* RBAC;
* dashboard avançado;
* automação de WhatsApp;
* envio automático de mensagens;
* Baileys;
* WhatsApp Business API;
* analytics complexos;
* notificações push;
* microfrontends;
* gerenciamento global complexo de estado.

---

# Regra final

O frontend deve ser moderno, simples, fortemente tipado e focado no fluxo real do LeadRadar.

Quando houver dúvida entre uma solução simples e adequada e uma arquitetura mais sofisticada sem benefício concreto, prefira a solução simples.

Use:

* `angular-new-app` para criar o frontend **uma única vez**;
* `angular-developer` para decidir **COMO implementar Angular corretamente**;
* `leadradar-frontend` para decidir **O QUE o LeadRadar precisa fazer**;
* `leadradar-antislopUI`  complementar a todas as outras **para uma interface melhor**.
