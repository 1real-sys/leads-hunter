# Sprints de implementação do frontend — Leads Hunter

Este documento organiza a implementação do frontend do MVP em sprints curtos, completos e executáveis em sequência. Ele deve ser lido junto com [AGENTS.md](AGENTS.md), [fluxo.md](fluxo.md), [API.md](API.md) e `.opencode/skills/leadradar-frontend/SKILL.md`.

## Estado atual

- Backend do MVP concluído e validado para uso local.
- API REST documentada em `API.md`.
- Frontend Angular criado em `frontend/` e validado com a suíte inicial e o build.
- Node.js disponível: `v26.7.0`.
- npm disponível: `12.0.2`.
- Angular CLI global não instalado; o projeto usa a CLI local `22.1.6`.
- Angular efetivamente instalado: `22.1.4`.
- Próximo sprint: **FE-09 — Drag-and-drop com persistência de status**.

## Decisões do plano

- O frontend ficará no diretório `frontend/` dentro deste repositório.
- A criação inicial seguirá `angular-new-app`; depois, o desenvolvimento seguirá `angular-developer` e `leadradar-frontend`.
- Para tarefas que envolvem criação e desenvolvimento de UI, usar `leadradar-antislopUI` como complementar
- Será usada a versão estável do Angular disponível no momento do bootstrap, respeitando o mínimo Angular 21+ definido pela skill.
- A aplicação será uma SPA local, sem SSR, autenticação, cadastro, RBAC ou deploy remoto.
- TypeScript permanecerá em modo `strict` e os componentes serão standalone.
- A navegação terá as áreas Busca, Kanban e Histórico.
- O frontend usará um proxy local para encaminhar `/api` ao backend em `http://localhost:8080`, evitando alterar CORS no backend para o desenvolvimento local.
- URLs de endpoints não serão espalhadas pelos componentes; cada domínio terá integração HTTP centralizada.
- Signals serão usados para estado local/derivado. RxJS será usado somente onde HTTP, cancelamento, debounce ou composição assíncrona justificarem.
- O mapa usará Leaflet, com marcador arrastável e círculo de raio.
- O Kanban usará Angular CDK Drag and Drop.
- O link de WhatsApp virá de `whatsappUrl`; o frontend não repetirá a normalização de telefone e nunca enviará mensagens automaticamente.
- A atualização comercial usará somente a rota real `PATCH /api/leads/{id}`. Não serão inventadas rotas `/status`, `/observacoes` ou `/contato`.
- CSV e XLSX serão baixados do backend; o frontend não recriará as regras de exportação.
- Não existe geocodificação no backend. `enderecoBase` será texto descritivo; latitude e longitude virão do mapa/formulário.
- Não será adicionada biblioteca de estado global, dashboard, analytics ou abstração arquitetural sem necessidade concreta.

## Contratos que o frontend deve preservar

- `CategoriaNegocio`: `MERCADO`, `PADARIA`, `DOCERIA`, `RESTAURANTE`, `DISTRIBUIDORA`, `ACOUGUE`, `FARMACIA`, `OUTROS`.
- `StatusFunil`: `NOVO`, `QUALIFICADO`, `CONTATADO`, `GANHO`, `PERDIDO`.
- `Temperatura`: `QUENTE`, `MORNO`, `FRIO`.
- Busca: latitude entre -90 e 90, longitude entre -180 e 180, raio inteiro de 1 a 20 km e ao menos uma categoria.
- Datas do backend são strings ISO-8601 locais, sem offset/fuso.
- Listagens retornam arrays diretos e não possuem paginação atualmente.
- Campos opcionais podem vir como `null`.
- Erros tratados seguem `ApiErrorResponse`: `timestamp`, `status`, `codigo`, `mensagem` e `path`.
- A resposta inicial da busca possui leads resumidos; `GET /api/leads` possui o contrato completo do lead.
- No histórico, `scoreNaBusca` e `temperaturaNaBusca` representam o snapshot da execução; status, observações e último contato são atuais.
- O PATCH preserva campos omitidos ou nulos. Observações podem ser limpas com string vazia; `ultimoContatoEm` não pode ser limpo enviando `null` no contrato atual.

## Status permitidos para os sprints

- `PENDENTE`
- `EM ANDAMENTO`
- `CONCLUÍDO`
- `BLOQUEADO`

Ao iniciar um sprint, alterar seu status para `EM ANDAMENTO`. Somente marcar `CONCLUÍDO` depois dos critérios de aceite e validações passarem. Um bloqueio deve registrar causa objetiva e o que falta para prosseguir.

## Definition of Done comum

Todo sprint deve cumprir, conforme seu escopo:

