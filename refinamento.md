# Refinamento — IDHM no Lead e mapa coroplético do Brasil

Planejamento detalhado em sprints para a feature de **IDHM**. As sprints **IDHM-00 a IDHM-05 estão concluídas e validadas**. Este documento registra o plano executado e o estado final da entrega.

## Objetivo

1. Enriquecer cada `Lead` com o **município, UF e IDHM** da cidade onde o estabelecimento está localizado, no momento da captura por uma busca, persistindo esse dado para uso futuro no sistema de pontuação.
2. Exibir o IDHM do lead no **drawer de detalhe**, em um **badge discreto no card do Kanban** e nas **exportações CSV/XLSX**.
3. Adicionar ao mapa da página de busca uma **camada coroplética do Brasil por IDHM** (cada município pintado por faixa, com legenda e clique mostrando nome/UF/IDHM), carregada **sob demanda pelos limites visíveis**.

## Situação atual

- Backend: migrations `V1__criar_tabelas.sql` e `V2__adicionar_geografia_lead.sql`, com `ddl-auto: validate` (Flyway). `Lead` já persiste código IBGE, município, UF, IDHM e referência.
- A criação/atualização automática em `BuscaService.persistirLead` resolve o município pelas coordenadas do estabelecimento usando o dataset offline. Dados comerciais e snapshots históricos continuam preservados.
- O backfill de leads anteriores existe em lotes de 100 e permanece opt-in por `leadhunter.backfill-municipio=true`; por padrão nenhum dado anterior é alterado no startup.
- `LeadResponse`, a paginação e as exportações já expõem os dados geográficos. O backend também serve os municípios visíveis por bbox em GeoJSON pelo endpoint `/api/geografia/municipios`.
- Frontend: o card do Kanban e o drawer exibem localidade e IDHM quando disponíveis. O mapa Leaflet possui uma camada coroplética opcional, carregada por bbox conforme o viewport visível.

## Base de dados: IDHM 2010 (decisão confirmada)

- **Não existe série oficial de IDHM municipal pós-2010.** O Atlas Brasil (PNUD/IPEA/FJP) tem dados municipais dos Censos 1991, 2000 e 2010; a evolução até 2021 existe apenas em nível de **UF** (PNAD Contínua). Confirmado nas fontes oficiais (atlasbrasil.org.br / undp.org/pt/brazil).
- Base adotada: **IDHM 2010** (Atlas Brasil 2013) por município, com código IBGE.
- Fontes gratuitas: exportação do Atlas Brasil e bases abertas CC BY 4.0 (ex.: `atlascidade.com.br/dados`, 5.571 municípios com código IBGE e IDHM 2010).
- Malha municipal: API oficial de Malhas Geográficas do IBGE, com atribuição ao IBGE e condições de uso compatíveis com CC BY 4.0; a simplificação é feita pelo próprio gerador da IDHM-00.
- Custo: R$ 0. Nenhuma API key e nenhuma chamada externa em runtime.

## Decisões de produto

- Persistir `idhm_referencia` (2010) junto do valor, para permitir futura troca de base sem quebrar schema.
- **Não alterar `ScoringService`/score nesta entrega**; o campo fica pronto para uso futuro.
- Leads sem coordenadas, fora do Brasil ou sem IDHM no dataset ficam com campos nulos (exibição neutra).
- Backfill de leads existentes: **opt-in** no startup via propriedade `leadhunter.backfill-municipio=true` (default off), idempotente, preenchendo somente leads com coordenadas e sem município.
- Exibição nesta entrega: drawer de detalhe, badge no card do Kanban e exportação CSV/XLSX. **Não** inclui resultados imediatos da busca nem histórico.
- Camada coroplética: opcional (toggle), sob demanda por bbox, classificação oficial em 5 faixas + "sem IDHM".

## Arquitetura alvo

- Novo pacote `dev.jlm.leadshunter.geo` (ou `municipio`) no backend, com:
  - carregador do dataset `src/main/resources/geo/municipios-idhm.json` (malha simplificada + IDHM + envelope bbox por município);
  - `MunicipioService`: point-in-polygon offline com pré-filtro por bbox;
  - controller `GET /api/geografia/municipios?bbox=...` devolvendo GeoJSON FeatureCollection (código IBGE, nome, UF, idhm, referência e polígono) para a camada do mapa.
