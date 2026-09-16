# Refinamento — Novas categorias de prospecção (Informática, Vestuário, Pet Shop)

Planejamento em sprints para adicionar categorias de negócio próprias à busca de leads, em vez de forçar o usuário a usar **Outros**. Hoje a busca só oferece `MERCADO`, `PADARIA`, `DOCERIA`, `RESTAURANTE`, `DISTRIBUIDORA`, `ACOUGUE`, `FARMACIA` e `OUTROS`. Faltam, entre outras, **loja de informática**, **loja de roupa** e **loja de ração (pet shop)**.

## Objetivo

Permitir que o usuário selecione categorias específicas de loja na tela **Busca**, que apareçam com o nome correto nos filtros do Kanban, nos cards/drawer, no histórico e na exportação, e que o Google Places seja consultado pelo tipo oficial correspondente. Nada de cair em `OUTROS`.

## Decisão de produto (nomes e escopo)

Nomes propostos para o enum e rótulos exibidos:

| Enum | Rótulo | Tipos oficiais do Google (Table A) |
| --- | --- | --- |
| `INFORMATICA` | Informática | `electronics_store`, `cell_phone_store` |
| `VESTUARIO` | Vestuário | `clothing_store`, `womens_clothing_store`, `shoe_store`, `sportswear_store` |
| `PETSHOP` | Pet Shop / Ração | `pet_store` |

Observações:

- `computer_store` **não existe** na tabela oficial; o tipo de informática é `electronics_store` (+ `cell_phone_store` para lojas de celular).
- Para "loja de roupa", o núcleo é `clothing_store`; `womens_clothing_store` cobre moda feminina e `shoe_store`/`sportswear_store` cobrem calçados e artigos esportivos, por decisão de produto.
- "Loja de ração" é melhor representada por `pet_store`. O termo continua no rótulo para o usuário se reconhecer.
- `OUTROS` permanece existindo; passa a ser a última opção, não a muleta.

### Decisões confirmadas em 15/09/2026

- Nomes do enum aprovados: **`VESTUARIO`** e **`PETSHOP`** (sem alteração).
- `VESTUARIO` inclui também **`shoe_store`** e **`sportswear_store`**, além de `clothing_store` e `womens_clothing_store`.
- `INFORMATICA` e `PETSHOP` permanecem com os tipos já listados.

## Restrições e fatos verificados

- O Nearby Search (New) aceita **até 50 tipos** em `includedTypes`. Hoje o código usa `LinkedHashSet` e já deduplica; adicionar categorias **não** quebra o limite nem aumenta chamadas (a busca continua com `maxResultCount = 20`).
- Categorias são persistidas como texto (`Busca.categoriasBuscadas`, `VARCHAR(500)`). Adicionar valores ao enum **não exige migration**; o pior caso de todas as categorias selecionadas continua abaixo de 500 caracteres.
- O `ScoringService` dá +30 para qualquer categoria diferente de `OUTROS`; as novas categorias herdam a pontuação sem alteração.
- Não há necessidade de nova chamada, novo provedor, cache ou mudança de contrato HTTP: a categoria já trafega como string do enum.

## Arquitetura alvo

Uma categoria nova precisa existir em **quatro lugares** para funcionar de ponta a ponta:

1. **Enum do domínio** — `CategoriaNegocio` (backend) e `CATEGORIAS_NEGOCIO` (frontend).
2. **Tradução para o Google** — `PlacesApiClient.tiposGoogle` (busca) e `PlacesResponseMapper.inferirCategoria` (volta).
3. **Texto de apoio da pesquisa inteligente** — `ClassificadorUrlService.termosCategoria` e `GooglePesquisaWebClient.rotuloCategoria` (scraping legado).
4. **Rótulos de UI** — formulário de busca, filtros, Kanban, resultado da busca e histórico.

---

## Sprints

### CTG-00 — Modelo e mapeamento no backend

**Status: PLANEJADA.**

**Objetivo:** adicionar os valores ao enum e traduzir corretamente ida e volta para o Google Places.

**Entregáveis:**

- `lead/CategoriaNegocio.java`: adicionar `INFORMATICA`, `VESTUARIO`, `PETSHOP` (antes de `OUTROS`).
- `integracao/places/PlacesApiClient.java` (`tiposGoogle`): mapear cada categoria para os tipos oficiais, mantendo o `LinkedHashSet`.
- `integracao/places/PlacesResponseMapper.java` (`inferirCategoria`): reconhecer os tipos novos e devolver a categoria certa, **antes** do retorno `OUTROS`.
- `integracao/pesquisa/ClassificadorUrlService.java` (`termosCategoria`): termos de apoio por categoria (ex.: `informatica`/`eletronicos`/`celular`; `roupas`/`vestuario`/`moda`; `pet`/`petshop`/`racao`).
- `integracao/pesquisa/GooglePesquisaWebClient.java` (`rotuloCategoria`): rótulo textual para o modo scraping.

**Critérios de aceite:**

