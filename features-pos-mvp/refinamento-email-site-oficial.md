# Refinamento — E-mail do lead a partir do site oficial

Planejamento em sprints para capturar o e-mail de contato do estabelecimento reaproveitando o que já existe: o `Lead.website` (coluna V7), o `LeitorPaginaCandidata` com anti-SSRF e o padrão de execução assíncrona persistida da "Buscar informações" (`PesquisaInformacoesExecucao`).

## Objetivo

Fechar o "kit de contato" do lead — **telefone + WhatsApp manual + site + e-mail** — sem novo provedor pago e sem scraping de terceiros. O e-mail passa a ser atributo persistido do `Lead`, exibido no frontend e incluído na exportação.

O Google Places não devolve e-mail. A única fonte confiável é o **site oficial do próprio estabelecimento**, que já capturamos em `Lead.website`.

## Regras

Não faça o código mais fácil, faça o de maior qualidade e melhor manutenção futura.

## Pré-requisito de governança

O `AGENTS.md` foi ajustado para autorizar explicitamente a leitura do site oficial do lead pela ação `Buscar e-mails`, após confirmação de identidade, com no máximo **duas páginas do mesmo host por execução** e gravação somente de endereço do domínio próprio. Nenhum outro limite de acesso (sem redirect, host público, sem scraping de buscador) foi alterado.

## Diagnóstico

- `Lead.website` existe desde a V7 e é preenchido com `places.websiteUri`; a migração mais recente é a **V10**.
- `PesquisaWebInternaService` já canonicaliza o site oficial e chama `LeitorPaginaCandidata.lerPagina(url)`, mas usa apenas `texto` e `links`.
- `PaginaLida` é `(texto, links)`; o leitor **não extrai e-mails** hoje.
- `LeitorPaginaCandidata` já garante: sem redirect, host público (anti-SSRF), timeout, `Accept` restrito a HTML/texto/JSON e corte por `maxBytes`.
- Não existe coluna `email`, campo em `Lead`, exposição em `LeadResponse`/`BuscaDetalheResponse.LeadHistoricoResponse` nem coluna em `ExportService.COLUNAS`.
- `POST /api/buscas/{id}/cnpj` e `POST/GET /api/buscas/{id}/informacoes` são os padrões existentes para ação explícita por busca (síncrona no primeiro caso, assíncrona persistida no segundo).

### Fato de custo

A extração usa apenas `HttpClient` do JDK contra o site do próprio lead. **Não consome Google Places nem Brave** e não aumenta chamadas pagas.

## Decisões fechadas (Q1–Q10)

1. **Permissão:** leitura do site oficial para capturar e-mail é autorizada no `AGENTS.md`, mantendo os limites atuais.
2. **Identidade:** `websiteUri` sozinho **não** comprova identidade; exige confirmação do estabelecimento antes de gravar.
3. **Domínio externo:** nesta versão só é gravado e-mail do domínio próprio; a perda é medida antes de ampliar.
4. **Gatilho:** somente pelo botão **Buscar e-mails**, por ação explícita sobre uma busca do histórico.
5. **Evidência combinada:** identidade pode ser confirmada somando as duas páginas do **mesmo host verificado**; qualquer dado conflitante veta a captura.
6. **Domínio próprio:** vale o host normalizado do site e seus subdomínios; o domínio-pai (site em subdomínio, e-mail na raiz) fica fora nesta versão.
7. **Mudança de site:** a origem do e-mail (`email_origem_host`) é registrada; se o host do `website` mudar, `email`, `email_capturado_em` e `email_origem_host` são **limpos** para nova captura. Falha de leitura **nunca** limpa nem grava ausência.
8. **Operação:** assíncrona, com execução persistida, progresso consultado pela tela e **somente uma execução ativa por busca**.
9. **Cobertura:** contar **leads** (não endereços) com contato descartado por domínio externo, como campo agregado no progresso, sem guardar os endereços rejeitados. Distinguir encontrado, sem e-mail elegível e falha técnica.
10. **Repetição:** até duas páginas por lead **em cada execução**; lead com e-mail é ignorado; nova tentativa exige novo clique, sem repetição automática.

