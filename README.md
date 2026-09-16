# Leads Hunter

Aplicação web local para **prospecção de estabelecimentos comerciais**. Ela consulta a Google Places API por região e categoria, transforma os estabelecimentos encontrados em *leads*, enriquece cada um com telefone, endereço, município, IDHM e CNPJ, calcula um score de oportunidade, organiza tudo em um Kanban e permite exportar os resultados.

O projeto foi feito para uso **local e single-user** (uso pessoal e portfólio). Não há autenticação, multiusuário nem deploy.

> Este README cobre o projeto inteiro. O frontend Angular tem um README próprio em [`frontend/README.md`](frontend/README.md).

---

## O que é

Um **funil comercial de rua digitalizado**. Você aponta um ponto no mapa, escolhe um raio e categorias (padaria, mercado, restaurante, etc.). O backend fala com a Google Places, deduplica os resultados, ignora o que não interessa, enriquece os dados e guarda cada estabelecimento como um lead reutilizável entre buscas.

## Para que serve

- Prospectar clientes de um bairro/cidade sem sair cadastrando tudo na mão.
- Priorizar quem vale mais a pena contatar primeiro (score e temperatura).
- Acompanhar o andamento comercial de cada lead (`NOVO` → `QUALIFICADO` → `CONTATADO` → `GANHO`/`PERDIDO`).
- Guardar observações e o último contato sem perder o histórico.
- Ter o telefone pronto para um contato **manual** pelo WhatsApp.
- Exportar a lista filtrada para CSV/Excel.

## Principais recursos

- Busca de estabelecimentos por coordenadas, raio (até 20 km) e múltiplas categorias.
- Cache e rate limit locais para não estourar a cota da Google em buscas repetidas.
- Histórico de buscas, com o retrato de score/temperatura daquele momento.
- Deduplicação de leads por `googlePlaceId`, preservando os dados comerciais que você editou.
- Blacklist de nomes (ex.: ignorar redes que você não quer prospectar).
- Enriquecimento geográfico offline: município, UF e IDHM.
- Enriquecimento de CNPJ por município, endereço e nome, 100% local.
- Score de 0 a 95 e temperatura `FRIO`, `MORNO` ou `QUENTE`.
- Kanban com drag-and-drop (e alternativa por teclado), filtros e paginação por coluna.
- WhatsApp apenas como **link manual** (`https://wa.me/55...`). Não existe disparo automático.
- Exportação CSV e Excel com os filtros aplicados.
- Pesquisa inteligente (opcional) de site/Instagram dos leads, via API do Brave Search.

---

## Tecnologias (resumo)

| Camada | Stack |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1, Spring Web MVC, Spring Data JPA, Hibernate, Jakarta Validation, Flyway, Lombok |
| Banco | MySQL 8 + Flyway (`ddl-auto: validate`) |
| Integrações | Google Places API (New), API oficial do Brave Search (opcional), jsoup, Playwright (scraping desativado) |
| Utilidades | Caffeine (cache), Bucket4j (rate limit), Apache POI (XLSX), Jackson |
| Frontend | Angular 22, TypeScript 6 (strict), Signals, Angular CDK, Leaflet + OpenStreetMap, SCSS |
| Testes | JUnit 5 / Mockito / MockMvc com catálogo MySQL temporário isolado, Vitest, Playwright + axe-core |

Para o raio-X tecnológico detalhado, veja [`tecnologias.md`](tecnologias.md).

---

## Arquitetura (visão rápida)

```text
Navegador (Angular :4200)
   |  proxy /api  ->  :8080
   v
Spring Boot / Tomcat
   +--> MySQL local (:3306)
   +--> Google Places API (HTTPS)
   +--> Brave Search API (HTTPS, opcional)
   +--> datasets offline no classpath (IDHM) e base CNPJ no banco
```

O backend segue `Controller → Service → Repository/integração`. Entidades JPA não são expostas: a API usa DTOs/records próprios, com erros centralizados em um contrato JSON único.

---

## Requisitos para rodar

