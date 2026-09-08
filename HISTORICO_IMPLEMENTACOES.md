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

---

## 23. Consulta e filtros de leads no frontend — 02/09/2026

A rota Kanban foi conectada à listagem real de leads persistidos. A tela agora consulta os dados ao entrar, permite combinar ou limpar filtros de etapa, categoria e temperatura e apresenta estados claros de carregamento, resultado vazio e falha com nova tentativa. Consultas simultâneas são bloqueadas, a ordenação recebida do backend é mantida e uma falha posterior não apaga a última lista válida.

### Arquivos envolvidos

**Criados:**

- `frontend/src/app/core/api/lead-api.spec.ts`
- `frontend/src/app/core/api/lead-api.ts`
- `frontend/src/app/features/kanban/kanban-page.html`
- `frontend/src/app/features/kanban/kanban-page.scss`
- `frontend/src/app/features/kanban/kanban-page.spec.ts`
- `frontend/src/app/features/kanban/lead-filters.html`
- `frontend/src/app/features/kanban/lead-filters.scss`
- `frontend/src/app/features/kanban/lead-filters.ts`

**Modificados:**

- `FRONTEND_SPRINTS.md`
- `fluxo.md`
- `frontend/src/app/features/kanban/kanban-page.ts`

---

## 24. Kanban somente leitura e cards de leads — 02/09/2026

A lista filtrada de leads passou a ser apresentada em um quadro Kanban somente leitura com as cinco etapas reais do funil. O agrupamento mantém cada lead em uma única coluna, calcula os contadores a partir das listas e preserva registros sem status válido em uma etapa separada, sem classificá-los como novos.

Os cards mostram de forma compacta apenas os dados disponíveis e identificam status e temperatura por texto. O quadro mantém os filtros existentes, distingue a lista global vazia de uma coluna vazia e oferece rolagem horizontal acessível para conservar a leitura dos cards em telas estreitas. A direção visual foi mantida contida e operacional, sem efeitos decorativos que prejudiquem a densidade de informação.

### Arquivos envolvidos

**Criados:**

- `frontend/src/app/features/kanban/kanban-board.html`
- `frontend/src/app/features/kanban/kanban-board.scss`
- `frontend/src/app/features/kanban/kanban-board.spec.ts`
- `frontend/src/app/features/kanban/kanban-board.ts`
- `frontend/src/app/features/kanban/kanban-column.html`
- `frontend/src/app/features/kanban/kanban-column.scss`
- `frontend/src/app/features/kanban/kanban-column.ts`
- `frontend/src/app/features/kanban/kanban.model.spec.ts`
- `frontend/src/app/features/kanban/kanban.model.ts`
- `frontend/src/app/features/kanban/lead-card.html`
- `frontend/src/app/features/kanban/lead-card.scss`
- `frontend/src/app/features/kanban/lead-card.spec.ts`
- `frontend/src/app/features/kanban/lead-card.ts`

**Modificados:**

- `FRONTEND_SPRINTS.md`
- `fluxo.md`
- `frontend/src/app/features/kanban/kanban-page.html`
- `frontend/src/app/features/kanban/kanban-page.scss`
- `frontend/src/app/features/kanban/kanban-page.spec.ts`
- `frontend/src/app/features/kanban/kanban-page.ts`
- `frontend/src/app/features/kanban/lead-filters.ts`

---

## 25. Movimentação persistida de leads no Kanban — 02/09/2026

Os cards do Kanban passaram a ser movimentados entre as cinco etapas com Angular CDK Drag and Drop. A interface atualiza a coluna imediatamente e persiste somente o novo status pela rota real de atualização do lead, substituindo o estado provisório pela resposta completa do backend.

Durante o salvamento, novas mudanças do mesmo card ficam bloqueadas. Se a operação falhar, o card retorna à etapa anterior e a tela apresenta uma mensagem segura. Os filtros permanecem selecionados e continuam coerentes com a lista após a confirmação. Também foram adicionados controles textuais de etapa anterior e próxima para permitir a mesma operação por teclado, sem depender do drag.

### Arquivos envolvidos

**Modificados:**

- `FRONTEND_SPRINTS.md`
- `fluxo.md`
- `frontend/package-lock.json`
- `frontend/package.json`
- `frontend/src/app/core/api/api-routes.ts`
- `frontend/src/app/core/api/lead-api.spec.ts`
- `frontend/src/app/core/api/lead-api.ts`
- `frontend/src/app/features/kanban/kanban-board.html`
- `frontend/src/app/features/kanban/kanban-board.spec.ts`
- `frontend/src/app/features/kanban/kanban-board.ts`
- `frontend/src/app/features/kanban/kanban-column.html`
- `frontend/src/app/features/kanban/kanban-column.scss`
- `frontend/src/app/features/kanban/kanban-column.ts`
- `frontend/src/app/features/kanban/kanban-page.html`
- `frontend/src/app/features/kanban/kanban-page.scss`
- `frontend/src/app/features/kanban/kanban-page.spec.ts`
- `frontend/src/app/features/kanban/kanban-page.ts`
- `frontend/src/app/features/kanban/kanban.model.spec.ts`
- `frontend/src/app/features/kanban/kanban.model.ts`
- `frontend/src/app/features/kanban/lead-card.html`
- `frontend/src/app/features/kanban/lead-card.scss`
- `frontend/src/app/features/kanban/lead-card.spec.ts`
- `frontend/src/app/features/kanban/lead-card.ts`
- `frontend/src/app/features/kanban/lead-filters.html`
- `frontend/src/app/features/kanban/lead-filters.ts`