- Dataset estático commitado no repositório (funciona offline e em testes); script de geração em `tools/idhm/` com procedência/licenças registradas.
- Frontend: util de classificação/cor de IDHM testável; camada Leaflet sob demanda com throttle no `moveend` e cache por célula do viewport.

---

## Sprints

### IDHM-00 — Dados: dataset e validação (spike)

**Status:** CONCLUÍDO em 05/09/2026.

**Objetivo:** produzir e congelar o dataset base, reproduzível e licenciado.

**Entregáveis:**
- Script one-off em `tools/idhm/` que: baixa/consome IDHM 2010 por município (código IBGE, nome, UF, valor) e a malha municipal simplificada; faz o join por código IBGE; calcula envelope (bbox) e simplifica a geometria; grava `src/main/resources/geo/municipios-idhm.json`.
- Arquivo de procedência e licenças junto ao script (`tools/idhm/README.md`).
- Checagem de licença das fontes de geometria; se não atender, fallback para simplificação própria da malha oficial IBGE.

**Critérios de aceite:**
- Dataset contém os 5.570 municípios (ou 5.571 conforme base), cada um com código IBGE, nome, UF, `idhm`, `idhm_referencia=2010` e geometria.
- Point-in-polygon de teste acerta **Vitória/ES** (`-20.3155, -40.3128`, IDHM 2010 ≈ 0.845) e **Curitiba/PR** (`-25.4284, -49.2733`, IDHM 2010 ≈ 0.823); valores exatos congelados a partir do dataset.
- Arquivo final razoável para servir subsets por bbox e carregar em memória no backend.

**Fora de escopo:** qualquer ingestão automática em runtime (sem download no boot).

**Resultado:** o gerador reproduzível em `tools/idhm/` combina os 5.571 registros do Atlas Cidade com as 5.570 geometrias municipais retornadas pela API oficial do IBGE. O artefato congelado contém 5.570 municípios, ocupa 3.709.696 bytes e possui SHA-256 `8c9ce54dff5eec54e7401ba2392e4305145edc4acb02c21388425393c6b56286`. Boa Esperança do Norte/MT (`5101837`) é a única diferença conhecida: não possui geometria na malha consumida nem IDHM 2010. As validações estrutural, de reprodutibilidade e point-in-polygon de Vitória/ES e Curitiba/PR passaram. Nenhuma carga ou chamada externa foi adicionada ao runtime da aplicação.

### IDHM-01 — Backend: modelo e enriquecimento do Lead

**Status:** CONCLUÍDO em 05/09/2026.

**Objetivo:** persistir município/UF/IDHM no lead e atribuí-los no fluxo de captura.

**Entregáveis:**
- Migration `V2__adicionar_geografia_lead.sql`:
  - `leads.municipio_codigo_ibge VARCHAR(7) NULL`
  - `leads.municipio_nome VARCHAR(120) NULL`
  - `leads.uf VARCHAR(2) NULL`
  - `leads.idhm DECIMAL(4,3) NULL`
  - `leads.idhm_referencia SMALLINT NULL`
  - índices leves (`idx_lead_uf`, `idx_lead_idhm`) — validar necessidade na execução.
- Campos correspondentes em `Lead.java`.
- `MunicipioService` (point-in-polygon offline, pré-filtro por bbox) + DTO interno de resultado (`MunicipioInfo`: código, nome, UF, idhm, referência).
- Integração em `BuscaService.persistirLead`: após `atualizarDadosExternos`, se o lead tiver lat/lng, resolver o município; se encontrado, setar os campos; se fora do Brasil ou sem correspondência, limpar os campos quando houver coordenadas. Sem chamada externa dentro da transação.
- Runner de backfill opt-in: `ApplicationRunner` ativado por `leadhunter.backfill-municipio=true`, preenchendo leads com coordenadas e `municipio_codigo_ibge IS NULL`.

**Critérios de aceite:**
- Nova busca cria/atualiza lead com município/UF/IDHM corretos para Vitória/ES e Curitiba/PR.
- Deduplicação por `googlePlaceId`, preservação de `status/observacoes/ultimoContatoEm` e snapshot de score/temperatura em `BuscaLead` permanecem intactos.
- `./mvnw test` passa (contexto Spring + MySQL + Flyway), incluindo nova migration e point-in-polygon.
- Leads sem coordenadas não quebram o fluxo e permanecem com geografia nula.