- comportamento funcional entregue de ponta a ponta dentro do frontend;
- contratos TypeScript compatíveis com a API real;
- estados `loading`, `success`, `empty` e `error` quando houver operação assíncrona;
- acessibilidade básica e navegação por teclado nas interações adicionadas;
- testes dos comportamentos importantes sem remover ou ignorar testes existentes;
- nenhuma URL, enum ou payload inventado;
- nenhuma alteração silenciosa no backend para acomodar o frontend;
- `npm test` no modo não interativo, usando o script real gerado pelo projeto;
- `npm run build` concluído sem erro;
- `fluxo.md` atualizado com o estado real;
- `HISTORICO_IMPLEMENTACOES.md` atualizado quando o sprint representar implementação relevante.

## Veredito obrigatório de conclusão

Ao terminar a implementação ou a revisão de qualquer sprint, o agente deve retornar explicitamente se **todo o escopo, todos os critérios de aceite e todas as validações obrigatórias passaram**. Não basta informar que o código foi criado, que os testes passaram ou que o build compilou.

O relatório de encerramento deve conter, obrigatoriamente:

1. cada item da seção `Escopo`, marcado individualmente como `PASSOU`, `FALHOU` ou `NÃO VALIDADO`, acompanhado da evidência correspondente;
2. cada item da seção `Critérios de aceite`, marcado individualmente como `PASSOU`, `FALHOU` ou `NÃO VALIDADO`, acompanhado da evidência correspondente;
3. cada comando ou procedimento da seção `Validação`, com o resultado realmente obtido;
4. um veredito final usando exatamente uma destas formas:
   - `SPRINT APROVADO — todo o escopo e todos os critérios de aceite passaram.`
   - `SPRINT NÃO APROVADO — existem itens que falharam ou não foram validados.`

Regras para o veredito:

- `PASSOU` exige implementação existente e verificação objetiva; suposição, inspeção superficial ou resultado de build isolado não são evidência suficiente para comportamento funcional;
- se qualquer item de escopo ou critério de aceite estiver como `FALHOU` ou `NÃO VALIDADO`, o sprint inteiro deve receber `SPRINT NÃO APROVADO`;
- um sprint não aprovado deve permanecer `EM ANDAMENTO` ou `BLOQUEADO`, conforme o caso, e o relatório deve listar claramente o que falta;
- é proibido marcar o sprint como `CONCLUÍDO`, atualizar o próximo passo ou declarar a entrega validada enquanto existir item pendente, falho ou não validado;
- depois de uma correção, os itens afetados e as validações obrigatórias devem ser executados novamente antes de emitir novo veredito;
- problemas encontrados não podem ser omitidos do relatório para permitir a conclusão do sprint.

Esta verificação é responsabilidade do agente que implementa e também do agente que revisa. O revisor não deve confiar no status documentado sem confrontá-lo com o código e com evidências de validação.

## Visão geral

| Sprint | Entrega | Dependência | Status |
| --- | --- | --- | --- |
| FE-00 | Bootstrap Angular e execução local | Nenhuma | CONCLUÍDO |
| FE-01 | Contratos TypeScript e base HTTP | FE-00 | CONCLUÍDO |
| FE-02 | Shell, navegação e rotas | FE-00 | CONCLUÍDO |
| FE-03 | Mapa Leaflet interativo | FE-01, FE-02 | CONCLUÍDO |
| FE-04 | Formulário de busca sincronizado ao mapa | FE-03 | CONCLUÍDO |
| FE-05 | Execução da busca pela API | FE-04 | CONCLUÍDO |
| FE-06 | Apresentação dos resultados da busca | FE-05 | CONCLUÍDO |
| FE-07 | Consulta e filtros de leads | FE-01, FE-02 | CONCLUÍDO |
| FE-08 | Kanban somente leitura e cards | FE-07 | CONCLUÍDO |
| FE-09 | Drag-and-drop com persistência de status | FE-08 | PENDENTE |
| FE-10 | Detalhe do lead e WhatsApp manual | FE-08 | PENDENTE |
| FE-11 | Observações e último contato | FE-10 | PENDENTE |
| FE-12 | Lista do histórico de buscas | FE-01, FE-02 | PENDENTE |
| FE-13 | Detalhe de uma busca anterior | FE-12 | PENDENTE |
| FE-14 | Downloads CSV e XLSX | FE-07 | PENDENTE |
| FE-15 | Polimento integrado, responsividade e acessibilidade | FE-06 a FE-14 | PENDENTE |
| FE-16 | Testes de fluxo e fechamento do MVP | FE-15 | PENDENTE |

---

## FE-00 — Bootstrap Angular e execução local

**Status:** CONCLUÍDO

### Objetivo

