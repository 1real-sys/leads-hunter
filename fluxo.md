# Fluxo interno do Leads Hunter

Este documento explica como o projeto funciona **no código atual**. Para formatos de requisição e resposta, consulte [API.md](API.md); para a sequência de entregas, consulte [HISTORICO_IMPLEMENTACOES.md](HISTORICO_IMPLEMENTACOES.md). O fluxo descrito aqui considera a aplicação local, com uma pessoa usando o Angular e o backend Spring Boot.

## Componentes e dados

```text
Angular (frontend/)
    │ HTTP /api
    ▼
Controllers Spring (src/main/java/dev/jlm/leadshunter/)
    │ validação de entrada e resposta HTTP
    ▼
Services de busca, leads, CNPJ, geografia e enriquecimento
    ├── Google Places API (New), pelo PlacesApiClient
    ├── Brave Search API, quando a ação Buscar informações o permite
    ├── dataset municipal/IDHM local
    ├── páginas públicas candidatas ou do site oficial, com limites próprios
    └── repositories JPA → MySQL local (schema controlado por Flyway)
```

O frontend usa as rotas `busca`, `kanban`, `historico`, `historico/:id` e `bloqueios`. Os componentes de `frontend/src/app/features/` chamam os serviços em `frontend/src/app/core/api/`; os modelos de `frontend/src/app/shared/models/` representam os contratos HTTP. Em desenvolvimento, o proxy encaminha `/api` ao backend local. Não há autenticação, multiusuário ou deploy na aplicação atual.

`Busca` guarda os parâmetros e o resumo de cada execução. `Lead` guarda um estabelecimento único, deduplicado por `googlePlaceId`, com dados externos, classificação e dados comerciais atuais. `BuscaLead` relaciona os dois em N:N e guarda `scoreNaBusca` e `temperaturaNaBusca`: esses dois valores registram a classificação **daquela execução**, mesmo que o `Lead` mude depois. As execuções das ações de informações e e-mails têm tabelas próprias para status, progresso e resultado. O schema evolui pelas migrations `V1` a `V12`; o Hibernate valida o schema (`ddl-auto: validate`).

## 1. Criar uma busca

```text
Mapa + formulário Angular
    → POST /api/buscas
    → BuscaController
    → BuscaService
    → BuscaPlacesCache
         ├── hit: reutiliza a resposta externa
         └── miss: PlacesApiClient → PlacesRateLimiter → Google Places Nearby Search
    → deduplicação e bloqueios
    → atualização dos Leads, geografia, CNPJ e scoring
    → Busca + BuscaLead no MySQL
    → HTTP 201 com resumo e leads encontrados
```

1. `BuscaPage` mantém coordenadas e raio sincronizados entre mapa e formulário. `BuscaRequest` valida coordenadas, categorias e raio de até 20 km antes de `BuscaService.criar` processar a requisição.
2. `BuscaCacheKey` usa coordenadas arredondadas, raio e categorias normalizadas. `BuscaPlacesCache` guarda por padrão até 100 respostas da Google durante 30 minutos. Um hit evita nova chamada externa, **mas ainda cria uma nova execução `Busca` e seus vínculos**.
3. Em um miss, `PlacesApiClient` monta o Nearby Search a partir das categorias, solicita os campos necessários e passa pelo `PlacesRateLimiter` antes da chamada real. `PlacesResponseMapper` traduz a resposta da Google para o modelo interno, incluindo telefone, `websiteUri`, coordenadas, endereço estruturado, avaliação e situação operacional quando vierem na resposta.
4. `BuscaService` salva em `Busca` o total bruto retornado. Depois remove resultados repetidos pelo `googlePlaceId` e consulta os termos de `NomeBloqueadoService`. Um nome bloqueado não cria nem atualiza `Lead` ou `BuscaLead`; a resposta imediata informa `totalBloqueados` separadamente.
5. Para cada resultado permitido, o serviço encontra o `Lead` pelo `googlePlaceId` ou cria um com status `NOVO`. Atualiza os dados externos presentes, inclusive telefone brasileiro normalizado e endereço estruturado. Ausência de telefone, componente de endereço ou site na nova resposta não apaga um valor conhecido. Se um novo `websiteUri` mudar o host do site, o e-mail anteriormente capturado e seus metadados são limpos.
6. Com coordenadas, `MunicipioService` localiza município/UF e IDHM no dataset offline. Sem correspondência, os campos geográficos ficam nulos. Em seguida, `CnpjService` tenta correspondência na base local para lead sem CNPJ; um CNPJ já preenchido é revalidado somente quando muda a competência ativa do município. A política de correspondência por endereço exato é fechada por padrão e depende de allowlist válida; fora dela, permanece o caminho legado. Correspondência insuficiente, ambígua ou truncada não preenche CNPJ. Uma base nova que não confirme o mesmo CNPJ limpa o enriquecimento antigo, sem substituí-lo silenciosamente por outro.
7. `ScoringService` calcula de 0 a 95 pontos por categoria, telefone, avaliações e funcionamento e classifica `FRIO`, `MORNO` ou `QUENTE`. `status`, `observacoes` e `ultimoContatoEm` definidos pelo usuário são preservados quando o lead reaparece. O score atual é salvo no `Lead`; o score e a temperatura da execução são copiados para `BuscaLead`.
8. A criação ocorre em uma transação. Um estabelecimento retornado sem `googlePlaceId` interrompe e reverte a gravação. `BuscaResponse` devolve os IDs dos leads persistidos e o resumo da execução.