**Resultado:** a migration V2 adicionou os cinco campos geográficos e os índices de UF e IDHM. O dataset congelado é carregado do classpath com limite de tamanho, checksum e validação estrutural; `MunicipioService` aplica pré-filtro por bbox e point-in-polygon com suporte a Polygon, MultiPolygon e buracos. `BuscaService` preenche ou limpa a geografia de acordo com as coordenadas sem alterar score nem dados comerciais. O backfill opt-in consulta apenas leads pendentes em lotes de 100 ordenados por ID. A suíte backend passou com 111 testes, incluindo Flyway/Hibernate no MySQL, Curitiba, Vitória, ausência de coordenadas, integridade do dataset e comportamento do backfill desligado por padrão.

### IDHM-02 — Backend: exposição na API e exportação

**Status:** CONCLUÍDO em 05/09/2026.

**Objetivo:** expor os novos campos nos contratos e nas exportações, e servir a camada geográfica do mapa.

**Entregáveis:**
- `LeadResponse` com `municipioCodigoIbge`, `municipioNome`, `uf`, `idhm`, `idhmReferencia` (nullable) e atualização do `from(...)`.
- `PaginaLeadsResponse` herda automaticamente (lista de `LeadResponse`).
- `ExportService`: novas colunas UF, Município, IDHM no CSV e XLSX + ajuste do mapeamento posicional `valores(...)`.
- Atualização dos testes que constroem `LeadResponse` posicionalmente (`ExportServiceTest`, `LeadControllerTest`) e dos assertions de cabeçalho/células da exportação.
- Novo endpoint `GET /api/geografia/municipios?bbox=minLng,minLat,maxLng,maxLat`:
  - valida 4 doubles finitos em ordem correta (senão `400` no padrão `ApiExceptionHandler`/`ApiErrorResponse`);
  - filtra municípios cujo envelope intersecta o bbox;
  - responde GeoJSON FeatureCollection com propriedades `codigoIbge`, `nome`, `uf`, `idhm`, `idhmReferencia` e `Geometry` simplificada.
- `API.md` atualizado (endpoints e campos novos).

**Critérios de aceite:**
- `GET /api/leads`, `GET /api/leads/{id}` e exportações retornam/exportam os novos campos.
- `GET /api/geografia/municipios` devolve polígonos apenas da região pedida; bbox inválido retorna `400` com o contrato de erro padrão.
- Build e suíte backend passam.

**Resultado:** os cinco campos geográficos passaram a compor `LeadResponse` e, por consequência, as respostas de listagem, detalhe, paginação e atualização comercial. CSV e XLSX agora incluem UF, município e IDHM em posições cobertas por testes. O novo endpoint geográfico valida tamanho, quantidade, finitude, limites e ordem do bbox, filtra os envelopes municipais em memória e devolve `FeatureCollection` com `Polygon`/`MultiPolygon` simplificados e propriedades de IDHM. Entradas inválidas ou regiões com mais de 1.500 municípios retornam `400 REQUISICAO_INVALIDA` no contrato padrão. Respostas válidas recebem cache HTTP público por 24 horas. Após a revisão, a suíte backend passou com 120 testes e o pacote executável foi gerado com sucesso.

**Resolução da revisão:**

- **Teto de trabalho — válido e corrigido:** cada bbox pode retornar até 1.500 municípios. O serviço lê no máximo 1.501 candidatos e rejeita a consulta com `400 REQUISICAO_INVALIDA` antes de construir as geometrias quando o teto é excedido.
- **Interseção geométrica exata — não adotada:** o entregável define explicitamente filtro pela interseção dos envelopes. O Leaflet desenha a geometria correta e o novo teto limita o pior payload; adicionar segmento × retângulo seria uma regra distinta, mais complexa e sem ganho visual. O trade-off ficou explícito em `API.md` e `fluxo.md`.
- **MultiPolygon — válido e corrigido:** Sítio d'Abadia/GO (`5220702`) fixa em teste o tipo `MultiPolygon`, seus dois polígonos e os quatro níveis de coordenadas GeoJSON.
- **Teste isolado do parser — válido e corrigido:** cobre espaços, limites geográficos, formato, componentes extras/ausentes, `NaN`, infinitos, inversão, área zero e estouro do tamanho máximo.
- **Tipagem da geometria — válido e corrigido:** `PolygonGeometry` e `MultiPolygonGeometry` possuem coordenadas tipadas separadamente; `Geometry.coordinates` deixou de ser `Object`. `idhmReferencia` passou de `short` para `Short` no DTO público.
- **Região vazia — válido e corrigido:** bbox sobre o oceano fixa `FeatureCollection` com `features` vazia.
- **Cache — parcialmente válido:** não foi criado cache interno por bbox, que aceitaria combinações arbitrárias de chave para uma varredura barata e fixa. Como o dataset é imutável, respostas válidas agora recebem `Cache-Control: max-age=86400, public`; throttle e cache por viewport continuam pertencendo à IDHM-04.