## Fluxo alvo

```text
POST /api/buscas/{id}/emails        GET /api/buscas/{id}/emails
        |                                     ^
        v                                     | (progresso/polling)
BuscaEmailExecucaoService  ---- enfileira ----> BuscaEmailWorker (1 ativa + 1 em espera)
        |                                              |
        |  (uma ativa por busca, unicidade)            v
        |                                    EmailLeadService.extrair(lead)
        |                                         |
        |                                         +--> segmenta o website
        |                                         |
        |                                         +--> pagina 1: home do site oficial   [1 página]
        |                                         |      +--> texto, links e e-mails (mailto:/regex)
        |                                         |
        |                                         +--> confirma identidade pela evidência das páginas
        |                                         |      (telefone, endereço com número ou CNPJ)
        |                                         |      conflito -> veta
        |                                         |
        |                                         +--> sem e-mail próprio e sem conflito?
        |                                         |      sim -> 1 link interno de contato, mesma host
        |                                         |             pagina 2 (dentro do teto)      [2ª página]
        |                                         |
        |                                         +--> só aceita e-mail do host próprio/subdomínio
        |                                         |
        |                                         v
        |                                   e-mail próprio (ou vazio)
        v
persiste email + email_capturado_em + email_origem_host  (transação curta)
```

## Sprints

### EMAIL-00 — Persistência e contratos

**Status: CONCLUÍDA.**

**Objetivo:** criar o campo no domínio e expô-lo em todos os contratos, sem capturar ainda.

**Entregáveis:**

- Migration **V11** (`V11__adicionar_email_lead.sql`):
  - `ALTER TABLE leads ADD COLUMN email VARCHAR(320) NULL;`
  - `ALTER TABLE leads ADD COLUMN email_capturado_em DATETIME NULL;`
  - `ALTER TABLE leads ADD COLUMN email_origem_host VARCHAR(255) NULL;`
- `Lead`: campos `email`, `emailCapturadoEm`, `emailOrigemHost`.
- `LeadResponse`: componente `email` aditivo (mantendo os construtores de compatibilidade usados nos testes).
- `BuscaDetalheResponse.LeadHistoricoResponse`: componente `email`.
- `ExportService.COLUNAS` + linha de valores: `email` em posição coerente com `website`.
- Frontend `lead.model.ts` e `busca.model.ts`: `email?: string | null`.
- `BuscaService`: ao atualizar dados externos, se `website` mudar de host, limpar `email`, `emailCapturadoEm` e `emailOrigemHost`. O Google nunca preenche nem apaga e-mail por omissão.

**Critérios de aceite:**

- Schema na versão 11 com `ddl-auto: validate` verde; nenhum lead existente alterado.
- `email` nulo não quebra contratos nem exportação.
- Mudança de host do site invalida o e-mail; pequenas variações de mesma host não.

**Validação:**

```bash
./mvnw -Dtest=LeadControllerTest,ExportServiceTest,BuscaControllerTest,BuscaServiceTest test
./mvnw -DskipTests package
cd frontend && npm test -- --watch=false && npm run build
```

### EMAIL-01 — Leitura e extração com confirmação de identidade

**Status: CONCLUÍDA.**

**Objetivo:** o leitor reconhece e-mails e um serviço dedicado decide, com confirmação de identidade, qual persistir.

**Entregáveis:**

- `PaginaLida`: novo componente `emails` (`List<String>`), preservando o construtor `(texto, links)`.
- `LeitorPaginaCandidata`:
  - extrair `a[href^=mailto:]` (endereço antes do `?`) e e-mails do texto/JSON já anexados, com regex conservadora;
  - normalizar (trim, minúsculas, remover pontuação final), sem propagar falha de parsing.