---

## 26. Detalhe do lead e WhatsApp manual no Kanban — 03/09/2026

O título de cada card do Kanban passou a abrir um painel lateral de detalhe do lead. O painel usa o contrato completo já carregado pela listagem e exibe dados externos, classificação e dados comerciais, omitindo campos nulos, e apresenta o link manual de WhatsApp somente quando o backend retorna `whatsappUrl`. O painel é um diálogo acessível que abre por teclado, mantém o foco preso, fecha por botão, tecla Escape ou clique no backdrop, e devolve o foco ao controle que o abriu.

### Arquivos envolvidos

**Criados:**

- `frontend/src/app/features/kanban/lead-detalhe.html`
- `frontend/src/app/features/kanban/lead-detalhe.scss`
- `frontend/src/app/features/kanban/lead-detalhe.spec.ts`
- `frontend/src/app/features/kanban/lead-detalhe.ts`

**Modificados:**

- `FRONTEND_SPRINTS.md`
- `fluxo.md`
- `frontend/src/app/features/kanban/kanban-board.html`
- `frontend/src/app/features/kanban/kanban-board.ts`
- `frontend/src/app/features/kanban/kanban-column.html`
- `frontend/src/app/features/kanban/kanban-column.ts`
- `frontend/src/app/features/kanban/kanban-page.html`
- `frontend/src/app/features/kanban/kanban-page.spec.ts`
- `frontend/src/app/features/kanban/kanban-page.ts`
- `frontend/src/app/features/kanban/lead-card.html`
- `frontend/src/app/features/kanban/lead-card.scss`
- `frontend/src/app/features/kanban/lead-card.spec.ts`
- `frontend/src/app/features/kanban/lead-card.ts`

---

## 27. Edição de observações e último contato no detalhe — 03/09/2026

A área de dados comerciais do painel de detalhe do lead passou a permitir edição com salvamento explícito. O usuário altera as observações e o último contato, e o painel envia somente os campos alterados para `PATCH /api/leads/{id}`, usando a resposta confirmada para atualizar o detalhe e a lista do Kanban. Observações podem ser limpas com string vazia, enquanto a interface explica que o último contato não pode ser removido no contrato atual. Falhas preservam o texto digitado, e fechar com alterações não salvas exibe um aviso até salvar ou cancelar.

### Arquivos envolvidos

**Modificados:**

- `FRONTEND_SPRINTS.md`
- `fluxo.md`
- `frontend/angular.json`
- `frontend/src/app/features/kanban/kanban-page.html`
- `frontend/src/app/features/kanban/kanban-page.spec.ts`
- `frontend/src/app/features/kanban/kanban-page.ts`
- `frontend/src/app/features/kanban/lead-detalhe.html`
- `frontend/src/app/features/kanban/lead-detalhe.scss`
- `frontend/src/app/features/kanban/lead-detalhe.spec.ts`
- `frontend/src/app/features/kanban/lead-detalhe.ts`

---

## 28. Lista do histórico de buscas — 03/09/2026

A área de Histórico passou a consultar as buscas persistidas por `GET /api/buscas` e apresentá-las na ordem fornecida pelo backend. A tabela mostra data local sem conversão de fuso horário, endereço-base, categorias, raio e total encontrado, com tratamento próprio para carregamento, lista vazia, erro e nova tentativa.

Cada registro possui navegação acessível para a rota identificada `/historico/:id`. Essa rota mantém o contexto visual de Histórico e prepara a transição para o detalhe completo, que permanece no escopo do sprint seguinte.

### Arquivos envolvidos

**Criados:**

- `frontend/src/app/features/historico/historico-detalhe-page.ts`
- `frontend/src/app/features/historico/historico-page.html`
- `frontend/src/app/features/historico/historico-page.scss`
- `frontend/src/app/features/historico/historico-page.spec.ts`

**Modificados:**

- `FRONTEND_SPRINTS.md`
- `fluxo.md`
- `frontend/src/app/app.html`
- `frontend/src/app/app.routes.spec.ts`
- `frontend/src/app/app.routes.ts`
- `frontend/src/app/app.spec.ts`
- `frontend/src/app/app.ts`
- `frontend/src/app/core/api/busca-api.spec.ts`
- `frontend/src/app/core/api/busca-api.ts`
- `frontend/src/app/features/historico/historico-page.ts`

---

## 29. Detalhe de uma busca anterior — 03/09/2026