### IDHM-03 — Frontend: dado do lead

**Status:** CONCLUÍDO em 05/09/2026.

**Objetivo:** consumir e exibir município/UF/IDHM no frontend.

**Entregáveis:**
- `LeadResponse` (TypeScript) com campos **opcionais** (`municipioCodigoIbge?`, `municipioNome?`, `uf?`, `idhm?`, `idhmReferencia?`) para não quebrar os literais tipados das specs.
- Util compartilhado de classificação/rotulagem do IDHM (faixa textual e cor), com testes.
- **Drawer de detalhe** (`lead-detalhe`): seção de estabelecimento/localidade exibindo Município/UF e IDHM quando presentes (rotulagem neutra quando nulo).
- **Card do Kanban** (`lead-card`): badge discreto com o IDHM (e UF) quando presente, sem interferir em drag/clique/whatsapp; estilo no padrão do card.

**Critérios de aceite:**
- Drawer e card mostram o valor quando existe e omitem com neutralidade quando nulo.
- Testes de frontend do card, drawer e util de classificação passam; suíte completa passa.

**Resultado:** `LeadResponse` passou a aceitar os cinco campos geográficos opcionais e anuláveis. Um util compartilhado concentra as faixas PNUD, os rótulos, a escala verde→vermelho, o estado neutro sem dado e a formatação brasileira com três casas. O card mostra um badge compacto com IDHM e UF, sem controles interativos adicionais; o drawer inclui Município/UF, valor, faixa e referência na seção Estabelecimento. Campos ausentes ou inválidos são omitidos. A revisão de acessibilidade do badge foi resolvida com papel semântico de imagem, nome acessível completo e texto de faixa visualmente oculto, sem depender apenas da cor. A suíte frontend passou com 171 testes, o build de produção concluiu sem warnings e a inspeção em 1.440 px e 390 px confirmou ausência de overflow e preservação de título, drag e WhatsApp.

**Observações da revisão:**

- **A faixa do IDHM no badge do card é transmitida quase só por cor, e o `aria-label` está num `<p>` sem `role`, que leitores de tela tendem a ignorar.** O badge mostra "IDHM 0,845 · ES" e a faixa ("Muito alto" etc.) apenas pelo círculo colorido. Sugestão: incluir a faixa como texto visualmente oculto (sr-only) dentro do badge, ex.: `<span class="sr-only">, faixa Muito alto</span>`, e/ou dar um `role` adequado ao elemento em vez de depender do `aria-label` em elemento genérico. No drawer a faixa já é texto visível (correto).
- **Sem discordância funcional** no restante: campos opcionais, omissão neutra de nulos, formatação `pt-BR` de três casas e faixas PNUD estão coerentes e testados.

**Resolução:** o badge agora usa `role="img"`, mantém um nome acessível com valor, faixa e UF, e inclui a faixa em texto visualmente oculto. A classificação não depende mais apenas da cor nem de `aria-label` em elemento genérico.

### IDHM-04 — Frontend: mapa coroplético do Brasil por IDHM

**Status:** CONCLUÍDO em 08/09/2026.

**Objetivo:** camada ligável no mapa da busca com municípios coloridos por IDHM.

**Entregáveis:**
- Toggle "IDHM" no `MapaBusca` (barra/controles existentes).
- Camada Leaflet GeoJSON alimentada por `GET /api/geografia/municipios?bbox=...`:
  - busca sob demanda em `moveend` com throttle/anti-duplicação e cache em memória por célula do viewport;
  - remoção limpa da camada ao desligar e na destruição do mapa.