- Novo `EmailLeadService`:
  - se `website` ausente/inválido, retorna vazio;
  - lê a **home** do site oficial; se preciso e sem conflito, segue **um** link interno de contato (`contato`, `contact`, `fale-conosco`, `atendimento`) no mesmo host — no máximo 2 páginas por execução, sem redirect;
  - confirma identidade somando evidências das páginas do mesmo host, reaproveitando as primitivas existentes (`ConfirmacaoPerfilInstagram.telefoneNacional`, dígitos de CNPJ e `AnalisadorEnderecoPesquisa` para endereço com número). **Qualquer conflito veta**;
  - aceita apenas e-mail do host normalizado do site ou de seus subdomínios; descarta `no-reply`/`noreply`, domínios de exemplo/serviço (`example.com`, `sentry.io`, `wixpress`, provedores de site), ruído de imagem (`@2x`, `.png`) e endereços inválidos/maiores que 320 caracteres;
  - prefere papéis (`contato@`, `comercial@`, `vendas@`, `atendimento@`) quando do domínio próprio;
  - retorna também um indicador de "houve contato externo descartado" (para o contador de cobertura), sem guardar o endereço.
- Persistência: grava `email` + `emailCapturadoEm` + `emailOrigemHost` só quando há candidato válido; dados comerciais, score e snapshots intactos.

**Critérios de aceite:**

- Nenhuma requisição ao Brave ou ao Google.
- Sem confirmação de identidade ou com conflito, nada é gravado.
- Domínio de terceiros na página é descartado e contado como perda.
- No máximo 2 páginas por lead por execução, sem redirect; falha/bloqueio não apaga e-mail existente nem grava ausência.

**Validação:**

```bash
./mvnw -Dtest=LeitorPaginaCandidataTest,EmailLeadServiceTest test
```

### EMAIL-02 — Execução assíncrona e endpoints

**Status: CONCLUÍDA.**

**Objetivo:** rodar o lote como trabalho persistido, acompanhado pela tela, sem conexão HTTP aberta durante toda a operação.

**Entregáveis:**

- Migration **V12** (`V12__criar_busca_email_execucao.sql`), no molde da V6:
  - colunas: `busca_id`, `status`, `criado_em`, `iniciado_em`, `atualizado_em`, `terminado_em`, `total_leads`, `ignorados_ja_com_email`, `ignorados_sem_site`, `processados`, `encontrados`, `sem_email_elegivel`, `descartados_dominio_externo`, `falhas`, `erro_codigo`, `erro_mensagem`;
  - `busca_ativa_id` gerado + `UNIQUE` (só uma ativa por busca), `CHECK` dos status e do progresso.
- Entidade `BuscaEmailExecucao`, repository e worker sequencial (uma execução em processamento + uma em espera), com marcação de execuções ativas como interrompidas no startup, no padrão da INFO.
- `EmailLeadService` por lead, com persistência em transação curta por resultado.
- `POST /api/buscas/{id}/emails`: inicia e retorna `202` com o DTO; pedidos concorrentes devolvem a execução ativa.
- `GET /api/buscas/{id}/emails`: devolve a execução ativa ou a mais recente; busca existente sem execução retorna `204`. Busca inexistente `404`; ID não numérico `400`.
- `BuscaEmailResponse`:

```json
{
  "id": 7,
  "buscaId": 10,
  "status": "CONCLUIDA",
  "totalLeads": 18,
  "progresso": 18,
  "ignoradosJaComEmail": 3,
  "ignoradosSemSite": 4,
  "processados": 11,
  "encontrados": 6,
  "semEmailElegivel": 4,
  "descartadosDominioExterno": 1,
  "falhas": 1,
  "erroCodigo": null,
  "erroMensagem": null
}
```

Equações: `totalLeads = ignoradosJaComEmail + ignoradosSemSite + processados` e `processados = encontrados + semEmailElegivel + falhas`. `descartadosDominioExterno` conta **leads** com ao menos um endereço externo descartado (anotação independente, sem guardar endereços). `progresso` é a soma dos contabilizados.

- Contrato de erro existente; sem stack trace, URL bruta ou endereço rejeitado no JSON.

**Critérios de aceite:**

- Não cria busca, não altera score/snapshot/observações e não chama APIs externas além da leitura do site.
- Repetir a ação ignora leads já com e-mail; leads sem e-mail podem ser tentados de novo em nova execução.
- Somente uma execução ativa por busca, garantida no banco.

**Validação:**

