# Histórico de Implementações

Este documento resume a evolução do Leads Hunter em ordem cronológica. As etapas foram reconstruídas a partir do histórico Git e conferidas no código atual, com apoio das regras de `v1.md` e da descrição de fluxo em `fluxo.md`. Alterações locais sem commit e artefatos de build não são tratados como etapas históricas.

## 1. Inicialização do projeto Spring Boot — 04/08/2026

Foi criada a base executável do backend com Maven Wrapper, Java 25, Spring Boot e um teste inicial de carregamento do contexto. Essa etapa estabeleceu a estrutura mínima para executar, testar e evoluir a aplicação de forma reproduzível.

### Arquivos envolvidos

**Criados:**

- `.gitattributes`
- `.gitignore`
- `.mvn/wrapper/maven-wrapper.properties`
- `mvnw`
- `mvnw.cmd`
- `pom.xml`
- `src/main/java/dev/jlm/leadshunter/LeadsHunterApplication.java`
- `src/main/resources/application.properties`
- `src/test/java/dev/jlm/leadshunter/LeadsHunterApplicationTests.java`

---

## 2. Estrutura arquitetural, domínio e banco de dados do MVP — 05/08/2026

Foi montado o esqueleto do backend organizado por funcionalidades, com as entidades `Busca`, `Lead` e `BuscaLead`, enums do domínio, repositories e pontos iniciais para controllers e services. O relacionamento entre buscas e leads nasceu como N:N por meio de `BuscaLead`, e a unicidade de um estabelecimento foi definida pelo `googlePlaceId`.

Também foi criada a migration inicial do Flyway para MySQL, com tabelas, relacionamentos, índices e restrições. A configuração passou para YAML, mantendo o Flyway como responsável pelo schema e o Hibernate em modo de validação. O endpoint de saúde e as diretrizes de arquitetura e desenvolvimento completaram a fundação do MVP.

### Arquivos envolvidos

**Criados:**

- `.opencode/skills/leadradar-architecture/SKILL.md`
- `.opencode/skills/leadradar-backend/SKILL.md`
- `.opencode/skills/leadradar-database/SKILL.md`
- `.opencode/skills/leadradar-frontend/SKILL.md`
- `.opencode/skills/leadradar-overview/SKILL.md`
- `.opencode/skills/leadradar-roadmap/SKILL.md`
- `.opencode/skills/leadradar-testes/SKILL.md`
- `v1.md`
- `src/main/java/dev/jlm/leadshunter/busca/Busca.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaController.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaLead.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaLeadRepository.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaRepository.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaService.java`
- `src/main/java/dev/jlm/leadshunter/config/HealthController.java`
- `src/main/java/dev/jlm/leadshunter/exportacao/ExportService.java`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesApiClient.java`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesResponseMapper.java`
- `src/main/java/dev/jlm/leadshunter/lead/CategoriaNegocio.java`
- `src/main/java/dev/jlm/leadshunter/lead/Lead.java`
- `src/main/java/dev/jlm/leadshunter/lead/LeadController.java`
- `src/main/java/dev/jlm/leadshunter/lead/LeadRepository.java`
- `src/main/java/dev/jlm/leadshunter/lead/LeadService.java`
- `src/main/java/dev/jlm/leadshunter/lead/StatusFunil.java`
- `src/main/java/dev/jlm/leadshunter/lead/Temperatura.java`
- `src/main/java/dev/jlm/leadshunter/scoring/ScoringService.java`
- `src/main/resources/application.yml`
- `src/main/resources/db/migration/V1__criar_tabelas.sql`

**Modificados:**

- `pom.xml`

**Arquivos removidos:**

- `src/main/resources/application.properties`

---

## 3. Fluxo inicial de criação de buscas — 07/08/2026

Foi implementado o primeiro fluxo funcional de `POST /api/buscas`. A API passou a validar endereço, coordenadas, raio e categorias, persistir o resumo da busca e devolver uma resposta própria com identificador e data de criação.

Nesta etapa também foram alinhados o nome da tabela de leads e a configuração do banco. Isso preparou o endpoint para receber, nas etapas seguintes, os estabelecimentos retornados pela integração externa.

### Arquivos envolvidos