- Classificação (convenção PNUD) e cores:
  - Muito Alto (≥ 0.800), Alto (0.700–0.799), Médio (0.600–0.699), Baixo (0.500–0.599), Muito Baixo (< 0.500); escala verde→vermelho; "sem IDHM" em cinza.
- Legenda visível com as faixas e a nota "referência 2010 (Atlas Brasil)".
- Popup no clique do município com nome, UF, IDHM e faixa.
- Ordem de camadas preservando marcador/círculo acima dos polígonos; hover/foco acessível e testes com Leaflet mockado (padrão de `mapa-busca.spec.ts`).

**Critérios de aceite:**
- Ao ligar a camada, municípios visíveis são pintados; ao navegar, novos polígonos são carregados sem chamadas duplicadas.
- Clique mostra cidade/UF/IDHM; legenda correta; "sem IDHM" discriminado.
- Marcar centro e círculo de raio continuam funcionando por cima da camada.
- Testes e build frontend passam.

**Resultado:** o mapa da Busca recebeu um switch IDHM que carrega o GeoJSON municipal somente quando ativado. O bbox é normalizado em células de viewport, chamadas de `moveend` são agrupadas por 250 ms, requisições obsoletas são canceladas e até 80 respostas são mantidas em cache na sessão. Viewports com amplitude acima de 5° não iniciam uma consulta potencialmente rejeitada pelo teto de 1.500 municípios; a camada permanece ativa e orienta o usuário a aproximar o mapa. Eventos dos polígonos não reposicionam mais o centro, a legenda não intercepta ponteiro e o contador identifica explicitamente a região carregada. A camada usa as cinco faixas PNUD e o estado cinza sem dado, apresenta legenda com referência ao Atlas Brasil 2010 e abre popup com localidade, valor e faixa. Os polígonos possuem nome acessível, foco visível e abertura por Enter/Espaço; por ser opt-in, cada município mantém seu próprio tabstop para navegação direta em áreas densas. Uma pane dedicada mantém os polígonos abaixo do círculo e do marcador, e o desligamento/destruição remove camada, timers, requisições e listeners. A suíte frontend passou com 179 testes, o build de produção terminou sem warnings e as inspeções em 1.440 px e 390 px não encontraram overflow nem violações WCAG A/AA após a estabilização das transições.

**Observações e decisões da revisão:**

- **Zoom amplo — decisão de produto:** a camada adota a alternativa de orientação em zoom baixo. Viewports acima de 5° não geram o bbox de 30° que provocava `400`; o toggle permanece ativo, sem camada renderizada, e informa “Aproxime o mapa para visualizar a camada de IDHM nesta região”. A visão país integral fica explicitamente fora do carregamento por causa do teto de 1.500 municípios do endpoint.
- **Conflito de interação com a seleção do centro — resolvido:** `bubblingMouseEvents: false` impede que o clique em um município abra o popup e altere simultaneamente o centro da busca.
- **Legenda — resolvida:** `.mapa-busca__idhm-legenda` usa `pointer-events: none`, preservando drag e zoom na área do mapa sem conteúdo interativo.
- **Contador — resolvido:** o status informa “municípios carregados na região”, deixando claro que o total representa a célula consultada, não necessariamente o viewport exato.
- **Navegação por Tab — decisão mantida:** a camada opt-in conserva um tabstop por município para que todos os polígonos permaneçam diretamente acessíveis; os testes cobrem foco, Enter e Espaço, inclusive o estado sem IDHM.

### IDHM-05 — Validação integrada e documentação

**Status:** CONCLUÍDO em 08/09/2026.

**Objetivo:** fechar a feature com validações e registros.

**Entregáveis:**
- Backend: `./mvnw test` (se o ambiente continuar exigindo o javaagent do Byte Buddy para Java 25, usar o mesmo `-DargLine` documentado no fluxo) e build.
- Frontend: `npm test` e `npm run build`.
- Revisão manual ponta a ponta (busca com lead em Vitória/ES e Curitiba/PR; badge, drawer, exportação e camada no mapa).
- Atualizar: `fluxo.md`, `HISTORICO_IMPLEMENTACOES.md`, `API.md` e este `refinamento.md` (estado real por sprint).