A rota identificada do Histórico passou a consultar `GET /api/buscas/{id}` e apresentar os parâmetros e leads registrados naquela execução. Score e temperatura históricos ficam separados do status, das observações e do último contato atuais, preservando o significado de cada dado sem recalcular a busca.

A tela mantém a ordem retornada pelo backend, oferece WhatsApp manual somente quando existe uma URL válida no contrato e trata carregamento, execução sem leads, identificador inválido, busca inexistente e erro com nova tentativa.

### Arquivos envolvidos

**Criados:**

- `frontend/src/app/features/historico/historico-detalhe-page.html`
- `frontend/src/app/features/historico/historico-detalhe-page.scss`
- `frontend/src/app/features/historico/historico-detalhe-page.spec.ts`

**Modificados:**

- `FRONTEND_SPRINTS.md`
- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `frontend/src/app/core/api/api-routes.ts`
- `frontend/src/app/core/api/busca-api.spec.ts`
- `frontend/src/app/core/api/busca-api.ts`
- `frontend/src/app/features/historico/historico-detalhe-page.ts`

---

## 30. Downloads CSV e XLSX no Kanban — 03/09/2026

O Kanban passou a baixar as exportações CSV e Excel produzidas pelo backend com os filtros de status, categoria e temperatura selecionados na interface. Cada formato possui carregamento independente, bloqueio contra repetição durante a geração e mensagens próprias de sucesso ou falha.

A integração trata as respostas como arquivos binários, usa nome e tipo MIME dos headers com fallbacks seguros e converte erros JSON recebidos como blob antes de apresentá-los. O download cria uma URL temporária somente após sucesso e sempre a libera depois do clique.

### Arquivos envolvidos

**Criados:**

- `frontend/src/app/core/api/exportacao-api.spec.ts`
- `frontend/src/app/core/api/exportacao-api.ts`
- `frontend/src/app/core/browser/arquivo-downloader.spec.ts`
- `frontend/src/app/core/browser/arquivo-downloader.ts`
- `frontend/src/app/features/kanban/exportacao-leads.html`
- `frontend/src/app/features/kanban/exportacao-leads.scss`
- `frontend/src/app/features/kanban/exportacao-leads.spec.ts`
- `frontend/src/app/features/kanban/exportacao-leads.ts`

**Modificados:**

- `FRONTEND_SPRINTS.md`
- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `frontend/src/app/features/kanban/kanban-page.html`
- `frontend/src/app/features/kanban/kanban-page.spec.ts`
- `frontend/src/app/features/kanban/kanban-page.ts`

---

## 31. Shell operacional e workspace da Busca — 04/09/2026

O shell centralizado foi substituído por uma estrutura de aplicação operacional com sidebar persistente e workspace amplo. A navegação entre Busca, Kanban e Histórico passou para a lateral, com identificação textual da rota ativa, e o header e o footer permanentes foram removidos para liberar a viewport.

Na Busca, os parâmetros passaram a ocupar uma região operacional compacta e simultânea ao mapa. O mapa tornou-se o elemento dominante do workspace, mantendo a sincronização com os campos, e os resultados foram adensados para aproveitar a largura disponível sem alterar regras de negócio ou contratos HTTP.

### Arquivos envolvidos

**Criados:**

- `.opencode/skills/leadradar-antislopUI/SKILL.md`

**Modificados:**

- `.opencode/skills/leadradar-frontend/SKILL.md`
- `AGENTS.md`
- `FRONTEND_SPRINTS.md`
- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `frontend/src/app/app.html`
- `frontend/src/app/app.scss`
- `frontend/src/app/app.spec.ts`
- `frontend/src/app/features/busca/busca-form.html`
- `frontend/src/app/features/busca/busca-form.scss`
- `frontend/src/app/features/busca/busca-page.html`
- `frontend/src/app/features/busca/busca-page.scss`
- `frontend/src/app/features/busca/busca-page.spec.ts`
- `frontend/src/app/features/busca/busca-resultados.scss`
- `frontend/src/app/features/busca/mapa-busca.scss`

---

## 32. Adaptação de Kanban e Histórico ao workspace operacional — 04/09/2026

O Kanban foi integrado ao workspace amplo criado na etapa anterior, com cabeçalho e controles mais compactos e cinco colunas usando simultaneamente a largura disponível no desktop. A rolagem horizontal ficou restrita ao quadro em larguras menores, enquanto cards, drag-and-drop, persistência de status, filtros, exportações e drawer de detalhes mantiveram o comportamento existente.

A lista e o detalhe do Histórico passaram a seguir a mesma estrutura operacional, com regiões úteis próprias, resumo mais compacto e tabelas densas para facilitar a leitura. A navegação entre Busca, Kanban, Histórico e detalhe foi preservada sem alteração de contratos HTTP ou regras de negócio.

### Arquivos envolvidos

**Modificados:**

