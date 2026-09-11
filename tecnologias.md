# Raio-X tecnológico do Leads Hunter

Levantamento do estado real do repositório em **10 de setembro de 2026**. Este documento descreve as tecnologias, a arquitetura, as funcionalidades, a execução local, as integrações e as limitações que existem hoje no projeto.

## Resumo executivo

O Leads Hunter é uma aplicação web local e single-user para prospecção de estabelecimentos comerciais. O sistema consulta a Google Places API, deduplica e persiste os estabelecimentos como leads, enriquece os dados com município, IDHM e CNPJ, calcula score comercial, organiza os leads em um Kanban e permite exportá-los em CSV ou Excel.

| Camada | Tecnologia principal | Estado atual |
| --- | --- | --- |
| Backend | Java 25 + Spring Boot 4.1.0 | Implementado |
| Frontend | Angular 22.1.4 + TypeScript 6.0.3 | Implementado |
| Banco | MySQL + Flyway | Implementado para execução local |
| Integração externa | Google Places API (New) | Implementada no backend |
| Mapa | Leaflet 1.9.4 + OpenStreetMap | Implementado |
| Cache | Caffeine em memória | Implementado |
| Rate limiting | Bucket4j 8.10.1 em memória | Implementado para chamadas à Google |
| Exportação | CSV próprio + Apache POI 5.5.1 para XLSX | Implementado |
| Infraestrutura | Processos locais, sem containers e sem deploy | Somente ambiente local |

## O que o programa faz

- Busca estabelecimentos por coordenadas, raio de até 20 km e categorias comerciais.
- Consulta a Google Places API (New) pelo backend e nunca expõe a integração diretamente ao navegador.
- Deduplica leads por `googlePlaceId`.
- Relaciona buscas e leads em N:N por meio de `BuscaLead`, preservando o retrato de score e temperatura de cada execução.
- Mantém o histórico de pesquisas e permite abrir seus detalhes.
- Persiste telefone, endereço formatado, endereço estruturado, avaliação, quantidade de reviews e situação operacional.
- Normaliza telefones brasileiros e gera somente um link manual `https://wa.me/55...`.
- Calcula score de 0 a 95 e classifica os leads como `FRIO`, `MORNO` ou `QUENTE`.
- Organiza o funil nos status `NOVO`, `QUALIFICADO`, `CONTATADO`, `GANHO` e `PERDIDO`.
- Permite movimentar leads no Kanban por drag-and-drop ou controles de teclado.
- Permite editar observações e último contato sem sobrescrever os demais dados.
- Filtra leads por status, categoria e temperatura.
- Pagina cada coluna do Kanban de forma independente, com até 25 registros por página.
- Exporta os leads filtrados em CSV UTF-8 e Excel `.xlsx`.
- Mantém uma blacklist persistente de nomes e ignora estabelecimentos cujo nome contenha um termo bloqueado normalizado.
- Localiza município, UF e IDHM por um dataset geográfico offline com 5.570 municípios.
- Exibe no mapa uma camada coroplética opcional de IDHM, servida como GeoJSON pelo backend.
- Faz correspondência local de CNPJ por município, endereço e nome, com limiar de confiança e rejeição de ambiguidades.
- Permite tentar novamente o enriquecimento de CNPJ dos leads de uma busca histórica.
- Oferece um endpoint próprio de health check.

## Backend

### Plataforma e build

| Item | Versão/configuração | Uso no projeto |
| --- | --- | --- |
| Java | 25 LTS; ambiente local em Amazon Corretto 25.0.4 | Linguagem e runtime da API |
| Spring Boot | 4.1.0 | Base da aplicação e gerenciamento de dependências |
| Maven Wrapper | Maven 3.9.16 no ambiente inspecionado | Build, testes e empacotamento |
| Artefato | `dev.jlm:leads-hunter:0.0.1-SNAPSHOT` | JAR executável Spring Boot |
| Pacote raiz | `dev.jlm.leadshunter` | Organização do código Java |
| Servidor | Tomcat embarcado, porta 8080 | Servidor HTTP fornecido pelo Spring Web |

Não foi encontrada configuração ativa de virtual threads. Embora essa tecnologia apareça como possibilidade na documentação inicial, o código e o `application.yml` atuais não a habilitam.

### Frameworks e bibliotecas

