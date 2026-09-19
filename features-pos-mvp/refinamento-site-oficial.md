# Refinamento — Site oficial do Google como semente da pesquisa

Planejamento em sprints para capturar o `websiteUri` que o Google Places já devolve e usá-lo como ponto de partida confiável da pesquisa inteligente, extraindo o Instagram do próprio site em vez de pagar o Brave para adivinhar.

## Objetivo

Aumentar a taxa de acerto da busca de site/Instagram e reduzir o uso do Brave. Hoje o `FIELD_MASK` pede telefone, rating e reviews (SKU Enterprise) e **descarta** o site oficial, que está no **mesmo SKU** — então capturá-lo não muda o preço da chamada. Com o site em mãos, a pesquisa abre a página oficial e tira os links sociais de lá.

## Regras

Não faça o codigo mais fácil, faça o com maior qualidade e melhor manuntenção futura

## Diagnóstico

- `PlacesApiClient.FIELD_MASK` não inclui `places.websiteUri`; o campo é conhecido pelo Google e jogado fora.
- `PlacesSearchResponse.PlaceResult` e a entidade `Lead` não têm campo de site.
- A pesquisa inteligente tenta achar o site e o Instagram via Brave. Quando não acha, gasta cota para redescobrir algo que o Google já respondeu.
- O `LeitorPaginaCandidata` já abre páginas (com teto e anti-SSRF), mas extrai apenas **texto**; não extrai os `href` dos links.
- Hoje o "site próprio" descoberto pela pesquisa é guardado como **texto** em `observacoes` (bloco `Site próprio:`/`Instagram:`), não como coluna do lead.

### Fato de custo (importante)

`places.websiteUri` pertence ao **Nearby Search Enterprise SKU** — o mesmo que já usamos por causa de `internationalPhoneNumber`, `rating` e `userRatingCount`. Adicionar o campo **não aumenta** o custo por chamada nem o número de chamadas.

## Decisões de produto

- O site retornado pelo Google é a **fonte oficial** do lead; deve ser persistido no `Lead`, como o CNPJ.
- A pesquisa inteligente **começa pelo site oficial**, quando existir, e só cai no Brave quando ele não existir ou não confirmar nada.
- Links sociais extraídos do site oficial entram como **candidatos** e ainda passam pelo classificador; nenhuma regra de precisão é removida.
- Abrir o site continua limitado pelo teto de **3 páginas por lead**, sem seguir redirecionamentos, restrito a HTTP/HTTPS público.
- Nenhuma dependência nova, nenhum custo novo.

## Fluxo alvo

```text
Busca (Google Places)
   |
   +--> grava Lead.website = websiteUri (quando existir)
   |
Buscar informações
   |
   +--> site oficial conhecido?
   |       sim -> abre a página 1x (teto de páginas)
   |              +--> extrai hrefs sociais (instagram.com/...)
   |              +--> candidato de site próprio + candidato de Instagram
   |              +--> classificador decide (telefone/endereço/CNPJ/identidade)
   |       não -> segue o fluxo Brave atual (até 3 consultas)
   v
Bloco de observações + coluna do site no lead
```

## Sprints

### SITE-00 — Capturar e persistir o site oficial

**Status: CONCLUÍDA.**

**Objetivo:** pedir, mapear e guardar o `websiteUri` do estabelecimento.

**Entregáveis:**

- `PlacesApiClient`: incluir `places.websiteUri` no `FIELD_MASK` e `websiteUri` no record `Place`.
- `PlacesResponseMapper`: propagar o site para `PlacesSearchResponse.PlaceResult` (novo campo `website`).
- `Lead`: nova coluna `website` (migration **V7**, `VARCHAR` com limite compatível com URL) e `atualizarDadosExternos` usando `atualizarSePresente(place.website(), lead::setWebsite)` — ausência **não** apaga um site já conhecido.
- `LeadResponse` (e `PaginaLeadsResponse`) e exportação CSV/XLSX expõem o site quando houver.
- Frontend: exibir o site no drawer do Kanban/histórico (rótulo neutro quando ausente).