- `FRONTEND_SPRINTS.md`
- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `frontend/src/app/features/historico/historico-detalhe-page.html`
- `frontend/src/app/features/historico/historico-detalhe-page.scss`
- `frontend/src/app/features/historico/historico-detalhe-page.spec.ts`
- `frontend/src/app/features/historico/historico-page.html`
- `frontend/src/app/features/historico/historico-page.scss`
- `frontend/src/app/features/historico/historico-page.spec.ts`
- `frontend/src/app/features/kanban/kanban-board.scss`
- `frontend/src/app/features/kanban/kanban-page.html`
- `frontend/src/app/features/kanban/kanban-page.scss`
- `frontend/src/app/features/kanban/kanban-page.spec.ts`
- `frontend/src/app/features/kanban/lead-filters.scss`

---

## 33. Scroll e paginação independentes no Kanban — 04/09/2026

O Kanban passou a consultar cada etapa do funil separadamente no backend, com páginas reais de até 25 leads. Cada coluna mantém página, total, carregamento, erro e estado vazio próprios, de modo que trocar uma página não apaga nem reposiciona as demais.

O board agora ocupa a altura restante do workspace e cada coluna possui rolagem vertical própria, preservando a rolagem horizontal em telas estreitas. Movimentos continuam otimistas e com rollback; após a confirmação, somente as colunas de origem e destino são reconsultadas para manter páginas e totais consistentes. Filtros, exportações, detalhe do lead e controles acessíveis por teclado foram preservados.

### Arquivos envolvidos

**Criados:**

- `src/main/java/dev/jlm/leadshunter/lead/PaginaLeadsResponse.java`

**Modificados:**

- `API.md`
- `FRONTEND_SPRINTS.md`
- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `frontend/src/app/app.scss`
- `frontend/src/app/core/api/api-routes.ts`
- `frontend/src/app/core/api/lead-api.spec.ts`
- `frontend/src/app/core/api/lead-api.ts`
- `frontend/src/app/features/kanban/kanban-board.html`
- `frontend/src/app/features/kanban/kanban-board.scss`
- `frontend/src/app/features/kanban/kanban-board.spec.ts`
- `frontend/src/app/features/kanban/kanban-board.ts`
- `frontend/src/app/features/kanban/kanban-column.html`
- `frontend/src/app/features/kanban/kanban-column.scss`
- `frontend/src/app/features/kanban/kanban-column.ts`
- `frontend/src/app/features/kanban/kanban-page.html`
- `frontend/src/app/features/kanban/kanban-page.scss`
- `frontend/src/app/features/kanban/kanban-page.spec.ts`
- `frontend/src/app/features/kanban/kanban-page.ts`
- `frontend/src/app/features/kanban/kanban.model.spec.ts`
- `frontend/src/app/features/kanban/kanban.model.ts`
- `frontend/src/app/shared/models/lead.model.ts`
- `src/main/java/dev/jlm/leadshunter/config/ApiExceptionHandler.java`
- `src/main/java/dev/jlm/leadshunter/lead/LeadController.java`
- `src/main/java/dev/jlm/leadshunter/lead/LeadService.java`
- `src/test/java/dev/jlm/leadshunter/lead/LeadControllerTest.java`
- `src/test/java/dev/jlm/leadshunter/lead/LeadServiceTest.java`

---

## 34. Reorganização visual do detalhe do lead — 04/09/2026

O drawer aberto pelo Kanban foi reorganizado para apresentar o lead em blocos compactos e fáceis de escanear. Score, nota e avaliações passaram a formar um resumo comercial; telefone, endereço e coordenadas ficaram agrupados como dados do estabelecimento; e último contato e observações ganharam uma seção comercial com leitura vertical e espaço adequado para textos maiores.

A ação manual de WhatsApp permaneceu como ação principal, enquanto categoria, status, temperatura e metadados foram adensados conforme sua importância. A largura lateral e o comportamento responsivo foram ajustados sem alterar abertura, fechamento, foco, edição ou contratos HTTP.

### Arquivos envolvidos

**Modificados:**

- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `frontend/src/app/features/kanban/lead-detalhe.html`
- `frontend/src/app/features/kanban/lead-detalhe.scss`
- `frontend/src/app/features/kanban/lead-detalhe.spec.ts`

---

## 35. Refinamento de espaçamento do drawer de lead — 04/09/2026

O drawer recebeu um refinamento visual para aproveitar melhor sua largura e altura. O conteúdo passou a ter padding e espaçamento efetivos, as métricas deixaram de parecer uma tabela com divisórias, os dados do estabelecimento foram alinhados em linhas de leitura rápida e o rodapé passou a ocupar o final do painel. A área de observações também ficou mais confortável para textos maiores.

### Arquivos envolvidos

**Modificados:**

- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `frontend/src/app/features/kanban/lead-detalhe.scss`

---

## 36. Feedback explícito da busca de leads — 04/09/2026

A tela de Busca passou a comunicar claramente o ciclo da operação sem alterar o endpoint ou os parâmetros enviados. Durante o carregamento, o botão fica bloqueado, muda para “Buscando leads...” e exibe um indicador discreto. Ao concluir, a interface informa a quantidade encontrada, diferencia o caso vazio e oferece atalhos para Kanban e Histórico; falhas continuam usando as mensagens seguras já existentes.