Criar uma aplicação Angular moderna, mínima e validada, pronta para receber as funcionalidades do Leads Hunter.

### Escopo

- Confirmar novamente as versões de Node, npm e Angular CLI no momento da execução.
- Confirmar que a versão do Node é suportada pela versão Angular escolhida; se não for, usar uma versão LTS compatível sem alterar desnecessariamente o ambiente global.
- Como não há CLI global atualmente, usar o fallback `npx @angular/cli@latest` ou instalar globalmente somente se o usuário preferir.
- Criar o projeto no diretório `frontend/`, com routing, SCSS, componentes standalone, testes habilitados e sem SSR.
- Manter TypeScript `strict` e carregar a configuração de agente gerada pelo Angular CLI.
- Configurar scripts reais de build e testes no `package.json` gerado.
- Configurar proxy de desenvolvimento para `/api` → `http://localhost:8080`.
- Criar uma página inicial mínima que confirme que o Angular está renderizando.
- Registrar no documento a versão Angular efetivamente instalada.

### Fora do escopo

- Componentes de negócio.
- Leaflet, Angular CDK e chamadas à API.
- Escolha de biblioteca visual pesada.

### Critérios de aceite

- O projeto existe somente uma vez em `frontend/` e não sobrescreve o backend.
- `npm install` foi concluído e o lockfile foi gerado.
- A aplicação compila com TypeScript strict.
- O proxy local está configurado sem mudança de CORS no backend.
- A suíte inicial passa.

### Validação

- Executar o comando de teste não interativo definido pelo projeto.
- Executar `npm run build`.

---

## FE-01 — Contratos TypeScript e base HTTP

**Status:** CONCLUÍDO

### Objetivo

Criar a base fortemente tipada para integrar o frontend à API real.

### Escopo

- Configurar `provideHttpClient` conforme a versão Angular instalada.
- Criar tipos para `CategoriaNegocio`, `StatusFunil`, `Temperatura` e `ApiErrorResponse`.
- Criar tipos para `BuscaRequest`, `BuscaResponse`, `BuscaResumoResponse`, `BuscaDetalheResponse`, `LeadResponse` e `AtualizarLeadRequest`.
- Representar campos opcionais/nulos e datas como o backend realmente retorna.
- Criar utilitário simples para transformar falhas HTTP em mensagem segura para a interface.
- Centralizar o prefixo relativo `/api`; não hardcodar host nos componentes.
- Criar testes de contrato/utilitário sem chamar rede real.

### Fora do escopo

- Implementar todos os services antecipadamente.
- Interceptor de autenticação.
- Estado global.

### Critérios de aceite

- Nenhum DTO usa `any`.
- Enums e nomes de campos coincidem com Java/API.md.
- Erro desconhecido possui fallback legível e não mostra stack trace.
- A base HTTP pode ser usada pelas features seguintes.

### Validação

- Testes dos tipos auxiliares e do mapeamento de erro.
- `npm run build`.

### Resultado

- `provideHttpClient()` configurado na aplicação.
- Contratos tipados da API organizados em `shared/models`, com enums literais, campos nulos/opcionais e datas como strings ISO.
- Prefixo relativo `/api` e rotas conhecidas centralizados em `core/api/api-routes.ts`.
- Falhas HTTP convertidas em mensagens seguras por `core/api/api-error-message.ts`.
- `AtualizarLeadRequest` exige em compilação ao menos um campo com valor não nulo, como o backend.
- Testes de contrato cobrem também os DTOs aninhados das buscas e payloads de atualização inválidos, sem rede real.

---

## FE-02 — Shell, navegação e rotas

**Status:** CONCLUÍDO

### Objetivo

Entregar a estrutura navegável do aplicativo antes das telas de negócio.

### Escopo

- Criar shell com cabeçalho, navegação principal e área de conteúdo.
- Criar rotas para Busca, Kanban e Histórico.
- Usar carregamento lazy quando ele simplificar a separação das telas.
- Criar páginas-placeholder acessíveis para cada rota.
- Definir tokens visuais básicos em SCSS: cores, espaçamento, tipografia, foco e estados.
- Criar fallback de rota desconhecida.
- Garantir navegação por teclado e indicação da rota ativa.

### Critérios de aceite

- As três áreas são acessíveis por URL e navegação visível.
- Recarregar uma rota mantém a tela correta no servidor de desenvolvimento.
- Foco e semântica do menu são adequados.
- Não existe autenticação ou guard sem necessidade.

### Validação

- Testes de roteamento e navegação.
- `npm run build`.

### Resultado