**Critérios de aceite:**

- A chamada continua com o mesmo SKU; nenhum campo novo fora de Enterprise.
- Um `Lead` capturado com site recebe a coluna; um lead já conhecido não perde o site quando a nova resposta omite o campo.
- Nenhuma quebra nos testes de mapper/cliente/contrato existentes.

**Validação:**

```bash
./mvnw -Dtest=PlacesApiClientTest,PlacesResponseMapperTest,LeadControllerTest,ExportServiceTest test
```

Validação concluída com 46 testes backend direcionados, a integração JPA/Flyway com 6 testes e a suíte frontend com 239 testes e build de produção sem warnings. A migration V7 foi aplicada em banco MySQL temporário.

### SITE-01 — Extrair links sociais da página

**Status: CONCLUÍDA.**

**Objetivo:** o leitor de página passar a devolver, além do texto, os links sociais confiáveis.

**Entregáveis:**

- `LeitorPaginaCandidata`: além de meta/JSON/corpo, extrair `a[href]` (e `link[rel~=me]` se útil) e devolver um record `PaginaLida(texto, links)`, mantendo os limites de bytes, tipo de conteúdo e destino público já existentes. Manter o método atual delegando para o novo, para não quebrar os testes.
- Filtro dos links por `UrlCandidatoCanonicalizer` (`instagram.com` como perfil), reaproveitando a regra existente; descartar IPs privados e redirects.
- Testes: link no rodapé, link absoluto/relativo, URL de perfil válida vs post/reel, página sem links, link em host privado, falha técnica.

**Critérios de aceite:**

- O leitor continua retornando vazio em erro/bloqueio (nunca vira ausência conclusiva).
- Um `instagram.com/perfil` no HTML é extraído e canonicalizado; `instagram.com/p/...` e `reel/...` são descartados.
- Nenhum acesso novo além do que já era permitido.

**Validação:**

```bash
./mvnw -Dtest=LeitorPaginaCandidataTest test
```

Validação concluída com 47 testes direcionados (leitor e canonicalizador), cobrindo links absolutos/protocol-relative, `rel=me`, posts/reels, destinos privados, página sem links e falhas técnicas.

### SITE-02 — Pesquisa inteligente semeada pelo site oficial

**Status: CONCLUÍDA.**

**Objetivo:** usar o site oficial como ponto de partida antes de gastar Brave.

**Entregáveis:**

- `PesquisaWebInternaService` passa a receber o site do lead (via `PesquisaLeadDados`). Quando existir:
  - adiciona o site oficial como candidato `SITE_PROPRIO` (canonicalizado — se for rede social/marketplace, é descartado como site);
  - abre a página **uma vez** (consome 1 das 3 páginas) e injeta o texto como evidência;
  - adiciona os `instagram.com/perfil` extraídos como candidatos `INSTAGRAM`;
  - roda o classificador existente, sem regra nova de identidade.
- Ordem de custo: site oficial → páginas → Brave. O Brave só entra quando o lead não tem site ou a validação do site não confirmou, respeitando o teto de **3 consultas ao Brave**.
- `PesquisaLeadDados` ganha `website` (propagado de `Lead`).

**Critérios de aceite:**

- Lead com site oficial confirmado **não** consome Brave.
- Lead sem site mantém o comportamento atual.
- Nenhuma regra de identidade/limiar/veto alterada; o Instagram achado no site ainda precisa passar pelo classificador.
- Tetos de 3 consultas e 3 páginas preservados.

**Validação:**

```bash
./mvnw -Dtest=PesquisaWebInternaServiceTest,ClassificadorUrlPrecisaoTest test
```

Validação concluída com 71 testes direcionados no núcleo do serviço e 118 testes no conjunto ampliado com leitor, canonicalizador, serviço e classificador. O site oficial válido é aberto uma vez, seus links de Instagram entram no classificador sem chamada ao Brave quando há confirmação, URLs de redes sociais caem no fluxo anterior e o teto de três páginas permanece contado por lead.