| Tecnologia | Uso |
| --- | --- |
| Spring Web MVC | Controllers REST, contratos HTTP e servidor web |
| Spring `RestClient` | Comunicação HTTP síncrona com a Google Places API |
| Spring Data JPA | Repositories, paginação, Query by Example e Specifications |
| Hibernate/JPA | Mapeamento e persistência das entidades |
| Jakarta Validation | Validação de DTOs, parâmetros, limites e payloads |
| Flyway + `flyway-mysql` | Criação, evolução e validação do schema |
| MySQL Connector/J | Driver JDBC de runtime |
| Caffeine | Cache local das respostas recentes da Google Places |
| Bucket4j 8.10.1 | Limite local de chamadas externas |
| Apache POI 5.5.1 | Geração de arquivos Excel `.xlsx` |
| Jackson | Serialização JSON e leitura do dataset geográfico |
| Lombok | Geração seletiva de getters, setters, construtores e logger |
| Spring Boot DevTools | Recarga e apoio ao desenvolvimento local |

### Organização do backend

```text
src/main/java/dev/jlm/leadshunter/
├── busca/                 busca, cache, histórico e vínculo Busca-Lead
├── bloqueio/              blacklist persistente de nomes
├── cnpj/                  dados e correspondência local de CNPJ
├── config/                health check e tratamento centralizado de erros
├── exportacao/            geração de CSV e Excel
├── geo/                   dataset municipal, IDHM, GeoJSON e backfill
├── integracao/places/     cliente, contratos e rate limit da Google Places
├── lead/                  gestão do lead, telefone e WhatsApp manual
└── scoring/               cálculo de score e temperatura
```

A arquitetura segue o fluxo Controller → Service → Repository/integração. Entidades JPA não são devolvidas diretamente: a API usa DTOs e records próprios. O tratamento de exceções é centralizado por `@RestControllerAdvice` e devolve um contrato JSON uniforme, sem stack trace ou detalhes internos.

### API REST disponível

| Método e rota | Finalidade |
| --- | --- |
| `GET /api/health` | Health check simples da aplicação |
| `POST /api/buscas` | Executar e persistir uma busca |
| `GET /api/buscas` | Listar o histórico |
| `GET /api/buscas/{id}` | Consultar uma busca anterior |
| `POST /api/buscas/{id}/cnpj` | Reprocessar CNPJ dos leads ainda sem correspondência |
| `GET /api/leads` | Listar e filtrar leads |
| `GET /api/leads/pagina` | Paginar uma etapa do Kanban |
| `GET /api/leads/{id}` | Consultar um lead |
| `PATCH /api/leads/{id}` | Atualizar status, observações e/ou último contato |
| `GET /api/bloqueios` | Listar termos bloqueados |
| `POST /api/bloqueios` | Cadastrar um termo bloqueado |
| `DELETE /api/bloqueios/{id}` | Remover um termo bloqueado |
| `GET /api/geografia/municipios` | Obter municípios/IDHM em GeoJSON por bounding box |
| `GET /api/exportacao/leads.csv` | Exportar leads em CSV |
| `GET /api/exportacao/leads.xlsx` | Exportar leads em Excel |

## Banco de dados e persistência

| Item | Situação atual |
| --- | --- |
| SGBD | MySQL local |
| Schema padrão | `leadsradar` |
| ORM | Hibernate/JPA via Spring Data JPA |
| Evolução de schema | Flyway |
| Estratégia Hibernate | `ddl-auto: validate`; não cria nem altera schema automaticamente |
| Open Session in View | Desabilitado (`open-in-view: false`) |
| Engine/charset das migrations | InnoDB e `utf8mb4` |

### Estrutura persistida

- `busca`: parâmetros, totais e data de cada pesquisa.
- `leads`: estabelecimento deduplicado, classificação, dados comerciais, geografia e CNPJ.
- `busca_lead`: associação N:N com score, temperatura e instante daquela busca.
- `nome_bloqueado`: termos da blacklist e sua forma normalizada única.
- `cnpj_empresa`: CNPJ básico, razão social e competência da base.
- `cnpj_estabelecimento`: unidade de 14 dígitos, endereço, município, situação cadastral e competência.

Existem migrations versionadas de `V1` a `V5` e uma migration repetível para a carga CNPJ. A migration repetível versionada é apenas um placeholder seguro; a carga mensal volumosa real é gerada localmente e não deve ser commitada.