**Criados:**

- `src/main/java/dev/jlm/leadshunter/busca/BuscaRequest.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaResponse.java`

**Modificados:**

- `.opencode/skills/leadradar-database/SKILL.md`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaController.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaService.java`
- `src/main/java/dev/jlm/leadshunter/lead/Lead.java`
- `src/main/resources/application.yml`
- `src/main/resources/db/migration/V1__criar_tabelas.sql`

---

## 4. Integração das buscas com a Google Places API — 11/08/2026

O fluxo de busca foi conectado ao Nearby Search da Google Places API. Foi criado um contrato interno para a consulta e outro para os resultados, mantendo o formato externo isolado do restante da aplicação.

O cliente passou a converter categorias do projeto em tipos aceitos pela Google, limitar e ordenar os resultados e solicitar somente os campos necessários. A resposta externa passou a ser transformada em dados do domínio, e a chave da API ficou configurável por variável de ambiente. Testes foram adicionados para o serviço de busca e para o mapeamento da resposta.

### Arquivos envolvidos

**Criados:**

- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesSearchRequest.java`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesSearchResponse.java`
- `src/test/java/dev/jlm/leadshunter/busca/BuscaServiceTest.java`
- `src/test/java/dev/jlm/leadshunter/integracao/places/PlacesResponseMapperTest.java`

**Modificados:**

- `src/main/java/dev/jlm/leadshunter/busca/BuscaService.java`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesApiClient.java`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesResponseMapper.java`
- `src/main/resources/application.yml`

---

## 5. Persistência, deduplicação, enriquecimento e scoring dos leads — 12/08/2026

Os estabelecimentos encontrados passaram a ser persistidos como leads e relacionados à busca por `BuscaLead`. Resultados repetidos e leads já conhecidos passaram a ser identificados pelo `googlePlaceId`, evitando duplicações. Quando um lead reaparece, seus dados externos são atualizados sem perder `status`, `observacoes` ou `ultimoContatoEm`.

A integração passou a obter telefone, situação operacional, avaliação e quantidade de reviews. Telefones brasileiros válidos passaram a ser normalizados, e o scoring começou a classificar cada lead como frio, morno ou quente. O score atual fica no lead e um retrato do score e da temperatura de cada execução fica em `BuscaLead`, preservando o contexto histórico. O fluxo geral também começou a ser documentado em `fluxo.md`.

### Arquivos envolvidos

**Criados:**

- `fluxo.md`
- `src/main/java/dev/jlm/leadshunter/lead/TelefoneNormalizer.java`
- `src/test/java/dev/jlm/leadshunter/lead/TelefoneNormalizerTest.java`
- `src/test/java/dev/jlm/leadshunter/scoring/ScoringServiceTest.java`

**Modificados:**

- `src/main/java/dev/jlm/leadshunter/busca/BuscaService.java`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesApiClient.java`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesResponseMapper.java`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesSearchResponse.java`
- `src/main/java/dev/jlm/leadshunter/scoring/ScoringService.java`
- `src/test/java/dev/jlm/leadshunter/busca/BuscaServiceTest.java`
- `src/test/java/dev/jlm/leadshunter/integracao/places/PlacesResponseMapperTest.java`

---

## 6. Cache e limite de chamadas à Google Places — 17/08/2026

Foi adicionada proteção contra consultas externas repetidas. Buscas equivalentes passaram a reutilizar temporariamente a resposta da Google por meio de um cache local Caffeine, usando coordenadas arredondadas, raio e categorias como chave. Mesmo em um acerto de cache, uma nova busca e seus vínculos continuam sendo registrados no histórico.

As chamadas externas reais também passaram por um limite de requisições em memória com Bucket4j. Quando a capacidade temporária é esgotada, a API responde com erro apropriado, reduzindo rajadas e ajudando a proteger a cota da integração. Cache, limite e seus parâmetros configuráveis receberam testes próprios.

### Arquivos envolvidos

**Criados:**