### Obrigatórios

- **Git**
- **JDK 25** (validado em Amazon Corretto 25.0.4)
- **MySQL 8.x** rodando em `localhost:3306`
- **Node.js** (validado em 26.7.0) e **npm** (validado em 12.0.2) — para o frontend
- **Chave da Google Places API (New)** — sem ela, `POST /api/buscas` retorna `503`
- Um navegador com acesso à internet (tiles do OpenStreetMap)

> Não é necessário instalar Maven: o projeto usa o **Maven Wrapper** (`./mvnw`).

### Opcionais

- **Chave da Brave Search API** — habilita a pesquisa de site/Instagram no Histórico. Sem ela, a funcionalidade responde indisponível de forma rápida.
- **Python 3.11+** — só para gerar/atualizar os datasets offline (`tools/cnpj/` e `tools/idhm/`).
- Espaço em disco: a base bruta mensal do CNPJ pode passar de vários GiB (fica fora do Git).

---

## Configuração com `.env`

As credenciais **não ficam no código**. O `application.yml` lê variáveis de ambiente e o `.env` é o lugar prático de guardá-las.

```bash
cp .env.example .env
# edite .env com os seus valores
```

Conteúdo esperado do `.env`:

```dotenv
DB_USERNAME=root
DB_PASSWORD=sua_senha_do_mysql
GOOGLE_PLACES_API_KEY=sua_chave_google
BRAVE_SEARCH_API_KEY=sua_chave_brave   # opcional
```

Variáveis opcionais de ajuste:

| Variável | Padrão | Efeito |
| --- | ---: | --- |
| `GOOGLE_PLACES_RATE_LIMIT_REQUISICOES` | `10` | Chamadas externas por janela |
| `GOOGLE_PLACES_RATE_LIMIT_PERIODO_SEGUNDOS` | `60` | Duração da janela do rate limit |
| `BUSCA_CACHE_EXPIRACAO_MINUTOS` | `30` | Validade do cache de buscas |
| `BUSCA_CACHE_TAMANHO_MAXIMO` | `100` | Máximo de buscas em cache |
| `PESQUISA_BRAVE_HABILITADO` | `true` | Liga/desliga a fonte Brave |
| `PESQUISA_SCRAPING_HABILITADO` | `false` | Reativa o scraping (desaconselhado) |

> **Importante:** o Spring Boot não lê o arquivo `.env` sozinho. Você precisa carregá-lo no ambiente antes de subir o backend:
>
> ```bash
> set -a
> source .env
> set +a
> ./mvnw spring-boot:run
> ```
>
> IDEs (IntelliJ/VS Code) também conseguem carregar o `.env` automaticamente; nesse caso basta rodar a aplicação pela IDE.

### Sobre as chaves de API

As chaves são **responsabilidade de quem roda o projeto**. O repositório não distribui, não empresta e não inclui nenhuma chave funcional.

- **Google Places API (New):** crie um projeto no Google Cloud, habilite a *Places API (New)* e gere uma chave. Coloque em `GOOGLE_PLACES_API_KEY`. Sem ela, a busca não funciona.
- **Brave Search API:** crie uma conta no Brave Search API e gere o token. Coloque em `BRAVE_SEARCH_API_KEY`. É opcional; só a pesquisa de Instagram/site depende dela.

As chamadas partem sempre do backend. A chave nunca é enviada ao navegador.

---

## Como rodar

### 1. Banco de dados

Crie o schema (o Flyway depois cria as tabelas):

```sql
CREATE DATABASE leadsradar CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Se quiser um usuário dedicado (recomendado):

```sql
CREATE USER 'leadhunter'@'localhost' IDENTIFIED BY 'sua_senha';
GRANT ALL PRIVILEGES ON leadsradar.* TO 'leadhunter'@'localhost';
FLUSH PRIVILEGES;
```

E ajuste `DB_USERNAME`/`DB_PASSWORD` no `.env`.

### 2. Backend

```bash
set -a; source .env; set +a
./mvnw spring-boot:run
```

A API sobe em `http://localhost:8080`. Health check: `GET /api/health` → `{"status":"UP"}`.