```bash
./mvnw -Dtest=BuscaEmailExecucaoServiceTest,BuscaEmailWorkerTest,BuscaEmailControllerTest test
```

### EMAIL-03 — Frontend, exportação e validação integrada

**Status: CONCLUÍDA.**

**Objetivo:** exibir o e-mail, abrir o `mailto:` manual, acompanhar o progresso e fechar com prova integrada.

**Entregáveis:**

- `historico-detalhe-page`: botão **Buscar e-mails** ao lado de **Buscar CNPJ**, com acompanhamento por polling (mesmo padrão da INFO), estados de carregando/resultado/erro e recarga do detalhe.
- `lead-detalhe` (drawer do Kanban) e `historico-detalhe-page`: exibir `mailto:` manual quando houver e-mail; rótulo neutro quando ausente.
- `api` do frontend: `iniciarEmails(buscaId)` e `consultarEmails(buscaId)` tipados.
- Exportação CSV/XLSX: conferir a coluna `email` e a neutralização de fórmula já aplicada a texto externo.
- Testes de componente/contrato para exibição, ausência, polling e botão.
- Atualizar `fluxo.md`, `HISTORICO_IMPLEMENTACOES.md`, `API.md` e este arquivo.

**Critérios de aceite:**

- E-mail aparece quando o backend entrega; ausente não mostra valor falso.
- `mailto:` é apenas link manual; nenhum envio automático.
- Exportação contém a coluna nos dois formatos.
- Suíte backend e frontend verdes e build sem warnings.

**Validação:**

```bash
./mvnw test
./mvnw -DskipTests package
cd frontend && npm test -- --watch=false && npm run build && npm run e2e:smoke
```

## Resultado da implementação — 23/09/2026

As sprints EMAIL-00 a EMAIL-03 foram implementadas. Flyway chegou à V12 em banco MySQL temporário isolado, com `ddl-auto=validate`; a suíte backend executou 544 testes (533 aprovados, 11 opt-in ignorados, zero falhas/erros). O frontend executou 242 testes, o build Angular e o pacote Maven passaram, e o smoke Firefox em modo mock passou com 29 requisições. Os testes cobrem extração, domínio próprio, confirmação, endpoints, unicidade e progresso persistido; não houve captura em site público real nem medição de cobertura de uma amostra de produção. Uma tentativa do smoke dentro do sandbox falhou ao iniciar o Firefox (`NS_ERROR_OUT_OF_MEMORY`); a repetição fora do sandbox passou.

## Riscos e pontos de atenção

- **Domínio próprio sem PSL:** aceitar host + subdomínios é conservador e evita hospedagem compartilhada; o caso "site em subdomínio, e-mail na raiz" fica de fora e entra no contador de perda.
- **Identidade difícil:** sites só com imagem/JS podem não exibir telefone/endereço/CNPJ em texto; sem evidência, não grava (perda aceitável, não é conclusão negativa).
- **Endereços ofuscados** (`contato [at] dominio`): fora desta versão.
- **Muro de consentimento/JS:** o leitor não executa JavaScript nem segue redirects; e-mails montados no cliente não são capturados.
- **Custo de tempo:** até 2 páginas × timeout por lead, agora assíncrono; a tela só consulta progresso.
- **Contrato:** adicionar `email` é aditivo; respostas antigas sem o campo continuam compatíveis.
- **Privacidade:** são contatos comerciais públicos do próprio estabelecimento; nada é lido em terceiros.

## Fora de escopo

- Crawling além da home + 1 página de contato; sitemap, múltiplos níveis ou página de terceiros.
- Decodificação de e-mails ofuscados ou montados por JavaScript.
- Uso do e-mail no `ScoringService`/temperatura.
- Edição/limpeza manual do e-mail via `PATCH /api/leads/{id}` e ressincronização retroativa.
- Envio automático de e-mail ou integração com provedor de disparo.
- Acoplar a captura ao classificador de Instagram/site da pesquisa inteligente; `EmailLeadService` fica reutilizável para isso depois.
- Endpoint por lead (`POST /api/leads/{id}/email`), que pode vir em outro refinamento sobre o mesmo serviço.