- `src/main/java/dev/jlm/leadshunter/busca/BuscaCacheKey.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaPlacesCache.java`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesRateLimitExceededException.java`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesRateLimiter.java`
- `src/test/java/dev/jlm/leadshunter/busca/BuscaCacheKeyTest.java`
- `src/test/java/dev/jlm/leadshunter/busca/BuscaPlacesCacheTest.java`
- `src/test/java/dev/jlm/leadshunter/integracao/places/PlacesApiClientTest.java`
- `src/test/java/dev/jlm/leadshunter/integracao/places/PlacesRateLimiterTest.java`

**Modificados:**

- `fluxo.md`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaService.java`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesApiClient.java`
- `src/main/resources/application.yml`
- `src/test/java/dev/jlm/leadshunter/busca/BuscaServiceTest.java`

---

## 7. Consulta e gestão comercial dos leads — 17/08/2026

Foram implementadas a listagem dos leads, a consulta individual e a atualização parcial dos campos comerciais. A listagem aceita filtros combináveis de status, categoria e temperatura e prioriza os leads com maior score.

A atualização permite alterar apenas `status`, `observacoes` e `ultimoContatoEm`, preservando os demais dados. Requisições sem nenhum campo são rejeitadas, e leads inexistentes recebem resposta de não encontrado. Um DTO próprio evita expor diretamente a entidade de persistência na API.

### Arquivos envolvidos

**Criados:**

- `src/main/java/dev/jlm/leadshunter/lead/AtualizarLeadRequest.java`
- `src/main/java/dev/jlm/leadshunter/lead/LeadNaoEncontradoException.java`
- `src/main/java/dev/jlm/leadshunter/lead/LeadResponse.java`
- `src/test/java/dev/jlm/leadshunter/lead/AtualizarLeadRequestTest.java`
- `src/test/java/dev/jlm/leadshunter/lead/LeadServiceTest.java`

**Modificados:**

- `fluxo.md`
- `src/main/java/dev/jlm/leadshunter/lead/LeadController.java`
- `src/main/java/dev/jlm/leadshunter/lead/LeadService.java`

---

## 8. Links manuais de WhatsApp — 17/08/2026

Foi adicionada a geração de links `https://wa.me/` somente para telefones brasileiros normalizados e válidos. O link é calculado para as respostas de busca e de lead, sem ser persistido no banco.

Essa funcionalidade oferece um atalho para contato iniciado manualmente pelo usuário. Não existe disparo automático ou em massa, e leads sem telefone válido continuam com o link ausente.

### Arquivos envolvidos

**Criados:**

- `src/main/java/dev/jlm/leadshunter/lead/WhatsAppLinkGenerator.java`
- `src/test/java/dev/jlm/leadshunter/lead/WhatsAppLinkGeneratorTest.java`

**Modificados:**

- `fluxo.md`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaResponse.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaService.java`
- `src/main/java/dev/jlm/leadshunter/lead/LeadResponse.java`
- `src/main/java/dev/jlm/leadshunter/lead/LeadService.java`
- `src/test/java/dev/jlm/leadshunter/busca/BuscaServiceTest.java`
- `src/test/java/dev/jlm/leadshunter/lead/LeadServiceTest.java`

---

## 9. Consulta do histórico de buscas — 18/08/2026

Foram criados endpoints para listar as buscas da mais recente para a mais antiga e consultar o detalhe de uma execução. O resumo apresenta os parâmetros e o total encontrado; o detalhe combina os leads vinculados, o score e a temperatura registrados naquela busca e os dados comerciais atuais.

O carregamento dos vínculos foi preparado para trazer os leads associados de forma eficiente. Buscas inexistentes passaram a retornar resposta de não encontrado, e os fluxos de service e controller receberam cobertura automatizada.

### Arquivos envolvidos

**Criados:**

- `src/main/java/dev/jlm/leadshunter/busca/BuscaDetalheResponse.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaNaoEncontradaException.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaResumoResponse.java`
- `src/test/java/dev/jlm/leadshunter/busca/BuscaControllerTest.java`

**Modificados:**

- `fluxo.md`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaController.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaLeadRepository.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaService.java`
- `src/test/java/dev/jlm/leadshunter/busca/BuscaServiceTest.java`

---

## 10. Exportação de leads em CSV e Excel — 20/08/2026