- `POST /api/buscas` com `["INFORMATICA"]` envia `includedTypes: ["electronics_store","cell_phone_store"]`, sem tipos inválidos.
- Um `Place` com types `[electronics_store, store, point_of_interest]` é inferido como `INFORMATICA`; `[pet_store]` como `PETSHOP`; `[clothing_store]` como `VESTUARIO`; nunca `OUTROS`.
- Uma busca antiga persistida (sem as novas categorias) continua desserializando normalmente.
- Testes unitários atualizados em `PlacesApiClientTest`, `PlacesResponseMapperTest` e `ClassificadorUrlServiceTest`.

**Validação:**

```bash
./mvnw -Dtest=PlacesApiClientTest,PlacesResponseMapperTest,ClassificadorUrlServiceTest test
```

### CTG-01 — Frontend: seleção, filtros e rótulos

**Status: PLANEJADA.**

**Objetivo:** mostrar e permitir escolher as novas categorias na busca e nos filtros, sem quebrar o `Record<CategoriaNegocio, ...>` do TypeScript.

**Entregáveis:**

- `shared/models/enums.model.ts`: adicionar os três valores em `CATEGORIAS_NEGOCIO`.
- `features/busca/busca-form.model.ts`: incluir as chaves em `ROTULOS_CATEGORIA`, em `criarBuscaFormInicial().categorias` e refletir na ordem do formulário.
- `features/kanban/kanban.model.ts`: incluir em `ROTULOS_CATEGORIA` (usado também pelos filtros).
- `features/historico/historico-page.ts` e `historico-detalhe-page.ts`: incluir nos rótulos locais.
- `features/busca/busca-resultados.ts`: incluir no mapa de rótulos.
- Ajustar specs que enumeram categorias (`busca-form.spec.ts`, `busca-form.model.spec.ts`, `api-contracts.spec.ts`, etc.).

**Critérios de aceite:**

- Os checkboxes **Informática**, **Vestuário** e **Pet Shop / Ração** aparecem na Busca e são enviados no request.
- Os mesmos valores aparecem no filtro de categoria do Kanban, no card, no drawer, no resultado e no histórico.
- O `Record<CategoriaNegocio, string>` continua exaustivo (compilação falha se faltar rótulo).
- Nenhuma opção cai em "Outros".

**Validação:**

```bash
cd frontend && npm test -- --watch=false
cd frontend && npm run build
```

### CTG-02 — Validação integrada e documentação

**Status: PLANEJADA.**

**Objetivo:** fechar a feature com uma execução real controlada e documentação sincronizada.

**Entregáveis / atividades:**

- Teste de integração do `BuscaService`/`PlacesApiClient` confirmando a montagem correta dos tipos para cada nova categoria.
- Chamada real opt-in à Google Places (1 município, raio pequeno) com `INFORMATICA`, `VESTUARIO` e `PETSHOP` para confirmar que retornam estabelecimentos e são classificados corretamente (sem persistir lixo; usar catálogo de teste).
- Atualizar `fluxo.md`, `API.md` (lista de categorias aceitas), `HISTORICO_IMPLEMENTACOES.md` e este arquivo com o resultado.
- Confirmar que `ScoringService` dá 30 pontos para as novas categorias e que a exportação CSV/XLSX mostra o rótulo/valor corretos.

**Critérios de aceite:**

- `./mvnw test` verde.
- `npm test` e `npm run build` verdes.
- Uma busca real com as três categorias retorna leads com categoria correta (não `OUTROS`).
- Exportação contém os leads dessas categorias.

**Validação:**

```bash
./mvnw test
./mvnw -DskipTests package
cd frontend && npm test -- --watch=false && npm run build
```

---

## Riscos e pontos de atenção

- **Mapa de rótulos exaustivo:** como o frontend usa `Record<CategoriaNegocio, string>`, esquecer um rótulo quebra a compilação — é um guarda-corpo bom, mas exige atualizar todos os mapas de uma vez.
- **Tipos genéricos:** `DISTRIBUIDORA` mapeia para `store`; como a busca pode combinar categorias, um resultado vindo de `store` pode ser inferido agora como `INFORMATICA`/`VESTUARIO`/`PETSHOP`. É desejável, mas muda a categoria inferida de alguns leads antigos; não há reclassificação retroativa.
- **`shoe_store`/`sportswear_store` em `VESTUARIO`:** decisão confirmada. Amplia o volume de resultados da categoria (calçados e esporte passam a contar como Vestuário) e exige que `PlacesResponseMapper.inferirCategoria` reconheça os quatro tipos antes do retorno `OUTROS`.
- **Sem migration:** confirmar que `categorias_buscadas` continua `VARCHAR(500)` e que a string de todas as categorias cabe; se um dia o enum crescer muito, revisar o tamanho.

## Fora de escopo

- Categorias novas além das três pedidas (ex.: construção, autopeças, móveis) — só se houver necessidade real.
- Reclassificação retroativa de leads já capturados.
- Alteração de `ScoringService`, do modelo N:N, do rate limit ou do cache.