- Shell com marca, cabeçalho, navegação principal, área de conteúdo e rodapé.
- Rotas `/busca`, `/kanban` e `/historico` carregadas sob demanda, com títulos de página e indicação acessível da rota ativa.
- Páginas-placeholder sem lógica de negócio e fallback para rotas desconhecidas.
- Tokens visuais básicos, foco visível, skip link e layout responsivo adicionados sem biblioteca visual.
- Suíte de navegação validada com `RouterTestingHarness` e smoke test de rota profunda no servidor de desenvolvimento.

---

## FE-03 — Mapa Leaflet interativo

**Status:** CONCLUÍDO

### Objetivo

Entregar um mapa reutilizável que controle o ponto central e o raio da busca.

### Escopo

- Adicionar somente as dependências necessárias do Leaflet e seus tipos.
- Configurar CSS e assets dos marcadores de modo compatível com o build Angular.
- Criar componente de mapa com ponto central tipado.
- Permitir clique no mapa e arraste do marcador.
- Exibir círculo proporcional ao raio em quilômetros.
- Atualizar círculo sem recriar a instância inteira do mapa.
- Definir fonte de tiles e atribuição válidas no momento da implementação.
- Encerrar listeners/instância no ciclo de vida correto.

### Critérios de aceite

- Clique e drag emitem latitude/longitude válidas.
- Alterar o raio atualiza o círculo imediatamente.
- O mapa não duplica instâncias ao navegar entre rotas.
- A atribuição da fonte de mapa permanece visível.

### Validação

- Testes da lógica de coordenadas/raio sem depender de tile externo.
- Teste manual de clique, drag, zoom e navegação.
- `npm run build`.

### Resultado

- Leaflet `1.9.4` e seus tipos foram adicionados como as únicas dependências do mapa, com CSS global e assets locais do marcador configurados no build Angular.
- O componente reutilizável recebe ponto central e raio tipados, emite coordenadas válidas por clique ou drag, oferece confirmação do centro visível por teclado e atualiza marcador/círculo sem recriar o mapa.
- Tiles HTTPS do OpenStreetMap foram configurados com atribuição visível e sem prefetch, geocodificação ou armazenamento offline.
- Listeners e instância são encerrados ao destruir o componente, permitindo sair e retornar à rota sem duplicação.
- Testes isolam o Leaflet e validam coordenadas, raio, interação, atualização incremental e cleanup sem acessar tiles externos.

---

## FE-04 — Formulário de busca sincronizado ao mapa

**Status:** CONCLUÍDO

### Objetivo

Permitir configurar uma busca válida usando mapa e formulário sincronizados.

### Escopo

- Escolher Signal Forms ou Reactive Forms conforme a versão Angular instalada; não misturar estratégias.
- Implementar `enderecoBase`, latitude, longitude, `raioKm` e múltiplas categorias.
- Aplicar limites reais: coordenadas geográficas, raio inteiro entre 1 e 20 e ao menos uma categoria.
- Sincronizar clique/drag do mapa com latitude e longitude do formulário.
- Sincronizar slider/campo de raio com o círculo do mapa.
- Exibir labels amigáveis sem mudar os valores dos enums enviados.
- Definir estado inicial claro; não simular geocodificação ou autocomplete inexistente.
- Exibir mensagens de validação acessíveis.

### Critérios de aceite

- Mapa e formulário nunca divergem silenciosamente.
- O submit permanece bloqueado quando o request seria rejeitado pelo backend.
- Seleção de categorias envia os valores exatos do enum.
- `enderecoBase` é tratado apenas como descrição opcional.

### Validação

- Testes de limites, categorias vazias e sincronização com o mapa.
- `npm run build`.

### Resultado

- Signal Forms foi adotado como estratégia única na versão Angular 22.1.4, com um modelo tipado compartilhado entre o formulário e a página.
- Endereço opcional, coordenadas, raio e categorias possuem as mesmas restrições do `BuscaRequest` do backend, incluindo endereço de até 255 caracteres, limites geográficos, raio inteiro de 1 a 20 km e ao menos uma categoria.
- Mapa, inputs numéricos e slider usam o mesmo estado: clique ou drag atualizam o formulário, e alterações válidas no formulário atualizam ponto e círculo sem recriar o mapa.
- As oito categorias exibem rótulos em português e mantêm no request os valores exatos de `CategoriaNegocio`.
- O estado inicial é explícito, o endereço é descrito como referência sem geocodificação e mensagens de erro são associadas aos controles.
- A confirmação local permanece bloqueada para payload inválido e não realiza HTTP; a execução real continua reservada ao FE-05.
- A suíte isolada validou limites, categoria vazia, payload tipado e sincronização nos dois sentidos, e a revisão com axe-core não encontrou violações automatizadas.

---

## FE-05 — Execução da busca pela API