## Frontend

### Plataforma e bibliotecas

As versões abaixo são as resolvidas atualmente pelo `package-lock.json`.

| Tecnologia | Versão | Uso |
| --- | --- | --- |
| Angular | 22.1.4 | SPA e componentes standalone |
| Angular CLI | 22.1.6 | Desenvolvimento e build |
| Angular Build | 22.1.6 | Pipeline de compilação |
| Angular CDK | 22.1.4 | Drag-and-drop do Kanban |
| TypeScript | 6.0.3 | Código tipado em modo estrito |
| RxJS | 7.8.2 | Fluxos assíncronos, HTTP e cancelamento |
| Leaflet | 1.9.4 | Mapa, marcador, raio e camada municipal |
| SCSS | Configurado pelo Angular | Estilos globais e por componente |
| Angular Signals | Recurso do framework | Estado local e valores derivados |
| Signal Forms | Recurso usado nas telas de formulário | Formulários tipados e reativos |

O frontend é uma SPA sem SSR, sem biblioteca global de estado e sem framework visual externo. Usa componentes standalone, lazy loading por rota, `provideHttpClient`, `provideRouter`, templates estritos e TypeScript estrito.

### Telas e rotas

| Rota | Conteúdo |
| --- | --- |
| `/busca` | Formulário, mapa Leaflet, raio, camada IDHM e resultados |
| `/kanban` | Cinco etapas do funil, filtros, paginação, detalhe e exportação |
| `/historico` | Lista das buscas realizadas |
| `/historico/:id` | Detalhe e leads de uma busca anterior, inclusive reprocessamento de CNPJ |
| `/bloqueios` | Cadastro, listagem e remoção de nomes bloqueados |
| `**` | Página de rota não encontrada |

### Experiência e acessibilidade implementadas

- Layout responsivo para desktop e mobile.
- Navegação lateral e áreas de trabalho independentes.
- Temas `Sistema`, `Claro` e `Escuro`, com Signals, persistência em `localStorage`, acompanhamento de `prefers-color-scheme` e aplicação antecipada no bootstrap.
- Paleta escura por tokens semânticos nas superfícies da aplicação; o tratamento interno do Leaflet permanece planejado na DM-01.6.
- Kanban com rolagem horizontal localizada e rolagem vertical por coluna.
- Drag-and-drop com atualização otimista, confirmação da API e rollback em erro.
- Alternativa por teclado para mover cards entre etapas.
- Drawer de detalhes com foco preso, fechamento por `Escape` e retorno do foco ao gatilho.
- Mensagens distintas de carregamento, sucesso, vazio e erro.
- Camada de IDHM opcional com legenda e popups acessíveis.
- Download de blobs com nomes obtidos de forma segura dos headers HTTP.
- WhatsApp aberto manualmente em nova aba somente quando a API fornece uma URL válida.

## Integrações e dados externos

### Google Places API (New)

- Endpoint utilizado: Nearby Search `places:searchNearby`.
- A chamada parte exclusivamente do backend.
- Busca por popularidade e retorna no máximo 20 estabelecimentos por consulta externa.
- Usa Field Mask para solicitar somente os campos necessários.
- Traduz categorias internas para tipos aceitos pela Google.
- Trata separadamente falta de configuração, autorização, cota, rate limit, consulta rejeitada, indisponibilidade e resposta inválida.
- O cache evita chamadas repetidas para coordenadas, raio e categorias equivalentes.
- O rate limiter protege somente as chamadas externas reais; cache hit não consome permissão.

### Dados geográficos e IDHM

- O runtime usa o arquivo offline `src/main/resources/geo/municipios-idhm.json`.
- O dataset contém 5.570 municípios, limites geográficos e Polygon/MultiPolygon simplificados.
- A carga valida tamanho, metadados e checksum antes de permanecer em memória.
- A localização usa bounding boxes e point-in-polygon.
- Não há download geográfico em runtime.
- Os scripts reproduzíveis de geração ficam em `tools/idhm/` e usam Python.

### Base de CNPJ

- A correspondência acontece localmente; não existe consulta à Receita Federal em runtime.
- Os scripts em `tools/cnpj/` processam os arquivos mensais oficiais em streaming.
- O pipeline usa Python, manifesto de fontes, HTTPS, checksums SHA-256, filtros por UF/município e saída SQL determinística.
- A aplicação rejeita correspondências ambíguas, de baixa confiança ou com candidatos demais.
- Competência e confiança são persistidas para permitir revalidação futura.

