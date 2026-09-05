# Refinamento — IDHM no Lead e mapa coroplético do Brasil

Planejamento detalhado em sprints para a feature de **IDHM**. A **IDHM-00 está concluída e validada**; as sprints IDHM-01 a IDHM-05 continuam pendentes. Este documento é o plano de referência e será atualizado conforme o estado real da execução.

## Objetivo

1. Enriquecer cada `Lead` com o **município, UF e IDHM** da cidade onde o estabelecimento está localizado, no momento da captura por uma busca, persistindo esse dado para uso futuro no sistema de pontuação.
2. Exibir o IDHM do lead no **drawer de detalhe**, em um **badge discreto no card do Kanban** e nas **exportações CSV/XLSX**.
3. Adicionar ao mapa da página de busca uma **camada coroplética do Brasil por IDHM** (cada município pintado por faixa, com legenda e clique mostrando nome/UF/IDHM), carregada **sob demanda pelos limites visíveis**.

## Situação atual (ponto de partida)

- Backend: única migration `V1__criar_tabelas.sql`; `ddl-auto: validate` (Flyway). Não há coluna de município/UF/IDHM nem nada geográfico além de `latitude`/`longitude` em `Lead`.
- Criação/atualização automática de lead acontece em `BuscaService.persistirLead` (transação única, hoje já engloba o HTTP do Google Places). Cada estabelecimento (`PlaceResult`) traz lat/lng.
- `LeadResponse` é um record posicional (19 campos) consumido também por `ExportService` (colunas + mapeamento posicional) e por testes que usam `new LeadResponse(...)`.
- Frontend: único mapa Leaflet é `MapaBusca` (tiles OSM, marcador + círculo), sem camada vetorial. Não há infraestrutura de arquivos grandes nem endpoint de dados geográficos; o frontend fala apenas via `/api`.
- Não existe nenhuma referência a município/UF/IDHM/IBGE/GeoJSON no repositório.

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

### IDHM-02 — Backend: exposição na API e exportação

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

### IDHM-03 — Frontend: dado do lead

**Objetivo:** consumir e exibir município/UF/IDHM no frontend.

**Entregáveis:**
- `LeadResponse` (TypeScript) com campos **opcionais** (`municipioCodigoIbge?`, `municipioNome?`, `uf?`, `idhm?`, `idhmReferencia?`) para não quebrar os literais tipados das specs.
- Util compartilhado de classificação/rotulagem do IDHM (faixa textual e cor), com testes.
- **Drawer de detalhe** (`lead-detalhe`): seção de estabelecimento/localidade exibindo Município/UF e IDHM quando presentes (rotulagem neutra quando nulo).
- **Card do Kanban** (`lead-card`): badge discreto com o IDHM (e UF) quando presente, sem interferir em drag/clique/whatsapp; estilo no padrão do card.

**Critérios de aceite:**
- Drawer e card mostram o valor quando existe e omitem com neutralidade quando nulo.
- Testes de frontend do card, drawer e util de classificação passam; suíte completa passa.

### IDHM-04 — Frontend: mapa coroplético do Brasil por IDHM

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

### IDHM-05 — Validação integrada e documentação

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
