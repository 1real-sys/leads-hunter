# Refinamento — Blacklist de nomes na busca (cadastrável)

Planejamento em sprints para bloquear estabelecimentos por nome antes de virar Lead. **Implementação concluída e validada em 08/09/2026.**

## Objetivo

Permitir que o usuário cadastre, pelo **frontend**, nomes/trechos de estabelecimentos sem interesse (ex.: redes como "Supermercados BH" e "Extrabom") e impedir que esses resultados sejam persistidos como Lead no fluxo de busca, mantendo o Kanban e o histórico limpos.

## Contexto e limite técnico (importante)

- A busca não envia nome à Google: `POST /api/buscas` envia `latitude`, `longitude`, `raioKm` e `categorias`, e o backend usa o Nearby Search por tipo (`includedType`). **A Google não permite excluir nomes na requisição.**
- Consequência: a blacklist roda **somente sobre os resultados retornados**, depois que a chamada já foi feita. **Ela não economiza requisições**; serve para não criar Leads/vínculos para termos cadastrados e dar visibilidade de quantos foram ignorados.
- A blacklist é **gerenciada pelo usuário** (CRUD no frontend) e persistida no banco; não é lista fixa em código/YAML.

## Decisões de produto

- Termos bloqueados **persistidos em banco** e gerenciados por API REST consumida pelo frontend (cadastrar, listar e remover).
- Termo de teste adotado: **`Supermercados BH`**.
- Correspondência por **contém** (substring) do termo no nome do estabelecimento, normalizado (sem acentos, minúsculas, sem espaços nas bordas). Sem regex nesta entrega.
- **Unicidade pelo termo normalizado**: não existem dois cadastros iguais ("Supermercados BH" e "supermercados bh" são o mesmo).
- Bloqueio aplicado no `BuscaService` após a deduplicação por `googlePlaceId` dentro da resposta; o resultado bloqueado **não cria Lead nem `BuscaLead`**.
- `POST /api/buscas` expõe `totalBloqueados` (resultados únicos ignorados) no retorno imediato.
- `totalEncontrados` (persistido na `Busca`) permanece o total retornado pela Google, incluindo bloqueados.
- Leads já persistidos em buscas anteriores **não** são removidos retroativamente.

## Arquitetura alvo

- **Backend**
  - Entidade/repositório (ex.: `dev.jlm.leadshunter.bloqueio.NomeBloqueado`) com coluna de termo original + termo normalizado e índice único no normalizado; migration Flyway nova (`V3`).
  - `NomeBloqueadoService`: normaliza (NFKD + caixa baixa + trim), valida entrada e opera listar/cadastrar/remover.
  - `BuscaService` consulta os termos ativos no momento da busca e usa `estaBloqueado(nome)` ao filtrar os `placesUnicos`.
  - Controller `GET/POST/DELETE /api/bloqueios` seguindo o padrão de erros existente (`ApiExceptionHandler`).
- **Frontend**
  - Tipo/API para os endpoints de bloqueio.
  - Área de gestão (rota ou seção) para listar, cadastrar e remover termos, no padrão visual e de acessibilidade do projeto.

---

## Sprints

### BL-00 — Modelo e persistência

**Status: CONCLUÍDO.** A migration V3, a entidade e o repository foram implementados. O MySQL 8.1 validou as três migrations com `ddl-auto: validate`, e 2 testes JPA confirmaram persistência, ordenação e unicidade normalizada.

**Objetivo:** guardar os termos bloqueados no banco.

**Entregáveis:**
- Migration `V3__criar_nome_bloqueado.sql`: tabela `nome_bloqueado` com `id`, `termo` (texto exibido), `termo_normalizado`, `criado_em`; índice único em `termo_normalizado`.
- Entidade JPA `NomeBloqueado` e `NomeBloqueadoRepository` (findAll ordenado, consulta por `termoNormalizado`).

**Critérios de aceite:**
- Flyway aplica a `V3` sem `ddl-auto=update`; `ddl-auto: validate` passa.
- Duplicidade por termo normalizado é rejeitada no banco (índice único) e tratada no serviço.
- Teste de integração JPA confirma persistência, unicidade e ordenação.

### BL-01 — Serviço e API de gestão

**Status: CONCLUÍDO.** Foi adotado o mínimo de 3 e o máximo de 120 caracteres. A API lista, cadastra e remove por ID, normaliza com NFKD e possui erros padronizados para entrada inválida, duplicidade e ID inexistente. Os 23 testes direcionados de serviço/controller e tratamento de erro passaram após a ampliação feita na BL-02.

**Objetivo:** expor CRUD de termos bloqueados.

**Entregáveis:**
- `NomeBloqueadoService`: normalizar e validar termo (não vazio; tamanho máximo, ex.: 120; opcional mínimo de 3 caracteres para reduzir falso positivo — decisão a fechar), evitar duplicidade normalizada, listar em ordem de criação e remover por id ou termo.
- `NomeBloqueadoController`:
  - `GET /api/bloqueios` — lista os termos;
  - `POST /api/bloqueios` — cadastra um termo (corpo com `termo`);
  - `DELETE /api/bloqueios/{id}` — remove.
- Tratamento de `400` (payload inválido/duplicado) e `404` (id inexistente) no contrato padrão.

**Critérios de aceite:**
- Cadastro normaliza e devolve o registro persistido; cadastro duplicado normalizado retorna erro padronizado.
- Remoção de id inexistente retorna `404`; listagem retorna vazia quando não há cadastros.
- Testes HTTP/controller e de serviço cobrem os casos.