A busca comum não executa automaticamente as ações **Buscar CNPJ**, **Buscar informações** ou **Buscar e-mails** do Histórico. Ela também não envia mensagens pelo WhatsApp.

## 2. Consultar e administrar leads

`LeadController` expõe `GET /api/leads`, `GET /api/leads/pagina`, `GET /api/leads/{id}` e `PATCH /api/leads/{id}`. `LeadService` aplica filtros combináveis de `status`, `categoria`, `temperatura` e `buscaId`. Quando há `buscaId`, o vínculo `BuscaLead` restringe quais leads entram na consulta; os campos exibidos continuam sendo os **valores atuais do Lead**. A ordenação é por score decrescente, nome e ID. A rota paginada exige status e limita cada página a 25 registros.

O Kanban consulta uma página por etapa, com totais e páginas independentes. A seleção de **Endereço base de pesquisa** usa o ID de uma execução do Histórico como `buscaId`; a opção padrão reúne todas as buscas. Depois de mover um card, a interface envia `PATCH /api/leads/{id}` e recarrega as colunas afetadas. O mesmo endpoint atualiza observações e último contato no detalhe. Campos omitidos no PATCH são preservados; não há recálculo de score por mudança manual de status. Limpar o campo de último contato na interface não remove a data persistida pelo contrato atual.

`LeadResponse` expõe dados do estabelecimento, classificação, geografia, CNPJ, site, e-mail e dados comerciais sem retornar a entidade JPA diretamente. `WhatsAppLinkGenerator` cria `https://wa.me/55...` somente para telefone brasileiro normalizado válido. O Kanban, o Histórico e as exportações apenas apresentam esse link para abertura manual; não há envio automático.

## 3. Consultar o Histórico

`GET /api/buscas` lê os resumos persistidos da mais recente para a mais antiga. `GET /api/buscas/{id}` lê a `Busca` e seus vínculos `BuscaLead`; não repete consulta à Google nem ao cache. No detalhe, score e temperatura vêm do snapshot da execução. Status, observações, último contato, CNPJ, razão social, site e e-mail vêm do estado **atual** de cada `Lead`. Assim, uma atualização comercial ou um enriquecimento posterior aparece também em buscas antigas sem alterar o score histórico.

O detalhe do Histórico oferece três ações explícitas: **Buscar CNPJ**, **Buscar informações** e **Buscar e-mails**. Cada uma opera somente sobre os leads vinculados à busca selecionada. As ações têm regras e persistência próprias descritas abaixo.

### Buscar CNPJ

`POST /api/buscas/{id}/cnpj` chama `BuscaCnpjService`. Ele percorre os vínculos da execução, ignora leads que já têm CNPJ e tenta correspondência local para os demais, na mesma transação. A resposta informa total, ignorados, encontrados e sem correspondência. A base mensal de CNPJ é carregada localmente pelo pipeline em `tools/cnpj/`; a aplicação não consulta a Receita em runtime. O botão não altera dados comerciais, score nem snapshots históricos.

### Buscar informações públicas

`POST /api/buscas/{id}/informacoes` cria ou recupera uma execução persistida e responde `202`; `GET` na mesma rota consulta seu progresso e responde `204` quando ainda não existe execução. `BuscaInformacoesExecucaoService` limita o volume por execução, impede duas execuções ativas da mesma busca e reserva o worker após o commit. O worker processa os leads em sequência, fora de uma transação longa; o resultado de cada lead e os contadores são gravados em transações curtas. Uma execução interrompida não retoma automaticamente na inicialização seguinte.

`BuscaInformacoesService` ignora um lead cujo bloco automático de observações já contém Instagram e site válidos. Para os demais, `PesquisaWebInternaService` tenta primeiro o site oficial conhecido (`websiteUri`) e os links públicos extraídos dele. Se não houver confirmação e a opção **Brave Search** estiver ativa, consulta a API oficial do Brave. O teto é de **três consultas ao Brave e três páginas candidatas por lead**, contando o site oficial quando ele é aberto. As páginas candidatas são o próprio site ou perfil público do Instagram, usadas somente para confirmar telefone, endereço ou CNPJ; o fluxo não navega por páginas de resultados de buscadores.