**Status:** CONCLUÍDO

### Objetivo

Executar `POST /api/buscas` a partir do formulário e representar todos os estados da operação.

### Escopo

- Criar integração `BuscaApi` somente com o método necessário neste sprint.
- Enviar `BuscaRequest` tipado pelo proxy local.
- Impedir requisição inválida e submissões duplicadas por múltiplos cliques.
- Exibir loading durante a chamada.
- Guardar a `BuscaResponse` confirmada pelo backend.
- Tratar `400`, `429`, `502`, `503` e `500` usando `ApiErrorResponse` quando disponível.
- Permitir nova tentativa segura após erro.

### Critérios de aceite

- Uma busca válida retorna e mantém ID, total, data e leads da resposta.
- O botão fica indisponível enquanto a mesma operação está em andamento.
- Erros de Google/rate limit são compreensíveis e não mostram detalhes internos.
- Resposta vazia é sucesso, não erro.

### Validação

- Testes HTTP do método, payload, resposta e erros relevantes.
- Testes do estado de loading e prevenção de clique duplo.
- Smoke test local com backend, sem afirmar sucesso da Google se a chamada não for executada.
- `npm run build`.

### Resultado

- `BuscaApi` passou a encapsular o `POST /api/buscas` com `BuscaRequest` e `BuscaResponse` tipados.
- A página controla os estados `loading`, `success`, `empty` e `error`, bloqueia submissões concorrentes, mantém a resposta completa e libera uma nova tentativa após falha.
- Os erros `400`, `429`, `502`, `503` e `500` usam o envelope conhecido quando disponível e mantêm os fallbacks seguros já existentes para respostas desconhecidas.
- Os testes com `HttpTestingController` validaram método, rota, payload, resposta, códigos de erro, loading, clique duplo, retry e resposta sem leads.
- O smoke local enviou o payload pelo proxy Angular e recebeu `503 GOOGLE_PLACES_CONFIGURATION` do backend iniciado com `GOOGLE_PLACES_API_KEY` vazia; nenhuma chamada à Google foi executada.
- A suíte frontend concluiu 57 testes sem falhas e o build de produção foi gerado com sucesso.
- A auditoria automatizada com axe-core 4.13.0 no Firefox não encontrou violações na rota `/busca`.

---

## FE-06 — Apresentação dos resultados da busca

**Status:** CONCLUÍDO

### Objetivo

Transformar a resposta inicial da busca em uma visão útil e clara para o usuário.

### Escopo

- Exibir resumo da busca: endereço, raio, categorias, total e data.
- Exibir os leads resumidos retornados pelo `BuscaResponse`.
- Mostrar nome, categoria, endereço, telefone, score e temperatura quando existirem.
- Exibir WhatsApp somente quando `whatsappUrl` existir.
- Tratar resultado vazio com orientação clara.
- Permitir seguir para o Kanban após uma busca.
- Não apresentar rating/reviews nesse contrato resumido, pois eles não existem na resposta inicial.

### Critérios de aceite

- Campos nulos não geram textos ou links falsos.
- Temperatura não depende somente de cor.
- WhatsApp abre o link do backend em nova aba com proteção apropriada.
- Resultado vazio mantém a busca concluída e utilizável.

### Validação

- Testes de resultados preenchidos, parciais e vazios.
- Testes de presença/ausência do link WhatsApp.
- `npm run build`.

### Resultado

- A resposta confirmada pela API passou a exibir endereço de referência quando informado, raio, categorias, total retornado e data formatada.
- Os leads são apresentados em uma lista responsiva com nome, categoria, endereço, telefone, score e temperatura somente quando esses dados existem.
- A temperatura possui rótulo textual além da diferenciação visual, e o WhatsApp só aparece quando o backend retorna `whatsappUrl`, abrindo em nova aba com `noopener noreferrer`.
- O resultado vazio permanece como uma busca concluída, orienta ajustes de raio, ponto ou categorias e mantém disponível a navegação para o Kanban.
- Os testes cobrem respostas preenchidas, parciais, vazias e presença ou ausência do WhatsApp; a suíte concluiu 61 testes sem falhas e o build de produção foi gerado com sucesso.
- A auditoria com axe-core 4.13.0 no Firefox não encontrou violações na rota `/busca`; os estados dinâmicos dos resultados foram verificados pelos testes de componente.

---

## FE-07 — Consulta e filtros de leads

**Status:** CONCLUÍDO

### Objetivo

Criar a fonte de dados real do Kanban com filtros combináveis.

### Escopo