### OpenStreetMap

O Leaflet usa tiles do OpenStreetMap no navegador, com atribuição. Portanto, embora o dataset municipal seja offline, a camada base do mapa depende de rede durante o uso normal, salvo se os tiles já estiverem em cache no navegador.

## Cache, limites e desempenho

- Cache Caffeine local, padrão de 30 minutos e no máximo 100 buscas.
- Chave de cache por latitude/longitude arredondadas, raio e categorias normalizadas.
- Bucket4j local, padrão de 10 requisições repostas ao longo de 60 segundos.
- Resultados do cache não impedem a criação de um novo histórico de busca.
- Endpoint geográfico limitado a 1.500 municípios por resposta.
- Paginação do Kanban limitada a 25 leads por página.
- Correspondência CNPJ limitada a 200 candidatos.
- Cache e rate limiter ficam somente na memória do processo e são reiniciados com a aplicação.

## Testes e qualidade

### Backend

| Ferramenta | Origem/versão | Uso |
| --- | --- | --- |
| Spring Boot Test | Gerenciada pelo Spring Boot 4.1.0 | Base dos testes e da integração com o contexto Spring |
| JUnit Jupiter/JUnit 5 | Gerenciada pelo Spring Boot | Testes unitários, de integração e parametrizados |
| Mockito | Gerenciada pelo Spring Boot | Mocks, capturas de argumentos e unidades isoladas |
| MockMvc | Spring Test | Contratos HTTP de controllers e tratamento de erros |
| MockRestServiceServer | Spring Test | Simulação das respostas HTTP da Google Places |
| Spring/JPA/Flyway/MySQL | Integração local | Validação de repositories, migrations, transações e schema |
| Apache POI | 5.5.1 | Leitura e validação das planilhas Excel produzidas |

Foram encontrados 31 arquivos de classes de teste Java no inventário atual.

### Frontend

| Ferramenta | Versão resolvida | Uso |
| --- | --- | --- |
| Vitest | 4.1.11 | Testes unitários e de componentes |
| jsdom | 28.1.0 | DOM simulado para testes |
| Playwright | 1.62.1 | Smoke E2E em navegador |
| axe-core para Playwright | 4.13.0 | Auditoria automatizada de acessibilidade |
| Prettier | 3.9.6 | Formatação do código frontend |

Foram encontrados 27 arquivos `*.spec.ts`, além do smoke E2E `frontend/scripts/mvp-flow-smoke.mjs` e de um script de inspeção visual.

### Ferramentas de dados

- Testes Python com `unittest` para os geradores de IDHM e CNPJ.
- Validação de checksum, parsing, normalização, determinismo, limites e filtros.

O estado persistido em `fluxo.md` registra 201 testes frontend aprovados na última validação do refinamento CNPJ-07. Na última execução completa documentada do backend, 161 testes foram executados: 155 passaram e seis terminaram com falha/erro por conflito entre fixtures antigas e a carga CNPJ real já presente no MySQL local. Os 41 testes diretamente ligados à última entrega passaram. Esses números são históricos documentados; as suítes não foram reexecutadas para produzir este inventário.

## Infraestrutura e execução

### Topologia atual

```text
Navegador
   |
   | http://localhost:4200
   v
Angular Development Server
   |
   | proxy /api -> http://localhost:8080
   v
Spring Boot / Tomcat
   |
   +--> MySQL local em localhost:3306
   +--> Google Places API por HTTPS
   +--> dataset IDHM e base CNPJ locais
```

### Ambiente local inspecionado

| Componente | Versão/porta |
| --- | --- |
| Sistema | Linux amd64 |
| Java | Amazon Corretto 25.0.4 LTS |
| Maven | 3.9.16 pelo Maven Wrapper |
| Node.js | 26.7.0 |
| npm | 12.0.2 |
| Backend | porta 8080 |
| Frontend de desenvolvimento | porta 4200 |
| MySQL | porta padrão 3306 |

O `package.json` fixa o gerenciador em npm 12.0.2. As dependências Angular 22.1.x exigem uma versão compatível de Node; o Node 26.7.0 instalado atende ao intervalo declarado no lockfile.

### Comandos disponíveis