### SITE-03 — Exibição, exportação e validação integrada

**Status: IMPLEMENTADA; validação real positiva limitada por indisponibilidade externa.**

**Objetivo:** fechar a feature com prova real e documentação.

**Entregáveis:**

- Confirmar no frontend/exportação o site do lead.
- Reproduzir um lead real com `websiteUri` (ex.: uma das lojas já capturadas) e comparar antes/depois: quantos passam a ter site e Instagram e quantas consultas ao Brave foram economizadas.
- Conferência manual da amostra para garantir que o Instagram veio do site oficial do próprio lead.
- Atualizar `fluxo.md`, `HISTORICO_IMPLEMENTACOES.md`, `API.md` e este arquivo.

**Critérios de aceite:**

- Nenhum falso positivo novo na amostra.
- Redução mensurável de consultas ao Brave nos leads com site.
- Suíte backend e frontend verdes e build sem warnings.

**Validação:**

```bash
./mvnw test
./mvnw -DskipTests package
cd frontend && npm test -- --watch=false && npm run build
```

Implementação e contratos foram validados com 492 testes backend (zero falhas/erros e nove opt-in ignorados), Flyway/JPA no MySQL temporário, 239 testes frontend, build Angular sem warnings e empacotamento do backend. A redução de consultas foi medida nos cenários controlados: um site confirmado encerra a pesquisa sem chamadas ao Brave; um site sem confirmação preserva as três chamadas previstas. O diagnóstico opt-in `SiteOficialLiveTest` tentou a página pública real usada na amostra do Supermercado Michel, mas o leitor recebeu resposta vazia por indisponibilidade/DNS do ambiente; por isso não há afirmação de extração positiva real nem de economia medida em produção.

Após a reprodução do caso Petz Vila Velha, o leitor também passou a extrair perfis de Instagram presentes em atributos dinâmicos (`data-href`, `data-url`, `data-link` e `onclick`) e em JSON/estado inicial da página. O teste controlado com o nome, endereço, telefone, site oficial e perfil da Petz confirmou o resultado com `usarBrave=false` e zero consultas ao gateway. A URL pública real da Petz respondeu `403 Access Denied` ao HTTP e ao Chromium headless do ambiente de validação; isso impede comprovar a extração positiva nessa página específica, sem justificar bypass de proteção externa.

O detalhe do Histórico agora permite controlar essa etapa por execução: o interruptor `Brave Search` vem ativado para preservar o fluxo atual; desligado, o worker continua validando o site oficial e candidatos já encontrados, mas não chama a API do Brave.

## Riscos e pontos de atenção

- **Nem todo lugar tem `websiteUri`.** Negócios pequenos costumam não ter; para esses, o fluxo Brave continua igual.
- **`websiteUri` pode apontar para rede social/marketplace** (Facebook, iFood, Linktree). O canonicalizador já rejeita esses como site próprio; nesses casos não há página oficial para raspar e a pesquisa volta ao fluxo normal.
- **Instagram do site pode ser o da rede, não o da filial.** O site oficial de uma rede costuma linkar o perfil matriz. Como o Instagram extraído ainda passa pelo classificador, a regra de filial continua valendo; se o site só oferecer o perfil genérico, o resultado pode ser o perfil da marca — aceitável, mas registrar como trade-off.
- **Site fora do ar ou com muro de consentimento:** tratado como ausência de evidência adicional, sem retry agressivo.
- **Precisão:** nada é aceito só por vir do site; a evidência independente (telefone/endereço/CNPJ) continua sendo exigida pelo classificador.
- **Contrato HTTP:** adicionar `website` às respostas é aditivo; respostas antigas sem o campo continuam compatíveis no frontend (campo opcional/anulável).

## Fora de escopo

- Crawling além de uma página do site oficial por lead.
- Uso de API paga de enriquecimento ou scraping de motores de busca.
- Reclassificação retroativa de blocos de observações já gravados.
- Alteração do `ScoringService` (avaliar uso do site no score em outro refinamento).