### 3. Frontend

Em outro terminal:

```bash
cd frontend
npm install
npm start
```

Abra `http://localhost:4200`. O proxy de desenvolvimento encaminha `/api` para `http://localhost:8080`.

---

## Enriquecimento de CNPJ por município (injeção da base local)

A correspondência de CNPJ do Leads Hunter é **100% local**: em runtime não existe consulta à Receita Federal. O que a aplicação faz é cruzar os leads (nome, endereço, número, CEP) contra um recorte da base pública do CNPJ que **você** carrega no MySQL, restrita aos municípios que te interessam.

O fluxo tem 4 passos: baixar a competência mensal → montar o manifesto → gerar o SQL do recorte desejado → importar no MySQL.

### Passo 1 — Baixar a competência mensal

No [catálogo oficial de Dados Abertos do CNPJ](https://dados.gov.br/dados/conjuntos-dados/cadastro-nacional-da-pessoa-juridica---cnpj), escolha **uma competência** e baixe para `tools/cnpj/sources/`:

- todos os lotes `Empresas*.zip`
- todos os lotes `Estabelecimentos*.zip`
- `Municipios.zip`

Calcule os checksums:

```bash
sha256sum tools/cnpj/sources/*.zip
```

### Passo 2 — Montar o manifesto

Crie `tools/cnpj/fontes-AAAA-MM.json` com a data-base, as URLs, os SHA-256 e **o recorte geográfico desejado**. Você escolhe por UF e/ou por municípios pontuais.

**Exemplo A — todo o Espírito Santo (`ufs: ["ES"]`):**

```json
{
  "dataBase": "2026-08-08",
  "fontes": [
    {
      "tipo": "empresas",
      "arquivo": "Empresas0.zip",
      "url": "https://arquivos.receitafederal.gov.br/dados/cnpj/dados_abertos_cnpj/2026-08/Empresas0.zip",
      "sha256": "SHA256_DE_64_CARACTERES_REVISADO"
    },
    {
      "tipo": "estabelecimentos",
      "arquivo": "Estabelecimentos0.zip",
      "url": "https://arquivos.receitafederal.gov.br/dados/cnpj/dados_abertos_cnpj/2026-08/Estabelecimentos0.zip",
      "sha256": "SHA256_DE_64_CARACTERES_REVISADO"
    },
    {
      "tipo": "municipios",
      "arquivo": "Municipios.zip",
      "url": "https://arquivos.receitafederal.gov.br/dados/cnpj/dados_abertos_cnpj/2026-08/Municipios.zip",
      "sha256": "SHA256_DE_64_CARACTERES_REVISADO"
    }
  ],
  "ufs": ["ES"]
}
```

**Exemplo B — só municípios específicos:**

```json
{
  "dataBase": "2026-08-08",
  "fontes": [ "..." ],
  "municipiosInteresse": [
    { "codigoIbge": "4106902", "nome": "Curitiba", "uf": "PR" },
    { "codigoIbge": "3205309", "nome": "Vitória", "uf": "ES" }
  ]
}
```

**Exemplo C — combinar UF + municípios (união, sem duplicar):**

```json
{
  "dataBase": "2026-08-08",
  "fontes": [ "..." ],
  "ufs": ["ES"],
  "municipiosInteresse": [
    { "codigoIbge": "4106902", "nome": "Curitiba", "uf": "PR" }
  ]
}
```

O manifesto aceita `ufs` e/ou `municipiosInteresse`, mas **pelo menos um** precisa ter itens. Cada UF é expandida pelo catálogo `tools/cnpj/municipios-ibge.csv` (5.570 municípios). Repita as entradas de `fontes` para **todos** os lotes publicados na competência.

### Passo 3 — Gerar o SQL do recorte

```bash
python3 tools/cnpj/gerar_dataset.py \
  --manifest tools/cnpj/fontes-2026-08.json \
  --source-dir tools/cnpj/sources \
  --output-sql /tmp/cnpj-es.sql \
  --no-json \
  --workers 0
```

- `--workers 0` usa até 8 processos automaticamente (mais rápido); `--workers 1` reduz memória.
- `--no-json` gera só o SQL. Sem ele, também sai um JSON em `src/main/resources/cnpj/`.
- Mantenha a saída em `/tmp` enquanto revisa; o arquivo é grande e **não deve ser versionado**.

### Passo 4 — Importar no MySQL

```bash
mysql -u root -p leadsradar < /tmp/cnpj-es.sql
```

Se o MySQL estiver em container:

```bash
docker exec -i mysql-db mysql -u root -p leadsradar < /tmp/cnpj-es.sql
```

A carga é **idempotente para o mesmo recorte/competência**: ela remove e reinsere apenas os municípios selecionados. Confira as contagens:

```bash
mysql -u root -p leadsradar -e \
  "SELECT COUNT(*) FROM cnpj_estabelecimento; SELECT COUNT(*) FROM cnpj_empresa; ANALYZE TABLE cnpj_estabelecimento, cnpj_empresa;"
```

### Como o CNPJ aparece nos leads

Depois da carga, o enriquecimento acontece em dois momentos:

1. **Durante uma nova busca**, para cada lead do município que ainda não tem CNPJ.
2. **Sob demanda**, via `POST /api/buscas/{id}/cnpj` (botão **Buscar CNPJ** no Histórico), que tenta apenas os leads daquela busca com `cnpj` nulo.

A correspondência exige candidato único, município, endereço, número e nome compatíveis, com CEP como filtro forte e limiar maior no fallback sem CEP. Ambiguidade, baixa confiança ou excesso de candidatos deixam o lead **sem** CNPJ (falha segura). CNPJ, razão social, competência e confiança ficam persistidos.

> A migration repetível `src/main/resources/db/migration/R__carregar_subset_cnpj.sql` no Git é um **placeholder vazio de propósito**. A base volumosa vive apenas no seu MySQL local.
>
> Sem base carregada para o município do lead, `cnpj` e `razaoSocial` simplesmente ficam `null` — não é erro.

Detalhes completos em [`tools/cnpj/README.md`](tools/cnpj/README.md).

---

## Dataset de IDHM (offline)

O município, a UF e o IDHM vêm de `src/main/resources/geo/municipios-idhm.json` (5.570 municípios), carregado na inicialização e validado por tamanho/checksum. **Não há download em runtime.**

Só é preciso regenerar se quiser atualizar os dados:

```bash
python3 tools/idhm/gerar_dataset.py
```

Detalhes em [`tools/idhm/README.md`](tools/idhm/README.md).

---

## Rotas da API (resumo)

Base local: `http://localhost:8080/api`. Sem autenticação no estágio atual. O contrato completo, com payloads e códigos de erro, está em [`API.md`](API.md).

| Método | Rota | Para quê |
| --- | --- | --- |
| `GET` | `/api/health` | Health check simples |
| `POST` | `/api/buscas` | Executa e persiste uma busca |
| `GET` | `/api/buscas` | Lista o histórico de buscas |
| `GET` | `/api/buscas/{id}` | Detalhe de uma busca anterior |
| `POST` | `/api/buscas/{id}/cnpj` | Reprocessa o CNPJ dos leads da busca |
| `POST` | `/api/buscas/{id}/informacoes` | Dispara a pesquisa de site/Instagram (`202`) |
| `GET` | `/api/buscas/{id}/informacoes` | Estado/progresso da pesquisa |
| `GET` | `/api/leads` | Lista e filtra leads |
| `GET` | `/api/leads/pagina` | Pagina uma coluna do Kanban |
| `GET` | `/api/leads/{id}` | Consulta um lead |
| `PATCH` | `/api/leads/{id}` | Atualiza status, observações e/ou último contato |
| `GET` | `/api/bloqueios` | Lista nomes bloqueados |
| `POST` | `/api/bloqueios` | Cadastra um nome bloqueado |
| `DELETE` | `/api/bloqueios/{id}` | Remove um nome bloqueado |
| `GET` | `/api/geografia/municipios` | Municípios/IDHM em GeoJSON por `bbox` |
| `GET` | `/api/exportacao/leads.csv` | Exporta os leads em CSV |
| `GET` | `/api/exportacao/leads.xlsx` | Exporta os leads em Excel |

Exemplo de busca:

```bash
curl -X POST http://localhost:8080/api/buscas \
  -H "Content-Type: application/json" \
  -d '{
    "enderecoBase": "Centro, Curitiba - PR",
    "latitude": -25.4284,
    "longitude": -49.2733,
    "raioKm": 5,
    "categorias": ["PADARIA", "MERCADO", "RESTAURANTE"]
  }'
```

Valores de enum aceitos:

- `CategoriaNegocio`: `MERCADO`, `PADARIA`, `DOCERIA`, `RESTAURANTE`, `DISTRIBUIDORA`, `ACOUGUE`, `FARMACIA`, `OUTROS`
- `StatusFunil`: `NOVO`, `QUALIFICADO`, `CONTATADO`, `GANHO`, `PERDIDO`
- `Temperatura`: `QUENTE`, `MORNO`, `FRIO`

---

## Testes

Backend:

```bash
./mvnw test
./mvnw package
```

Frontend:

```bash
cd frontend
npm test
npm run build
npm run e2e:smoke      # smoke E2E com API simulada
```

> O ambiente local validou o backend Java 25 precisando de um agente Byte Buddy em runtime para compatibilidade do Mockito com a JVM. Em JVMs compatíveis, `./mvnw test` roda normalmente. Os testes automatizados não abrem o WhatsApp nem consomem cota da Google.

---

## Estrutura do projeto

```text
leads-hunter/
├── src/main/java/dev/jlm/leadshunter/
│   ├── busca/               busca, cache, histórico e vínculo Busca-Lead
│   ├── bloqueio/            blacklist de nomes
│   ├── cnpj/                correspondência local de CNPJ
│   ├── config/              health check e tratamento centralizado de erros
│   ├── exportacao/          CSV e Excel
│   ├── geo/                 dataset municipal, IDHM e GeoJSON
│   ├── integracao/places/   cliente, contratos e rate limit da Google
│   ├── integracao/pesquisa/ pesquisa de site/Instagram (Brave)
│   ├── lead/                lead, telefone e WhatsApp manual
│   └── scoring/             score e temperatura
├── src/main/resources/
│   ├── application.yml
│   ├── db/migration/        migrations Flyway (V1..V6 + R__)
│   └── geo/                 dataset IDHM offline
├── frontend/                SPA Angular
├── tools/cnpj/              ingestor da base pública do CNPJ
├── tools/idhm/              gerador do dataset de IDHM
├── API.md                   contrato da API REST
├── tecnologias.md           raio-X tecnológico
├── fluxo.md                 estado atual do projeto
└── HISTORICO_IMPLEMENTACOES.md
```

---

## Escopo e limitações (por decisão do projeto)

- Aplicação **local e single-user**; sem autenticação, usuários, roles ou deploy.
- **WhatsApp apenas como link manual** `https://wa.me/55...`; sem disparo automático ou em massa.
- A pesquisa usa a **API oficial do Brave**. A abertura da URL candidata (site próprio ou perfil público do Instagram) serve apenas para validar telefone/endereço/CNPJ do lead, com teto de **3 consultas ao Brave e 3 páginas por lead**, sem seguir redirecionamentos e sem acessar destinos privados; o scraping de motores de busca permanece desativado.
- Sem Docker/Compose, CI/CD, Redis, RabbitMQ/Kafka ou cache distribuído.
- Cache e rate limit são **em memória** e reiniciam com a aplicação.
- O CNPJ não entra no cálculo do score.
- A blacklist não remove retroativamente leads já cadastrados.
- A base de CNPJ **não vem carregada**; é preciso injetá-la (veja a seção acima).