Backend:

```bash
./mvnw spring-boot:run
./mvnw test
./mvnw package
```

Frontend:

```bash
cd frontend
npm install
npm start
npm test
npm run build
npm run e2e:smoke
```

### O que não existe na infraestrutura atual

- Dockerfile.
- Docker Compose.
- Pipeline de CI/CD no repositório.
- Deploy, VPS, Kubernetes ou configuração de nuvem.
- Reverse proxy e HTTPS próprios do projeto.
- Perfis separados de desenvolvimento, teste e produção.
- Secret manager.
- Redis.
- RabbitMQ, Kafka ou outro message broker.
- Observabilidade dedicada, métricas ou tracing.
- Spring Boot Actuator; o health check é um controller próprio.
- Automação de SAST, SCA, SBOM ou secret scanning.

## Segurança e limites do escopo atual

- A aplicação é local e single-user.
- Não há autenticação, autorização, usuários, roles, JWT, sessão ou recuperação de senha.
- Não há Spring Security no `pom.xml`.
- Não há liberação CORS customizada; o desenvolvimento usa o proxy do Angular.
- Entradas externas usam DTOs e Jakarta Validation.
- A API não expõe entidades JPA diretamente.
- O backend centraliza erros e não deve devolver stack trace, corpo bruto da Google ou credenciais.
- A chave da Google deve permanecer somente no backend e deveria vir de `GOOGLE_PLACES_API_KEY`.
- O WhatsApp é apenas link manual; não há disparo automático, em massa ou automação do WhatsApp Web.
- Cache e rate limit atuais não são proteção contra DDoS e não funcionam de forma distribuída.

### Atenção: configuração sensível no worktree

O `application.yml` presente no worktree no momento deste levantamento contém uma credencial privilegiada do MySQL e uma chave da Google escritas diretamente no arquivo. Os valores não são reproduzidos aqui. Esse estado diverge das regras do projeto e da configuração versionada anterior, que usava variáveis de ambiente. Além do risco de commit acidental, o usuário administrativo do banco concede privilégios maiores que os necessários à aplicação.

Para manter o modelo local com segurança, a configuração deve voltar a usar `DB_USERNAME`, `DB_PASSWORD` e `GOOGLE_PLACES_API_KEY`, com um usuário MySQL exclusivo e limitado ao schema da aplicação. Se a chave atual tiver sido compartilhada ou commitada em algum lugar, ela deve ser rotacionada e restringida no Google Cloud.

## Funcionalidades propositalmente ausentes

- Autenticação e multiusuário.
- Deploy remoto ou uso em produção pública.
- Scraping ou integração com Instagram.
- Envio automático ou em massa de WhatsApp.
- Filas e mensageria.
- Cache distribuído.
- Uso de IDHM ou CNPJ no cálculo do score.
- Limpeza retroativa dos leads que já existiam antes de um nome entrar na blacklist.

## Estado atual e pendências conhecidas

- O MVP, o frontend FE-00 a FE-17, a melhoria FE-100, IDHM, blacklist e CNPJ-00 a CNPJ-07 estão documentados como concluídos em seus respectivos escopos.
- O dark mode está concluído da DM-01.1 à DM-01.5; a DM-01.6 ainda precisa adaptar os elementos internos do Leaflet e fechar o smoke E2E da sprint.
- A migration repetível de CNPJ no Git continua vazia por desenho; é preciso gerar e revisar uma carga mensal para uso com dados reais.
- A suíte completa do backend precisa ter suas fixtures isoladas da carga CNPJ local para voltar a passar integralmente em uma máquina com a base populada.
- A configuração sensível local precisa voltar a variáveis de ambiente antes de qualquer commit ou compartilhamento.
- Não existe infraestrutura de produção; o sistema deve ser tratado como ferramenta local e projeto de portfólio.

## Fontes deste levantamento

- `pom.xml` e dependências Maven.
- `frontend/package.json`, `frontend/package-lock.json`, `angular.json` e `tsconfig.json`.
- Código-fonte backend e frontend.
- Migrations Flyway em `src/main/resources/db/migration/`.
- `application.yml` e proxy de desenvolvimento Angular.
- Scripts e documentação de `tools/idhm/` e `tools/cnpj/`.
- `fluxo.md`, `API.md` e `SECURITY_REVIEW.md` confrontados com o código atual.