### BL-02 — Aplicação do bloqueio na busca

**Status: CONCLUÍDO.** O filtro por substring normalizada ocorre depois da deduplicação, lê os termos no momento de cada busca e informa `totalBloqueados`. Os testes unitários e os 3 cenários de integração JPA confirmaram contagem única, ausência de `Lead`/`BuscaLead`, lista vazia e histórico preservado.

**Objetivo:** usar os termos cadastrados ao filtrar resultados e reportar a contagem.

**Entregáveis:**
- Em `BuscaService.persistirLeads`: carregar os termos ativos (repositório) e descartar, após a deduplicação por `googlePlaceId`, resultados cujo nome contenha algum termo normalizado; contar apenas os únicos ignorados; não criar Lead nem `BuscaLead` para eles.
- `BuscaResponse` ganha `totalBloqueados` no retorno de `POST /api/buscas`; `totalEncontrados` inalterado; histórico segue registrando somente os leads persistidos.

**Critérios de aceite:**
- Busca com "Supermercados BH Centro" cadastrado + alvo válido ⇒ persiste só o alvo; `totalBloqueados = 1`.
- Nenhum Lead/`BuscaLead` para termo bloqueado; duplicado na mesma resposta não conta duas vezes.
- Lista vazia ⇒ comportamento idêntico ao atual.
- Testes de serviço e integração JPA cobrem bloqueio, duplicado, lista vazia e preservação do histórico.

### BL-03 — Frontend: gestão dos bloqueios

**Status: CONCLUÍDO.** A rota `/bloqueios` usa Signal Forms e oferece listagem, cadastro e remoção com estados acessíveis. O contrato da busca aceita `totalBloqueados` opcional e mostra a nota somente para valor positivo. Os 18 testes frontend direcionados e o build de produção passaram.

**Objetivo:** cadastrar/listar/remover termos pela interface.

**Entregáveis:**
- Modelos TypeScript e serviço HTTP (`BloqueioApi`) para `GET/POST/DELETE /api/bloqueios`.
- Área de gestão: listagem dos termos cadastrados, formulário de cadastro (com validação e mensagens seguras de erro) e ação de remover, com estados de carregamento/vazio e acessibilidade (labels, foco, mensagens de status).
- `BuscaResponse.totalBloqueados?: number` no tipo TS e nota neutra no resumo da busca quando > 0 ("N ignorados pelos bloqueios cadastrados").

**Critérios de aceite:**
- Cadastrar termo atualiza a lista; remover some da lista; erros da API exibem mensagens sem vazar detalhes.
- Nota de ignorados aparece apenas quando `totalBloqueados > 0` e não quebra com campo ausente.
- Testes dos componentes/serviços e build passam.

### BL-04 — Validação integrada e documentação

**Status: CONCLUÍDO.** A suíte backend passou com 140 testes e gerou o JAR executável; a suíte frontend passou com 189 testes e build de produção. O fluxo HTTP integrado e o smoke de navegador cadastraram `Supermercados BH`, mantiveram apenas o alvo permitido, informaram um bloqueado e removeram o termo. A auditoria da nova rota não encontrou violações WCAG A/AA nem overflow em desktop ou mobile. `API.md`, `fluxo.md`, `HISTORICO_IMPLEMENTACOES.md` e este arquivo foram sincronizados; `refinamento.md` permaneceu inalterado.

**Objetivo:** fechar com validações e registros.

**Entregáveis:**
- Backend: `./mvnw test` e build.
- Frontend: `npm test` e `npm run build`.
- Revisão ponta a ponta com "Supermercados BH" cadastrado.
- Atualizar: `API.md`, `fluxo.md`, `HISTORICO_IMPLEMENTACOES.md` e este arquivo (estado por sprint). `refinamento.md` (IDHM) não é alterado.

**Critérios de aceite:**
- Suítes backend/frontend e builds passam; documentação fiel ao comportamento real.

---

## Critérios de aceite gerais

- Bloqueio não altera cliente Google, cache, rate limit, scoring ou IDHM.
- Sem lista cadastrada ⇒ comportamento idêntico ao atual.
- Nenhuma requisição adicional é feita para compensar bloqueados (sem paginação automática).
- Termos são unicidade por normalização e persistidos no banco (não em código/YAML).

## Riscos e mitigações

- **Falso positivo de substring curta:** começar com termos completos ("Supermercados BH", "Extrabom") e permitir revisão/remoção pela própria interface; definir tamanho mínimo de cadastro (ex.: 3 caracteres).
- **Expectativa de economia de requisição:** o bloqueio **não** evita a chamada à Google; a interface não deve sugerir o contrário (nota neutra no frontend).
- **Leads antigos já persistidos** com nome agora bloqueado permanecem; limpeza retroativa fica fora do escopo.
- **Consistência entre cadastro e busca:** busca sempre lê a tabela no momento da execução; remoção de um termo vale para as próximas buscas (sem cache entre sessões nesta entrega, salvo decisão explícita).

## Fora de escopo

- Envio de nomes ou exclusão na requisição à Google (impossível na Nearby Search).
- Paginação automática (`nextPageToken`) para preencher slots com não bloqueados (custaria mais requisições).
- Whitelist de termos-alvo; bloqueio por categoria inteira; expressões regulares.
- Bloqueio retroativo de Leads existentes; regras por usuário/perfil (sem autenticação no escopo atual).