Foram implementadas exportações síncronas dos leads em CSV e XLSX. Os dois formatos reutilizam os filtros e a ordenação da consulta de leads e incluem os dados externos, o scoring, os campos comerciais e o link manual de WhatsApp.

O CSV é gerado em UTF-8 e trata corretamente valores com vírgulas, aspas e quebras de linha. A planilha Excel usa Apache POI e oferece cabeçalho destacado, células adequadas para números e datas, filtro automático, primeira linha congelada e colunas ajustadas. Os endpoints também definem os nomes e tipos corretos para download.

### Arquivos envolvidos

**Criados:**

- `src/main/java/dev/jlm/leadshunter/exportacao/ExportController.java`
- `src/test/java/dev/jlm/leadshunter/exportacao/ExportControllerTest.java`
- `src/test/java/dev/jlm/leadshunter/exportacao/ExportServiceTest.java`

**Modificados:**

- `fluxo.md`
- `pom.xml`
- `src/main/java/dev/jlm/leadshunter/exportacao/ExportService.java`

---

## 11. Tratamento centralizado de erros e validações HTTP — 22/08/2026

As falhas da Google Places passaram a ser separadas por causa, como ausência ou rejeição de configuração, cota excedida, indisponibilidade, consulta rejeitada e resposta inválida. Um handler global passou a transformar essas falhas e os recursos não encontrados em um contrato JSON uniforme, sem expor detalhes internos da integração.

Na sequência, o mesmo padrão foi aplicado às validações dos controllers, incluindo payload inválido, corpo ausente ou malformado e parâmetros incompatíveis. Isso tornou as respostas de erro previsíveis em toda a API e ampliou os testes HTTP de buscas e leads.

### Arquivos envolvidos

**Criados:**

- `src/main/java/dev/jlm/leadshunter/config/ApiErrorResponse.java`
- `src/main/java/dev/jlm/leadshunter/config/ApiExceptionHandler.java`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesApiConfigurationException.java`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesApiInvalidResponseException.java`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesApiQuotaExceededException.java`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesApiRequestRejectedException.java`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesApiUnavailableException.java`
- `src/test/java/dev/jlm/leadshunter/config/ApiExceptionHandlerTest.java`
- `src/test/java/dev/jlm/leadshunter/lead/LeadControllerTest.java`

**Modificados:**

- `fluxo.md`
- `src/main/java/dev/jlm/leadshunter/integracao/places/PlacesApiClient.java`
- `src/test/java/dev/jlm/leadshunter/busca/BuscaControllerTest.java`
- `src/test/java/dev/jlm/leadshunter/integracao/places/PlacesApiClientTest.java`

---

## 12. Validação integrada da persistência JPA — 22/08/2026

Foi adicionada uma verificação integrada do fluxo de buscas usando Spring, Hibernate, Flyway e MySQL. Essa etapa comprovou no banco o relacionamento N:N, a deduplicação por `googlePlaceId`, a restrição de unicidade e o rollback transacional.

Os testes também confirmaram que buscas repetidas preservam os dados comerciais do lead e mantêm o score e a temperatura históricos em `BuscaLead`. Com isso, as principais regras de persistência deixaram de depender apenas de testes unitários com componentes simulados.

### Arquivos envolvidos

**Criados:**

- `src/test/java/dev/jlm/leadshunter/busca/BuscaServiceJpaIntegrationTest.java`

**Modificados:**

- `fluxo.md`

---

## 13. Hardening do contrato de erros HTTP — 24/08/2026

O tratamento global de erros foi ampliado para cobrir exceções inesperadas sem expor mensagens internas, nomes de classes ou detalhes potencialmente sensíveis ao cliente. A API agora retorna `500 ERRO_INTERNO` com uma mensagem genérica e registra somente método, rota e tipo da exceção para diagnóstico seguro.

Também foram adicionados testes HTTP para rate limit local, configuração ausente, consulta rejeitada pela Google, erro inesperado, JSON malformado, corpo ausente e categoria inválida no payload.

### Arquivos envolvidos

**Criados:**

Nenhum.

**Modificados:**

- `fluxo.md`
- `src/main/java/dev/jlm/leadshunter/config/ApiExceptionHandler.java`
- `src/test/java/dev/jlm/leadshunter/busca/BuscaControllerTest.java`
- `src/test/java/dev/jlm/leadshunter/config/ApiExceptionHandlerTest.java`

---

## 14. Refatoração seletiva de boilerplate com Lombok — 25/08/2026

Foi aplicada uma refatoração de legibilidade no backend usando Lombok somente nos pontos em que a geração de código é segura e reduz manutenção. As entidades JPA passaram a declarar getters, setters e construtor sem argumentos por anotações explícitas, enquanto services e controllers passaram a usar construtores gerados para suas dependências obrigatórias.

O advice global de erros também passou a usar `@Slf4j`. Records, construtores especiais de integração e classes de configuração com parâmetros `@Value` foram preservados. Nenhuma entidade recebeu `@Data`, `@Builder`, `@EqualsAndHashCode` ou `@ToString`, evitando efeitos indesejados com relacionamentos JPA e proxies Hibernate.

### Arquivos envolvidos

**Criados:**

Nenhum.

**Modificados:**

- `fluxo.md`
- `src/main/java/dev/jlm/leadshunter/busca/Busca.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaController.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaLead.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaService.java`
- `src/main/java/dev/jlm/leadshunter/config/ApiExceptionHandler.java`
- `src/main/java/dev/jlm/leadshunter/exportacao/ExportController.java`
- `src/main/java/dev/jlm/leadshunter/exportacao/ExportService.java`
- `src/main/java/dev/jlm/leadshunter/lead/Lead.java`
- `src/main/java/dev/jlm/leadshunter/lead/LeadController.java`
- `src/main/java/dev/jlm/leadshunter/lead/LeadService.java`

---

## 15. Bootstrap do frontend Angular — 29/08/2026

Foi criada a aplicação frontend do Leads Hunter em Angular 22.1.4, com TypeScript strict, componentes standalone, routing, SCSS, testes habilitados e sem SSR. A página inicial confirma a renderização da aplicação, e o servidor de desenvolvimento foi configurado para encaminhar chamadas `/api` ao backend local em `http://localhost:8080`.