- Criar `LeadApi.listar` para `GET /api/leads`.
- Enviar filtros opcionais `status`, `categoria` e `temperatura` somente quando definidos.
- Criar barra de filtros simples e tipada.
- Carregar leads ao entrar na tela e ao aplicar/limpar filtros.
- Tratar loading, vazio, erro e retry.
- Evitar chamadas duplicadas e subscriptions sem encerramento.
- Preservar a ordenação retornada pelo backend.

### Critérios de aceite

- Filtros podem ser usados isoladamente ou combinados.
- Limpar filtros volta à consulta completa.
- Enum inválido nunca é produzido pela UI.
- Falha da API não apaga silenciosamente uma lista válida anterior sem feedback.

### Validação

- Testes HTTP de query params e resposta.
- Testes da aplicação/limpeza de filtros e estados da tela.
- `npm run build`.

### Resultado

- `LeadApi.listar` consulta `GET /api/leads` e envia somente os filtros definidos entre status, categoria e temperatura.
- A rota Kanban carrega a base persistida ao entrar e oferece uma barra compacta com filtros isolados ou combinados, aplicação, limpeza e retry.
- Os valores dos selects são limitados pelos enums conhecidos; valores manipulados e desconhecidos são descartados antes da chamada HTTP.
- Loading e chamadas simultâneas ficam bloqueados, enquanto sucesso, vazio e erro possuem feedback próprio. Uma falha posterior conserva a última lista válida visível e informa explicitamente esse comportamento.
- A prévia mantém a ordem recebida da API sem antecipar as colunas e os cards do FE-08.
- A suíte frontend concluiu 71 testes sem falhas e o build de produção foi gerado sem warnings. O smoke local confirmou HTTP 200 no backend e na rota `/kanban`, com capturas responsivas em desktop e mobile.

---

## FE-08 — Kanban somente leitura e cards

**Status:** CONCLUÍDO

### Objetivo

Apresentar os leads nas cinco colunas do funil antes de adicionar mutações por drag-and-drop.

### Escopo

- Criar colunas `NOVO`, `QUALIFICADO`, `CONTATADO`, `GANHO` e `PERDIDO`.
- Agrupar os `LeadResponse` pelo status real.
- Criar card compacto com nome, categoria, endereço resumido, telefone, rating, reviews, score e temperatura.
- Tratar campos nulos sem placeholders enganosos.
- Usar `track` estável pelo ID do lead.
- Manter filtros do FE-07 integrados ao board.
- Garantir leitura horizontal/vertical adequada em desktop.

### Critérios de aceite

- Cada lead aparece em exatamente uma coluna.
- Contagens das colunas refletem a lista carregada.
- Status e temperatura também são identificáveis por texto/ícone, não apenas cor.
- Lista vazia e coluna vazia possuem estados distintos e claros.

### Validação

- Testes de agrupamento, contagens e renderização parcial.
- `npm run build`.

### Resultado

- O board apresenta as cinco etapas reais do funil, com agrupamento puro por status, contadores derivados das listas e `track` pelo ID do lead.
- Cada coluna possui estado vazio próprio, enquanto a página mantém o estado global sem resultados do FE-07. Registros com status nulo ou inesperado não são classificados incorretamente e aparecem em uma coluna condicional `Sem etapa`.
- Os cards compactos mostram somente os dados disponíveis entre nome, categoria, endereço, telefone, nota, avaliações, score e temperatura; status e temperatura permanecem identificáveis por texto.
- Os filtros continuam integrados à mesma lista e o quadro permite comparação horizontal em telas estreitas sem comprometer a leitura vertical dos cards.
- A interface seguiu a direção visual operacional da skill antislop: hierarquia tipográfica contida, superfícies neutras, ausência de gradientes e sombras decorativas e cor semântica reservada à temperatura.
- A suíte frontend concluiu 80 testes sem falhas e o build de produção foi gerado sem warnings. A validação integrada carregou 18 leads pelo backend local no Firefox, gerou capturas em `1440x1200` e `390x1200` e terminou com zero violações no axe.

---

## FE-09 — Drag-and-drop com persistência de status

**Status:** PENDENTE

### Objetivo

Permitir mover leads no Kanban mantendo frontend e backend consistentes.

### Escopo

- Adicionar Angular CDK Drag and Drop.
- Conectar as cinco colunas.
- Ao mover, enviar `PATCH /api/leads/{id}` com `{ "status": "..." }`.
- Usar a resposta `LeadResponse` do backend como estado confirmado.
- Bloquear/reduzir movimentos concorrentes do mesmo card.
- Em erro, reverter o card para a coluna anterior e informar o usuário.
- Preservar filtros e demais dados do card.
- Oferecer alternativa acessível ao drag para mudança por teclado/controle selecionável.

### Critérios de aceite

