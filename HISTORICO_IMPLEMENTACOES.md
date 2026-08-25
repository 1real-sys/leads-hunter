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