O projeto foi instalado com Angular CLI 22.1.6 por meio do `npx`, mantendo o frontend isolado no diretório `frontend/`. Também foram incorporadas as instruções Angular usadas no bootstrap e o plano incremental do ciclo frontend. A suíte inicial e o build de produção foram validados com sucesso.

### Arquivos envolvidos

**Criados:**

- `.opencode/skills/angular-developer/`
- `.opencode/skills/angular-new-app/SKILL.md`
- `FRONTEND_SPRINTS.md`
- `frontend/.codex/config.toml`
- `frontend/.editorconfig`
- `frontend/.gitignore`
- `frontend/.prettierrc`
- `frontend/AGENTS.md`
- `frontend/angular.json`
- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/proxy.conf.json`
- `frontend/README.md`
- `frontend/tsconfig.json`
- `frontend/tsconfig.app.json`
- `frontend/tsconfig.spec.json`
- `frontend/public/favicon.ico`
- `frontend/src/main.ts`
- `frontend/src/index.html`
- `frontend/src/styles.scss`
- `frontend/src/app/app.config.ts`
- `frontend/src/app/app.routes.ts`
- `frontend/src/app/app.spec.ts`
- `frontend/src/app/app.scss`
- `frontend/src/app/app.ts`
- `frontend/src/app/app.html`

**Modificados:**

- `.opencode/skills/leadradar-frontend/SKILL.md`
- `fluxo.md`

---

## 16. Contratos TypeScript e base HTTP do frontend — 29/08/2026

Foi criada a base fortemente tipada para o frontend consumir os contratos reais da API. A configuração Angular passou a fornecer `HttpClient`, os DTOs e enums foram organizados por domínio, o prefixo relativo `/api` foi centralizado e as falhas HTTP passaram a ter mensagens seguras para a interface, sem expor detalhes internos. Testes de contrato e do utilitário foram adicionados sem chamadas de rede.

### Arquivos envolvidos

**Criados:**

- `frontend/src/app/core/api/api-error-message.spec.ts`
- `frontend/src/app/core/api/api-error-message.ts`
- `frontend/src/app/core/api/api-routes.ts`
- `frontend/src/app/shared/models/api-contracts.spec.ts`
- `frontend/src/app/shared/models/api-error-response.model.ts`
- `frontend/src/app/shared/models/busca.model.ts`
- `frontend/src/app/shared/models/date.model.ts`
- `frontend/src/app/shared/models/enums.model.ts`
- `frontend/src/app/shared/models/lead.model.ts`

**Modificados:**

- `FRONTEND_SPRINTS.md`
- `fluxo.md`
- `frontend/src/app/app.config.ts`

---

## 17. Correção dos contratos do FE-01 — 29/08/2026

Após revisão independente do sprint, o contrato TypeScript de atualização de lead passou a impedir payloads vazios ou compostos somente por valores nulos, acompanhando a validação já existente no backend. Os testes de contrato também passaram a montar os leads resumidos e históricos completos, cobrindo os campos dos DTOs aninhados. A skill de frontend foi reforçada para exigir autorização explícita no prompt atual antes de operações Git que alterem índice, histórico ou repositório remoto.

### Arquivos envolvidos

**Criados:**

Nenhum.

**Modificados:**

- `.opencode/skills/leadradar-frontend/SKILL.md`
- `FRONTEND_SPRINTS.md`
- `fluxo.md`
- `frontend/src/app/shared/models/api-contracts.spec.ts`
- `frontend/src/app/shared/models/lead.model.ts`

---

## 18. Shell, navegação e rotas do frontend — 29/08/2026

Foi entregue o shell navegável do Leads Hunter, com cabeçalho, navegação principal para Busca, Kanban e Histórico, área de conteúdo, rodapé e fallback acessível para rotas desconhecidas. As três áreas usam carregamento lazy e permanecem como placeholders, sem antecipar mapa, integração HTTP ou regras de negócio. Também foram adicionados tokens visuais básicos de cores, espaçamento, tipografia, foco e estados, além de skip link e responsividade inicial.

### Arquivos envolvidos

**Criados:**

- `frontend/src/app/app.routes.spec.ts`
- `frontend/src/app/features/busca/busca-page.ts`
- `frontend/src/app/features/historico/historico-page.ts`
- `frontend/src/app/features/kanban/kanban-page.ts`
- `frontend/src/app/features/not-found/not-found-page.ts`

**Modificados:**

- `FRONTEND_SPRINTS.md`
- `fluxo.md`
- `frontend/src/app/app.html`
- `frontend/src/app/app.scss`
- `frontend/src/app/app.routes.ts`
- `frontend/src/app/app.spec.ts`
- `frontend/src/app/app.ts`
- `frontend/src/styles.scss`

---

## 19. Mapa Leaflet interativo do frontend — 29/08/2026

Foi adicionado à tela de Busca um mapa interativo para escolher o ponto central da prospecção por clique, arraste do marcador ou confirmação por teclado, e visualizar o raio atual em um círculo. O mapa usa tiles do OpenStreetMap com atribuição visível, reaproveita as mesmas camadas quando ponto ou raio mudam e encerra corretamente seus recursos ao sair da rota. A interface mostra as coordenadas selecionadas sem antecipar o formulário ou a execução da busca dos próximos sprints.

### Arquivos envolvidos

**Criados:**

- `frontend/src/app/features/busca/busca-page.html`
- `frontend/src/app/features/busca/busca-page.scss`
- `frontend/src/app/features/busca/mapa-busca.html`
- `frontend/src/app/features/busca/mapa-busca.scss`
- `frontend/src/app/features/busca/mapa-busca.spec.ts`
- `frontend/src/app/features/busca/mapa-busca.ts`
- `frontend/src/app/features/busca/mapa.model.spec.ts`
- `frontend/src/app/features/busca/mapa.model.ts`

**Modificados:**

- `FRONTEND_SPRINTS.md`
- `fluxo.md`
- `frontend/angular.json`
- `frontend/package-lock.json`
- `frontend/package.json`
- `frontend/src/app/features/busca/busca-page.ts`

---

## 20. Formulário de busca sincronizado ao mapa — 31/08/2026

Foi adicionado à tela de Busca um formulário baseado em Signal Forms para configurar endereço de referência, coordenadas, raio e múltiplas categorias. O formulário aplica os limites reais do backend, mantém o submit bloqueado para configurações inválidas e converte os rótulos amigáveis para os valores exatos do enum no request tipado.

O formulário e o mapa compartilham o mesmo estado: clique ou arraste do marcador atualizam as coordenadas exibidas, enquanto alterações nas coordenadas e no slider atualizam o mapa e seu círculo. O endereço permanece apenas descritivo, sem geocodificação ou autocomplete, e nenhuma chamada HTTP foi antecipada.

### Arquivos envolvidos

**Criados:**

- `frontend/src/app/features/busca/busca-form.html`
- `frontend/src/app/features/busca/busca-form.model.spec.ts`
- `frontend/src/app/features/busca/busca-form.model.ts`
- `frontend/src/app/features/busca/busca-form.scss`
- `frontend/src/app/features/busca/busca-form.spec.ts`
- `frontend/src/app/features/busca/busca-form.ts`
- `frontend/src/app/features/busca/busca-page.spec.ts`

**Modificados:**

- `FRONTEND_SPRINTS.md`
- `fluxo.md`
- `frontend/src/app/features/busca/busca-page.html`
- `frontend/src/app/features/busca/busca-page.scss`
- `frontend/src/app/features/busca/busca-page.ts`
- `frontend/src/app/features/busca/mapa-busca.spec.ts`
- `frontend/src/app/features/busca/mapa-busca.ts`

---

## 21. Execução da busca pela API no frontend — 31/08/2026

O formulário de busca passou a executar o endpoint real `POST /api/buscas` por meio de uma integração HTTP tipada. A tela bloqueia submissões simultâneas, informa o carregamento, mantém a resposta completa confirmada pelo backend e diferencia sucesso com leads, sucesso sem leads e falhas recuperáveis.

Os erros previstos para validação, rate limit, integração com a Google e falha interna apresentam mensagens seguras, sem expor respostas desconhecidas. A operação pode ser enviada novamente após uma falha. Foram adicionados testes HTTP e de componente para payload, resposta, códigos `400`, `429`, `502`, `503` e `500`, clique duplo, retry e resposta vazia.

### Arquivos envolvidos

**Criados:**

- `frontend/src/app/core/api/busca-api.spec.ts`
- `frontend/src/app/core/api/busca-api.ts`

**Modificados:**

- `FRONTEND_SPRINTS.md`
- `fluxo.md`
- `frontend/src/app/features/busca/busca-form.html`
- `frontend/src/app/features/busca/busca-form.scss`
- `frontend/src/app/features/busca/busca-form.spec.ts`
- `frontend/src/app/features/busca/busca-form.ts`
- `frontend/src/app/features/busca/busca-page.html`
- `frontend/src/app/features/busca/busca-page.scss`
- `frontend/src/app/features/busca/busca-page.spec.ts`
- `frontend/src/app/features/busca/busca-page.ts`

---

## 22. Apresentação dos resultados da busca no frontend — 31/08/2026

A resposta de uma busca concluída passou a ser apresentada em uma visão resumida e responsiva. A tela mostra os parâmetros e a data da execução, lista os leads com os dados realmente disponíveis e identifica a temperatura por texto. O link manual de WhatsApp só é exibido quando retornado pelo backend e abre em nova aba com proteção apropriada.

Buscas sem leads continuam registradas como concluídas, apresentam orientação para ajustar a configuração e permitem seguir para o Kanban. Foram adicionados testes para resultados completos, parciais e vazios, incluindo a presença e a ausência do link de WhatsApp.

### Arquivos envolvidos

**Criados:**

- `frontend/src/app/features/busca/busca-resultados.html`
- `frontend/src/app/features/busca/busca-resultados.scss`
- `frontend/src/app/features/busca/busca-resultados.spec.ts`
- `frontend/src/app/features/busca/busca-resultados.ts`

**Modificados:**

- `FRONTEND_SPRINTS.md`
- `fluxo.md`
- `frontend/src/app/features/busca/busca-page.html`
- `frontend/src/app/features/busca/busca-page.scss`
- `frontend/src/app/features/busca/busca-page.spec.ts`
- `frontend/src/app/features/busca/busca-page.ts`