- Movimento bem-sucedido permanece após recarregar a lista.
- Falha de PATCH não deixa o card na coluna errada.
- O frontend não usa rota `/status` inexistente.
- A mudança é possível sem depender exclusivamente do mouse.

### Validação

- Testes do payload PATCH, confirmação, rollback e bloqueio concorrente.
- Teste manual do CDK entre todas as colunas.
- `npm run build`.

---

## FE-10 — Detalhe do lead e WhatsApp manual

**Status:** PENDENTE

### Objetivo

Permitir consultar os dados completos de um lead e iniciar contato manual.

### Escopo

- Criar painel de detalhe simples, evitando modal excessivo.
- Usar os dados completos já carregados ou `GET /api/leads/{id}` quando atualização explícita for necessária.
- Exibir informações externas, scoring, status e dados comerciais.
- Abrir `whatsappUrl` em nova aba somente quando existir.
- Não reconstruir URL nem normalizar telefone no componente.
- Exibir ação indisponível de forma clara quando não houver WhatsApp.
- Tratar lead inexistente ou falha de atualização do detalhe.

### Critérios de aceite

- O painel abre/fecha com teclado e devolve foco corretamente.
- Dados nulos são omitidos ou identificados sem invenção.
- Nenhuma mensagem é enviada automaticamente.
- Link externo utiliza proteção apropriada para nova aba.

### Validação

- Testes de detalhe, 404, campos nulos e WhatsApp disponível/indisponível.
- `npm run build`.

---

## FE-11 — Observações e último contato

**Status:** PENDENTE

### Objetivo

Editar os dados comerciais do lead com salvamento explícito e feedback confiável.

### Escopo

- Carregar observações e último contato atuais no detalhe.
- Permitir editar observações sem salvar a cada tecla.
- Permitir registrar data/hora do último contato no formato compatível com `LocalDateTime`.
- Enviar somente campos alterados para `PATCH /api/leads/{id}`.
- Permitir limpar observações com string vazia, explicando a limitação atual para limpar `ultimoContatoEm`.
- Atualizar card/detalhe com a resposta confirmada.
- Impedir submit vazio e tratar `400`, `404` e `500`.
- Avisar sobre alterações não salvas ao fechar o editor, sem criar fluxo complexo.

### Critérios de aceite

- Campos omitidos não são sobrescritos.
- Salvamento bem-sucedido aparece imediatamente no detalhe.
- Erro preserva o texto digitado para nova tentativa.
- O frontend não promete limpar `ultimoContatoEm` com `null`.

### Validação

- Testes dos payloads parciais, resposta confirmada e preservação em erro.
- Testes de submit vazio e formato de data.
- `npm run build`.

---

## FE-12 — Lista do histórico de buscas

**Status:** PENDENTE

### Objetivo

Permitir revisar todas as buscas anteriores sem chamar novamente a Google.

### Escopo

- Adicionar `BuscaApi.listarHistorico` para `GET /api/buscas`.
- Exibir buscas na ordem fornecida pelo backend.
- Mostrar data, endereço-base, categorias, raio e total encontrado.
- Tratar loading, lista vazia, erro e retry.
- Permitir abrir uma busca pelo ID.
- Formatar datas locais sem inventar fuso horário.

### Critérios de aceite

- Entrar no histórico não dispara `POST /api/buscas`.
- Uma lista vazia possui orientação clara.
- Itens são navegáveis por teclado.
- Endereço nulo/vazio não quebra a apresentação.

### Validação

- Testes HTTP e dos quatro estados da tela.
- Testes de navegação para o detalhe.
- `npm run build`.

---

## FE-13 — Detalhe de uma busca anterior

**Status:** PENDENTE

### Objetivo

Exibir os leads encontrados em uma execução histórica, preservando a diferença entre scoring histórico e dados comerciais atuais.

### Escopo

- Adicionar `BuscaApi.buscarHistoricoPorId` para `GET /api/buscas/{id}`.
- Exibir parâmetros e resumo da busca.
- Exibir leads ordenados como retornados pelo backend.
- Rotular claramente `scoreNaBusca` e `temperaturaNaBusca` como valores daquela execução.
- Exibir status, observações e último contato como dados comerciais atuais.
- Permitir abrir WhatsApp manual quando `whatsappUrl` existir.
- Tratar ID inválido, 404, vazio e erro.

### Critérios de aceite

- A tela não chama a Google nem recalcula score.
- Snapshot histórico não é confundido com `LeadResponse.score` atual.
- Busca inexistente oferece retorno seguro à lista.
- Leads sem WhatsApp não exibem link quebrado.

### Validação

- Testes HTTP de sucesso/404 e renderização de snapshot.
- `npm run build`.