### Arquivos envolvidos

**Modificados:**

- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `frontend/src/app/features/busca/busca-form.html`
- `frontend/src/app/features/busca/busca-form.scss`
- `frontend/src/app/features/busca/busca-form.spec.ts`
- `frontend/src/app/features/busca/busca-page.html`
- `frontend/src/app/features/busca/busca-page.scss`
- `frontend/src/app/features/busca/busca-page.spec.ts`
- `frontend/src/app/features/busca/busca-page.ts`

---

## 37. Polimento integrado, responsividade e acessibilidade — 05/09/2026

O frontend recebeu o polimento integrado do FE-16. As telas passaram a compartilhar tokens semânticos de estado, controles e loaders, o shell move o foco para o conteúdo após navegação e as mensagens de erro e salvamento recebem foco programático. O Kanban ganhou fluxo vertical natural em telas menores sem perder o workspace com scroll horizontal localizado e scroll vertical independente por coluna. O drawer continua acessível por teclado, com foco preso, Escape e retorno ao gatilho; o mapa mantém sua instância Leaflet e o marcador possui navegação por teclado explícita.

### Arquivos envolvidos

**Criados:**

Nenhum.

**Modificados:**

- `FRONTEND_SPRINTS.md`
- `fluxo.md`
- `frontend/src/styles.scss`
- `frontend/src/app/app.html`
- `frontend/src/app/app.ts`
- `frontend/src/app/features/busca/busca-form.html`
- `frontend/src/app/features/busca/busca-form.scss`
- `frontend/src/app/features/busca/busca-page.html`
- `frontend/src/app/features/busca/busca-page.scss`
- `frontend/src/app/features/busca/busca-page.ts`
- `frontend/src/app/features/busca/busca-resultados.scss`
- `frontend/src/app/features/busca/mapa-busca.html`
- `frontend/src/app/features/busca/mapa-busca.ts`
- `frontend/src/app/features/historico/historico-detalhe-page.html`
- `frontend/src/app/features/historico/historico-detalhe-page.scss`
- `frontend/src/app/features/historico/historico-detalhe-page.ts`
- `frontend/src/app/features/historico/historico-page.html`
- `frontend/src/app/features/historico/historico-page.scss`
- `frontend/src/app/features/historico/historico-page.ts`
- `frontend/src/app/features/kanban/exportacao-leads.html`
- `frontend/src/app/features/kanban/exportacao-leads.scss`
- `frontend/src/app/features/kanban/kanban-column.html`
- `frontend/src/app/features/kanban/kanban-column.scss`
- `frontend/src/app/features/kanban/kanban-page.html`
- `frontend/src/app/features/kanban/kanban-page.scss`
- `frontend/src/app/features/kanban/kanban-page.ts`
- `frontend/src/app/features/kanban/lead-card.scss`
- `frontend/src/app/features/kanban/lead-detalhe.html`
- `frontend/src/app/features/kanban/lead-detalhe.scss`
- `frontend/src/app/features/kanban/lead-detalhe.ts`
- `frontend/src/app/features/kanban/lead-filters.html`
- `frontend/src/app/features/kanban/lead-filters.scss`

---

## 38. Testes de fluxo e fechamento do MVP — 05/09/2026

Foram adicionados testes de integração e um smoke E2E controlado para validar o caminho principal do MVP: busca, resultados, Kanban, atualização comercial, reload, histórico e exportação. A integração usa o backend real com Flyway e MySQL local e substitui apenas a chamada à Places por um cliente controlado, evitando chave real e consumo de cota. O smoke de navegador usa Playwright, verifica o link manual de WhatsApp e oferece um modo local documentado para execução contra o backend.

### Arquivos envolvidos

**Criados:**

- `src/test/java/dev/jlm/leadshunter/MvpFlowIntegrationTest.java`
- `frontend/scripts/mvp-flow-smoke.mjs`

**Modificados:**

- `frontend/package.json`
- `frontend/README.md`
- `FRONTEND_SPRINTS.md`
- `fluxo.md`

---

## 39. WhatsApp visível no card do Kanban — 05/09/2026

O card de cada lead no Kanban passou a exibir o canal WhatsApp junto do telefone: o rótulo "WhatsApp" e o link manual "Abrir WhatsApp" aparecem ao lado direito do telefone sempre que o backend entrega `whatsappUrl` no `LeadResponse`. Antes, esse link só era visível no drawer de detalhes aberto ao clicar no nome do lead. Leads sem `whatsappUrl` continuam mostrando apenas o telefone, sem texto de indisponibilidade no card, e o drawer mantém o comportamento atual. O escopo anterior de `refinamento.md`, sobre validação de presença do número no WhatsApp via provedor oficial, foi descartado por decisão de produto; o documento passou a descrever esta feature. A mudança é exclusivamente de frontend e a suíte passou com 152 testes.

### Arquivos envolvidos

**Criados:**