**Critérios de aceite:**
- Suítes backend e frontend passam; build sem warnings.
- Migração Flyway `V2` aplicada em MySQL sem `ddl-auto=update`.
- Nenhuma chamada externa introduzida em runtime; nenhum segredo adicionado.

**Resultado:** a suíte backend passou com 120 testes no Java 25 e o pacote executável foi gerado. Durante a inicialização integrada, Flyway validou as duas migrations no MySQL 8.1 e confirmou o schema `leadsradar` na versão 2; o Hibernate permaneceu em `ddl-auto: validate`. A suíte frontend passou com 179 testes e o build de produção terminou sem warnings. A revisão ponta a ponta, com respostas locais controladas, percorreu buscas para Vitória/ES e Curitiba/PR, confirmou os badges `0,845 / ES` e `0,823 / PR`, os dados de município e referência nos drawers, os downloads CSV/XLSX e a camada coroplética nos dois viewports, incluindo legenda e popup por teclado. Requisições a domínios externos foram bloqueadas durante a validação; a feature continua usando somente o dataset congelado em runtime e não adicionou secrets ao conteúdo versionado.

**Observações e resoluções da revisão:**

- **Validações reproduzidas no fechamento:** `./mvnw test` com 120 testes verdes no backend e, no frontend, `npm test` com 179 testes e `npm run build` sem warnings. O smoke de navegador controlado percorreu as buscas de Vitória/ES e Curitiba/PR, confirmou badges, drawers, CSV/XLSX, popups por teclado nos dois viewports geográficos e nenhum problema WCAG no Kanban em 1.440 px. Os tiles externos foram bloqueados durante a inspeção.
- **Execução após a remoção dos segredos:** sem `DB_PASSWORD` definida no ambiente atual, `./mvnw test` iniciou 120 testes, com 116 aprovações e 4 erros de contexto nos testes que dependem do MySQL autenticado. Os testes unitários de integração (`PlacesApiClientTest` e `ApiExceptionHandlerTest`) passaram; a suíte integrada deve ser repetida com as credenciais fornecidas por ambiente.
- **Segurança do `application.yml` — resolvida:** a senha do MySQL e a chave da Google Places passaram a ser lidas exclusivamente de `${DB_PASSWORD:}` e `${GOOGLE_PLACES_API_KEY:}`. Essas credenciais não permanecem no arquivo de configuração versionável; a chave conhecida também não aparece no histórico do Git.
- **Escopo de zoom amplo — decisão registrada:** a camada é regional e orienta aproximação acima de 5° para respeitar o teto de 1.500 municípios; a visão país integral não é carregada nesta versão.

---

## Critérios de aceite gerais

- Deduplicação por `googlePlaceId` e preservação dos dados comerciais (`status`, `observacoes`, `ultimoContatoEm`) intactas.
- `idhm` persistido em `DECIMAL(4,3)` com `idhm_referencia=2010`; sem alteração do `ScoringService`/score nesta entrega.
- Nenhuma API key nova; dados 100% livres e offline em runtime.
- Exibição consistente entre Kanban (badge), drawer, exportação e dados de busca/histórico quando aplicável.

## Riscos e mitigações

- **Volume da malha municipal**: geometria simplificada + envio apenas por bbox + dataset otimizado em memória no backend.
- **Precisão do point-in-polygon em bordas**: uso de simplificação moderada; testes com coordenadas centrais de Vitória e Curitiba; aceitar imprecisão irrelevante em bordas.
- **Municípios criados depois de 2010**: ficam sem IDHM (cinza/nulo), com `uf` e `municipio_nome` ainda preenchidos quando possível.
- **Quebra de testes posicionais** do `LeadResponse` e da exportação: mapeado (Fase 02) e corrigido junto.
- **Licença da malha simplificada**: confirmada na IDHM-00; a fonte é a malha oficial do IBGE e a redução geométrica é realizada localmente pelo gerador.

## Fora de escopo

- Alteração do `ScoringService`/score usando IDHM (futuro, depende destas colunas).
- Filtros de listagem/exportação por município, UF ou faixa de IDHM.
- Exibição de município/IDHM nos resultados imediatos da busca ou no histórico.
- Cadastro manual de município/IDHM.
- Dados de IDH em nível de UF/estado ou séries históricas.
- Qualquer integração externa em runtime (API de geocoding, tiles proprietárias etc.).