---

## FE-14 — Downloads CSV e XLSX

**Status:** PENDENTE

### Objetivo

Permitir baixar as exportações produzidas pelo backend usando os filtros da tela de leads.

### Escopo

- Criar integração para `/api/exportacao/leads.csv` e `/api/exportacao/leads.xlsx` com resposta binária.
- Reutilizar filtros opcionais de status, categoria e temperatura.
- Respeitar `Content-Type` e obter o nome de `Content-Disposition` quando disponível, com fallback conhecido.
- Criar download no navegador e liberar a URL temporária depois do uso.
- Exibir loading independente para CSV e XLSX.
- Tratar `400` e `500` sem tentar interpretar arquivo de erro como download válido.
- Não montar CSV/XLSX no frontend.

### Critérios de aceite

- CSV baixa como `leads.csv` e XLSX como `leads.xlsx` quando os headers atuais forem recebidos.
- Filtros enviados correspondem aos ativos na interface.
- Clique repetido enquanto o mesmo download está em andamento é bloqueado.
- Falha apresenta mensagem e não gera arquivo corrompido.

### Validação

- Testes HTTP de `blob`, query params, headers, fallback de nome e erro JSON.
- Smoke test local dos dois downloads.
- `npm run build`.

---

## FE-15 — Polimento integrado, responsividade e acessibilidade

**Status:** PENDENTE

### Objetivo

Uniformizar as telas já funcionais sem adicionar novas funcionalidades ao MVP.

### Escopo

- Revisar layout desktop e comportamento em larguras menores.
- Padronizar botões, campos, cards, badges, loaders, vazios e mensagens de erro.
- Revisar contraste, foco visível, labels, nomes acessíveis e ordem de tabulação.
- Garantir alternativas ao uso exclusivo de cor e drag.
- Revisar gerenciamento de foco em painel de detalhe e após erros/salvamentos.
- Eliminar HTTP duplicado e recriações desnecessárias do mapa.
- Revisar `track`, subscriptions e efeitos.
- Manter UX direta, sem dashboard, animações pesadas ou navegação profunda.

### Critérios de aceite

- Busca → resultados → Kanban → edição → histórico → exportação usa linguagem visual consistente.
- Fluxos principais funcionam apenas com teclado, exceto a interação espacial do mapa, que possui campos equivalentes.
- Não há overflow impeditivo nas larguras suportadas.
- Nenhuma otimização altera contratos ou comportamento funcional.

### Validação

- Executar toda a suíte frontend.
- Teste manual de teclado, foco e layout.
- `npm run build`.

---

## FE-16 — Testes de fluxo e fechamento do MVP

**Status:** PENDENTE

### Objetivo

Validar o frontend integrado ao backend local e registrar o encerramento das fases 5 a 8 do MVP.

### Escopo

- Revisar cobertura dos services HTTP, formulários, Kanban, rollback, edição, histórico e downloads.
- Adicionar poucos testes integrados dos fluxos que atravessam múltiplos componentes.
- Avaliar teste E2E somente para os caminhos principais, usando a ferramenta compatível com o projeto criado.
- Executar smoke test local com backend e MySQL.
- Validar os estados de Google indisponível/rate limit por simulação controlada quando possível, sem gastar cota desnecessariamente.
- Confirmar que WhatsApp continua exclusivamente manual.
- Atualizar `fluxo.md` e `HISTORICO_IMPLEMENTACOES.md` com resultados realmente executados.
- Registrar limitações conhecidas sem implementar backlog futuro.

### Critérios de aceite

- O fluxo busca → resultados → Kanban → atualização comercial → histórico → exportação funciona localmente.
- Reload confirma persistência de status, observações e último contato.
- Erros relevantes possuem feedback e caminho de recuperação.
- Todos os testes definidos passam e o build de produção conclui.
- Nenhuma funcionalidade fora do MVP foi adicionada.

### Validação

- Suíte frontend completa em modo não interativo.
- `npm run build`.
- Smoke test integrado documentado com resultados reais.

## Fora do escopo de todos os sprints atuais

- Autenticação, cadastro, JWT, sessão, roles, permissions e multiusuário.
- Deploy público, SSR e configuração de produção remota.
- Scraping/enriquecimento por Instagram.
- Envio automático ou em massa por WhatsApp.
- Baileys ou WhatsApp Business API.
- Redis, filas ou mensageria.
- Dashboard avançado, analytics complexo ou CRM completo.
- Mudanças de backend não solicitadas para antecipar necessidades futuras.

## Próximo passo operacional

Os sprints **FE-00** a **FE-08** estão concluídos e validados. O próximo sprint é o **FE-09 — Drag-and-drop com persistência de status**.