`ClassificadorUrlService` avalia identidade e conflitos entre candidatos. Nome ou handle parecido, sozinho, não confirma um link. `LeitorPaginaCandidata` aceita apenas destinos HTTP/HTTPS públicos, não segue redirecionamentos e impõe timeout e limite de leitura. Páginas inacessíveis e falhas técnicas não viram ausência conclusiva. `FormatadorObservacoesPesquisa` atualiza somente o bloco automático de links ou ausência e preserva o texto comercial fora dele. A ação não altera status, último contato, CNPJ, score nem snapshots.

### Buscar e-mails

`POST /api/buscas/{id}/emails` inicia ou recupera a execução persistida; `GET` acompanha seu progresso. `BuscaEmailExecucaoService` impede duas execuções ativas da mesma busca e agenda o worker após o commit. `BuscaEmailProcessamento` percorre os leads vinculados, ignora quem já tem e-mail e registra o resultado de cada lead em transação curta.

`EmailLeadService` lê somente o `websiteUri` oficial e, se necessário, uma página de contato do **mesmo host**: no máximo duas páginas por lead. O conteúdo precisa confirmar a identidade do estabelecimento por telefone, endereço numerado ou CNPJ, sem conflito. Apenas e-mail do host normalizado do site ou de subdomínio é elegível; endereços externos descartados entram somente em um contador agregado. O e-mail aprovado é gravado com data e host de origem. Não há envio automático, consulta ao Brave ou à Google nessa ação. Falha de leitura e omissão do site em nova resposta Places não apagam um e-mail válido existente.

## 4. Bloqueios, geografia e exportação

A tela de Bloqueios usa `GET`, `POST` e `DELETE /api/bloqueios`. `NomeBloqueadoService` normaliza termos, persiste-os com unicidade e os consulta a cada nova busca. O filtro atua **antes** da persistência dos resultados daquela busca; cadastrar um termo não remove leads históricos já salvos.

`GET /api/geografia/municipios?bbox=...` consulta o dataset municipal/IDHM incluído no backend, limita o recorte e devolve GeoJSON para a camada opcional do mapa. A localização de um lead também usa esse dataset, sem download ou API geográfica no runtime. O backfill de geografia só roda por ativação explícita.

O JSON municipal é um artefato congelado: o backend valida seu SHA-256 antes de ler os 5.570 municípios e seus dados geográficos. O Git preserva os bytes do arquivo em Windows e Linux pela regra específica em `.gitattributes`, inclusive quando `core.autocrlf=true`; o build distribui o mesmo recurso sem conversão de fins de linha.

`GET /api/exportacao/leads.csv` e `GET /api/exportacao/leads.xlsx` passam por `ExportController` e `ExportService`. Aceitam `status`, `categoria`, `temperatura` e `buscaId`, reutilizando os filtros e a ordenação de `LeadService`. No Kanban, os arquivos usam os filtros **da última consulta aplicada**, mesmo que o formulário já tenha uma seleção nova ainda não aplicada. CSV é UTF-8 com escape de conteúdo; XLSX é produzido com Apache POI. Os arquivos incluem os campos atuais do lead, como geografia, CNPJ, site, e-mail e link manual de WhatsApp.

## 5. Configuração, erros e limites conhecidos

`application.yml` lê credenciais do MySQL e chaves externas por `DB_USERNAME`, `DB_PASSWORD`, `GOOGLE_PLACES_API_KEY` e `BRAVE_SEARCH_API_KEY`. O arquivo local `.env` é ignorado pelo Git e precisa ser carregado no ambiente antes de iniciar o Spring Boot; `.env.example` é o modelo sem segredos. Google Places é necessário para uma busca externa real. Brave é opcional para a ação de informações. O scraper legado de motores de busca permanece desativado na configuração padrão.

`ApiExceptionHandler` transforma erros de validação, recurso inexistente, limite, integração externa e falhas inesperadas no contrato `ApiErrorResponse`, sem expor resposta bruta externa ou credenciais. Falhas da Google não são armazenadas no cache; uma requisição que falha antes de persistir não cria histórico.

Pontos ainda abertos no estado atual:

- A promoção da correspondência CNPJ por endereço exato depende da revisão manual integral do diagnóstico; a política de produção permanece fechada sem allowlist aprovada.
- A precisão e a cobertura amplas da pesquisa de site/Instagram ainda dependem de uma amostra rotulada maior. Alguns sites públicos bloqueiam a leitura pelo backend; uma página bloqueada não é contornada automaticamente.
- A busca de e-mails tem validação automatizada, mas ainda não há medição de captura em uma amostra real de sites.
- IDHM e CNPJ não entram no `ScoringService`; cadastrar um bloqueio não faz limpeza retroativa de leads.

Para executar o projeto e configurar dependências locais, veja [README.md](README.md)