Nenhum.

**Modificados:**

- `refinamento.md`
- `frontend/src/app/features/kanban/lead-card.html`
- `frontend/src/app/features/kanban/lead-card.scss`
- `frontend/src/app/features/kanban/lead-card.spec.ts`
- `fluxo.md`

---

## 40. Dataset municipal de IDHM 2010 — 05/09/2026

Foi produzido e congelado o dataset geográfico que servirá de base para o enriquecimento futuro dos leads com município, UF e IDHM. Um gerador offline combina os dados de IDHM 2010 com a malha municipal oficial do IBGE pelo código do município, simplifica as geometrias, calcula seus limites e valida a cobertura nacional.

O processo registra procedência, licenças e checksums, não exige chave de API e não introduz downloads durante a execução da aplicação. Testes próprios validam os 5.570 municípios e confirmam a localização e os valores de IDHM de Vitória e Curitiba.

### Arquivos envolvidos

**Criados:**

- `src/main/resources/geo/municipios-idhm.json`
- `tools/idhm/README.md`
- `tools/idhm/gerar_dataset.py`
- `tools/idhm/test_gerar_dataset.py`

**Modificados:**

- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `refinamento.md`

---

## 41. Enriquecimento municipal e IDHM dos leads — 05/09/2026

O backend passou a localizar offline o município de cada estabelecimento pelas coordenadas retornadas na busca e a persistir código IBGE, município, UF, IDHM e referência 2010 no lead. A localização usa o dataset municipal congelado, pré-filtro por limites geográficos e point-in-polygon, sem API key ou chamada externa adicional.

Foi criada a migration V2 com as novas colunas e índices. Também foi disponibilizado um backfill opcional, desligado por padrão, que processa em lotes os leads anteriores com coordenadas e sem município. O fluxo mantém a deduplicação, o score, os snapshots históricos e os dados comerciais existentes.

### Arquivos envolvidos

**Criados:**

- `src/main/java/dev/jlm/leadshunter/geo/MunicipioBackfillRunner.java`
- `src/main/java/dev/jlm/leadshunter/geo/MunicipioBackfillService.java`
- `src/main/java/dev/jlm/leadshunter/geo/MunicipioDataset.java`
- `src/main/java/dev/jlm/leadshunter/geo/MunicipioInfo.java`
- `src/main/java/dev/jlm/leadshunter/geo/MunicipioService.java`
- `src/main/resources/db/migration/V2__adicionar_geografia_lead.sql`
- `src/test/java/dev/jlm/leadshunter/geo/MunicipioBackfillServiceTest.java`
- `src/test/java/dev/jlm/leadshunter/geo/MunicipioServiceTest.java`

**Modificados:**

- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `refinamento.md`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaService.java`
- `src/main/java/dev/jlm/leadshunter/lead/Lead.java`
- `src/main/java/dev/jlm/leadshunter/lead/LeadRepository.java`
- `src/test/java/dev/jlm/leadshunter/LeadsHunterApplicationTests.java`
- `src/test/java/dev/jlm/leadshunter/MvpFlowIntegrationTest.java`
- `src/test/java/dev/jlm/leadshunter/busca/BuscaServiceJpaIntegrationTest.java`
- `src/test/java/dev/jlm/leadshunter/busca/BuscaServiceTest.java`

---

## 42. Exposição de IDHM e camada geográfica municipal — 05/09/2026

Os contratos HTTP dos leads passaram a expor código IBGE, município, UF, IDHM e ano de referência, inclusive na listagem paginada. As exportações CSV e Excel agora incluem UF, município e IDHM.

Também foi criado um endpoint GeoJSON que recebe os limites visíveis do mapa, valida as quatro coordenadas e retorna somente os municípios cujos envelopes intersectam essa região. A resposta preserva as geometrias simplificadas do dataset e não realiza chamadas externas.

Na revisão final, o endpoint recebeu um teto de 1.500 municípios por resposta e cache HTTP público por 24 horas. As estruturas de Polygon e MultiPolygon foram tipadas separadamente, e os testes passaram a cobrir o parser de bbox, uma geometria MultiPolygon, região sem municípios e rejeição de consultas excessivamente amplas.

### Arquivos envolvidos

**Criados:**

- `src/main/java/dev/jlm/leadshunter/geo/BboxInvalidoException.java`
- `src/main/java/dev/jlm/leadshunter/geo/GeografiaController.java`
- `src/main/java/dev/jlm/leadshunter/geo/MunicipioBboxParser.java`
- `src/main/java/dev/jlm/leadshunter/geo/MunicipiosGeoJsonResponse.java`
- `src/test/java/dev/jlm/leadshunter/geo/GeografiaControllerTest.java`
- `src/test/java/dev/jlm/leadshunter/geo/MunicipioBboxParserTest.java`

**Modificados:**

- `API.md`
- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `refinamento.md`
- `src/main/java/dev/jlm/leadshunter/config/ApiExceptionHandler.java`
- `src/main/java/dev/jlm/leadshunter/exportacao/ExportService.java`
- `src/main/java/dev/jlm/leadshunter/geo/MunicipioDataset.java`
- `src/main/java/dev/jlm/leadshunter/geo/MunicipioService.java`
- `src/main/java/dev/jlm/leadshunter/lead/LeadResponse.java`
- `src/test/java/dev/jlm/leadshunter/exportacao/ExportServiceTest.java`
- `src/test/java/dev/jlm/leadshunter/geo/MunicipioServiceTest.java`
- `src/test/java/dev/jlm/leadshunter/lead/LeadControllerTest.java`
- `src/test/java/dev/jlm/leadshunter/lead/LeadServiceTest.java`

---

## 43. IDHM no card e no detalhe do lead — 05/09/2026

O frontend passou a consumir os dados geográficos do lead e a apresentar o IDHM no fluxo do Kanban. O card mostra um badge compacto com o índice e a UF, enquanto o drawer informa Município/UF, faixa de desenvolvimento e ano de referência na seção do estabelecimento. Leads sem o dado continuam com a interface neutra, sem valor substituto.

Também foi criado um util compartilhado com a classificação PNUD, as cores das cinco faixas e a formatação do índice, preparando a mesma regra para a futura camada do mapa.

### Arquivos envolvidos

**Criados:**

- `frontend/src/app/shared/utils/idhm.ts`
- `frontend/src/app/shared/utils/idhm.spec.ts`

**Modificados:**

- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `refinamento.md`
- `frontend/src/app/features/kanban/lead-card.html`
- `frontend/src/app/features/kanban/lead-card.scss`
- `frontend/src/app/features/kanban/lead-card.spec.ts`
- `frontend/src/app/features/kanban/lead-card.ts`
- `frontend/src/app/features/kanban/lead-detalhe.html`
- `frontend/src/app/features/kanban/lead-detalhe.spec.ts`
- `frontend/src/app/features/kanban/lead-detalhe.ts`
- `frontend/src/app/shared/models/lead.model.ts`

---

## 44. Camada coroplética de IDHM no mapa — 07/09/2026

O mapa da Busca passou a oferecer uma camada opcional que pinta os municípios visíveis de acordo com as cinco faixas de IDHM. O controle inclui estados de carregamento e erro, legenda com a referência da base e popup acessível com município, UF, valor e classificação.

O carregamento consulta o endpoint geográfico somente quando a camada está ativa. Movimentos consecutivos do mapa são agrupados, células já carregadas são reutilizadas por um cache limitado e requisições obsoletas são canceladas. Os polígonos permanecem abaixo do círculo e do marcador e são removidos com timers e listeners ao desligar a camada ou destruir o mapa.

### Arquivos envolvidos

**Criados:**

- `frontend/src/app/core/api/geografia-api.spec.ts`
- `frontend/src/app/core/api/geografia-api.ts`
- `frontend/src/app/shared/models/geografia.model.ts`

**Modificados:**

- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `refinamento.md`
- `frontend/src/app/core/api/api-routes.ts`
- `frontend/src/app/features/busca/mapa-busca.html`
- `frontend/src/app/features/busca/mapa-busca.scss`
- `frontend/src/app/features/busca/mapa-busca.spec.ts`
- `frontend/src/app/features/busca/mapa-busca.ts`
- `frontend/src/app/shared/utils/idhm.spec.ts`
- `frontend/src/app/shared/utils/idhm.ts`
- `frontend/src/styles.scss`

## 45. Fechamento integrado da feature IDHM — 08/09/2026

A entrega de IDHM foi encerrada após a validação conjunta do enriquecimento municipal, dos contratos de lead, das exportações e da camada coroplética. O fluxo controlado confirmou Vitória/ES e Curitiba/PR desde a busca até os badges e detalhes do Kanban, além dos arquivos CSV/XLSX e dos polígonos e popups do mapa.

A documentação passou a registrar o estado final das seis sprints, o uso exclusivamente offline do dataset em runtime e a ausência de pendências dentro deste refinamento.

### Arquivos envolvidos

**Modificados:**

- `API.md`
- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `refinamento.md`

---

## 46. Acessibilidade do badge de IDHM — 08/09/2026

O badge de IDHM do card do Kanban passou a expor a classificação de forma semântica e textual. O elemento recebeu um papel acessível e mantém a faixa em texto visualmente oculto, evitando que a identificação dependa somente da cor ou de um `aria-label` em elemento genérico.

### Arquivos envolvidos

**Modificados:**

- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `refinamento.md`
- `frontend/src/app/features/kanban/lead-card.html`
- `frontend/src/app/features/kanban/lead-card.scss`
- `frontend/src/app/features/kanban/lead-card.spec.ts`

---

## 47. Refinamento de interação da camada IDHM — 08/09/2026

A camada coroplética passou a orientar o usuário quando o viewport é amplo demais para o limite seguro do endpoint, evitando consultas rejeitadas. Os municípios não propagam clique para a seleção do centro, a legenda deixa de bloquear o mapa e o contador identifica a região carregada. A navegação por teclado continua disponível em cada município da camada opt-in, incluindo Enter e Espaço.

### Arquivos envolvidos

**Modificados:**

- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `refinamento.md`
- `frontend/src/app/features/busca/mapa-busca.html`
- `frontend/src/app/features/busca/mapa-busca.scss`
- `frontend/src/app/features/busca/mapa-busca.spec.ts`
- `frontend/src/app/features/busca/mapa-busca.ts`

---

## 48. Parametrização de credenciais da aplicação — 08/09/2026

A configuração local deixou de conter valores sensíveis em texto puro. A senha do banco e a chave da Google Places passaram a ser fornecidas por variáveis de ambiente, evitando que credenciais sejam adicionadas acidentalmente ao repositório.

### Arquivos envolvidos

**Modificados:**

- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `refinamento.md`
- `src/main/resources/application.yml`

---

## 49. Blacklist cadastrável de nomes — 08/09/2026

Foi entregue a gestão completa de nomes e trechos que não devem virar leads. Os termos são persistidos com unicidade normalizada, podem ser listados, cadastrados e removidos pela API e por uma área própria do frontend.

Durante a busca, os bloqueios são aplicados por substring sem acentos e sem diferença entre maiúsculas e minúsculas, depois da deduplicação por `googlePlaceId`. Estabelecimentos bloqueados não criam `Lead` nem `BuscaLead`; a resposta imediata informa a quantidade única ignorada sem alterar o total bruto retornado pela Google ou apagar leads antigos.

O fechamento incluiu testes de serviço, controller, persistência, interface e um fluxo HTTP integrado com `Supermercados BH`, além de smoke de navegador e revisão de acessibilidade em desktop e mobile.

### Arquivos envolvidos

**Criados:**

- `src/main/java/dev/jlm/leadshunter/bloqueio/NomeBloqueado.java`
- `src/main/java/dev/jlm/leadshunter/bloqueio/NomeBloqueadoController.java`
- `src/main/java/dev/jlm/leadshunter/bloqueio/NomeBloqueadoDuplicadoException.java`
- `src/main/java/dev/jlm/leadshunter/bloqueio/NomeBloqueadoInvalidoException.java`
- `src/main/java/dev/jlm/leadshunter/bloqueio/NomeBloqueadoNaoEncontradoException.java`
- `src/main/java/dev/jlm/leadshunter/bloqueio/NomeBloqueadoRepository.java`
- `src/main/java/dev/jlm/leadshunter/bloqueio/NomeBloqueadoRequest.java`
- `src/main/java/dev/jlm/leadshunter/bloqueio/NomeBloqueadoResponse.java`
- `src/main/java/dev/jlm/leadshunter/bloqueio/NomeBloqueadoService.java`
- `src/main/resources/db/migration/V3__criar_nome_bloqueado.sql`
- `src/test/java/dev/jlm/leadshunter/BlacklistFlowIntegrationTest.java`
- `src/test/java/dev/jlm/leadshunter/bloqueio/NomeBloqueadoControllerTest.java`
- `src/test/java/dev/jlm/leadshunter/bloqueio/NomeBloqueadoRepositoryTest.java`
- `src/test/java/dev/jlm/leadshunter/bloqueio/NomeBloqueadoServiceTest.java`
- `frontend/src/app/core/api/bloqueio-api.spec.ts`
- `frontend/src/app/core/api/bloqueio-api.ts`
- `frontend/src/app/features/bloqueios/bloqueios-page.html`
- `frontend/src/app/features/bloqueios/bloqueios-page.scss`
- `frontend/src/app/features/bloqueios/bloqueios-page.spec.ts`
- `frontend/src/app/features/bloqueios/bloqueios-page.ts`
- `frontend/src/app/shared/models/bloqueio.model.ts`

**Modificados:**

- `API.md`
- `HISTORICO_IMPLEMENTACOES.md`
- `fluxo.md`
- `refinamento-blacklist-nomes.md`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaResponse.java`
- `src/main/java/dev/jlm/leadshunter/busca/BuscaService.java`
- `src/main/java/dev/jlm/leadshunter/config/ApiExceptionHandler.java`
- `src/main/resources/application.yml`
- `src/test/java/dev/jlm/leadshunter/busca/BuscaControllerTest.java`
- `src/test/java/dev/jlm/leadshunter/busca/BuscaServiceJpaIntegrationTest.java`
- `src/test/java/dev/jlm/leadshunter/busca/BuscaServiceTest.java`
- `frontend/scripts/mvp-flow-smoke.mjs`
- `frontend/src/app/app.routes.ts`
- `frontend/src/app/app.spec.ts`
- `frontend/src/app/app.ts`
- `frontend/src/app/core/api/api-routes.ts`
- `frontend/src/app/features/busca/busca-resultados.html`
- `frontend/src/app/features/busca/busca-resultados.scss`
- `frontend/src/app/features/busca/busca-resultados.spec.ts`
- `frontend/src/app/shared/models/busca.model.ts`
