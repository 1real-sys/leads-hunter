# Tecnologias e escolhas técnicas

O Leads Hunter é uma aplicação local de prospecção com frontend Angular, API Spring Boot e MySQL. Este documento registra **a stack usada e o motivo das escolhas**. O funcionamento interno está em [fluxo.md](fluxo.md), os contratos HTTP em [API.md](API.md) e as instruções de execução no [README.md](README.md). As versões vêm de `pom.xml` e `frontend/package-lock.json`.

## Visão da arquitetura

```text
Angular → API Spring Boot → MySQL
                 ├── Google Places API (busca de estabelecimentos)
                 ├── Brave Search API (pesquisa pública opcional)
                 ├── dataset municipal/IDHM no classpath
                 └── base local de CNPJ no MySQL
```

A aplicação foi desenhada para **uso local e uma única instância**. Por isso, cache e limites de chamadas ficam em memória, enquanto buscas, leads e execuções de enriquecimento ficam no banco. O navegador não recebe chaves de APIs externas. Não há autenticação nem infraestrutura de deploy no escopo atual.

## Backend e persistência

| Tecnologia | Versão ou configuração | Papel no projeto |
| --- | --- | --- |
| Java | 25 | Linguagem e runtime do backend. |
| Spring Boot | 4.1.0 | Inicialização, configuração, servidor Tomcat embarcado e gerenciamento das dependências Spring. |
| Spring Web MVC | Gerenciada pelo Boot | Controllers REST e tratamento HTTP. |
| Spring Data JPA e Hibernate | Gerenciados pelo Boot | Repositories, paginação e persistência das entidades. |
| Jakarta Validation | Gerenciada pelo Boot | Validação dos DTOs e parâmetros recebidos. |
| MySQL e Connector/J | MySQL 8.x; driver gerenciado pelo Boot | Dados operacionais, histórico e recorte local de CNPJ. |
| Flyway e `flyway-mysql` | Gerenciados pelo Boot | Migrations versionadas `V1` a `V12` e carga repetível de CNPJ. |
| Jackson | Gerenciado pelo Boot | JSON dos contratos HTTP, da Brave e do dataset geográfico. |
| Lombok | Gerenciado pelo Boot | Reduz código repetitivo em entidades e serviços selecionados. |
| Maven Wrapper | 3.9.16, fixado em `.mvn/wrapper/` | Build reproduzível sem depender de Maven global. |

O Flyway controla o schema; Hibernate usa `ddl-auto: validate`. A relação entre `Busca` e `Lead` é N:N por `BuscaLead`, que também guarda o score da execução. A API devolve DTOs em vez de entidades JPA. O código Java é organizado por funcionalidade em `src/main/java/dev/jlm/leadshunter/`.

| Tecnologia | Versão | Por que está aqui |
| --- | --- | --- |
| Caffeine | Gerenciada pelo Boot | Reutiliza respostas recentes da Google sem impedir um novo registro de busca. |
| Bucket4j | 8.10.1 | Limita chamadas reais à Google Places; cache hit não consome permissão. |
| Apache POI | 5.5.1 | Gera XLSX com células tipadas; CSV é produzido pelo próprio backend. |

Caffeine e Bucket4j mantêm estado apenas no processo. Uma reinicialização limpa o cache e reinicia o limite local; essa escolha acompanha a execução em uma instância, sem Redis ou mensageria.

## Frontend

As versões abaixo são as resolvidas em `frontend/package-lock.json`.

| Tecnologia | Versão | Papel no projeto |
| --- | --- | --- |
| Angular e Angular CDK | 22.1.4 | SPA com componentes standalone; o CDK fornece drag-and-drop do Kanban. |
| Angular CLI e Build | 22.1.6 | Servidor de desenvolvimento e build. |
| TypeScript | 6.0.3 | Código e contratos HTTP tipados em modo estrito. |
| Angular Signals e Signal Forms | Incluídos no Angular | Estado local, valores derivados e formulário da Busca. |
| RxJS | 7.8.2 | Requisições HTTP, cancelamento e acompanhamento das ações assíncronas. |
| Leaflet | 1.9.4 | Mapa, seleção de coordenadas e camada opcional de IDHM. |
| SCSS | Suportado pelo Angular | Estilos globais, temas e estilos dos componentes. |

O frontend usa rotas carregadas sob demanda e serviços em `frontend/src/app/core/api/` para falar com o backend. O estado permanece próximo das telas; não há biblioteca global de estado. Os tiles básicos do mapa vêm do OpenStreetMap e dependem de rede durante o uso normal. A camada municipal/IDHM vem do backend, a partir de dados locais.

## Integrações e dados

| Tecnologia ou fonte | Uso e limite da escolha |
| --- | --- |
| Google Places API (New) + Spring `RestClient` | Busca estabelecimentos pelo backend. A chave fica no ambiente do servidor; cache e rate limit reduzem chamadas repetidas. |
| Brave Search API + `java.net.http.HttpClient` | Descobre candidatos a site próprio e perfil público do Instagram quando o usuário aciona **Buscar informações** e permite Brave. Não há integração com a API oficial do Instagram. |
| `java.net.http.HttpClient` + jsoup 1.23.2 | Lê páginas públicas elegíveis com limites de host, tamanho, tempo e redirecionamento; extrai texto e links para confirmar identidade e, na ação própria, e-mail do site oficial. |
| Dataset municipal/IDHM | Arquivo incluído no classpath; localiza município/IDHM e fornece GeoJSON sem download geográfico em runtime. |
| Base pública de CNPJ | Recorte mensal gerado offline por scripts Python em `tools/cnpj/` e carregado no MySQL. A aplicação não consulta a Receita em runtime. |
| OpenStreetMap | Fornece os tiles da camada base do mapa no navegador; não é a fonte do IDHM. |

Playwright Java 1.62.0 continua no `pom.xml` para o código legado de scraping de buscadores, **desativado por padrão**. A pesquisa normal usa a API oficial do Brave e pode abrir apenas páginas candidatas permitidas. Os limites e verificações dessa leitura estão descritos em [fluxo.md](fluxo.md).

## Testes e ferramentas de desenvolvimento

| Ferramenta | Uso |
| --- | --- |
| JUnit, Mockito e Spring Test/MockMvc | Testes unitários, de serviços e dos contratos HTTP do backend. |
| MySQL temporário, JPA e Flyway | Testes de integração com schema isolado da base da aplicação. |
| Vitest 4.1.11 e jsdom 28.1.0 | Testes de componentes e lógica do Angular. |
| Playwright 1.62.1 e `@axe-core/playwright` 4.13.0 | Smokes no navegador e auditorias automatizadas de acessibilidade. |
| Python `unittest` | Testes dos scripts de preparação dos dados de CNPJ e IDHM. |
| Spring Boot DevTools e Prettier | Apoio ao desenvolvimento local e formatação do frontend. |

As credenciais do MySQL e as chaves externas são lidas de variáveis de ambiente pelo `application.yml`. O `.env` local é ignorado pelo Git e precisa ser carregado antes de iniciar o backend; o [README.md](README.md) mostra os passos. Resultados e quantidade de testes não são fixados aqui, pois mudam conforme o código evolui.
