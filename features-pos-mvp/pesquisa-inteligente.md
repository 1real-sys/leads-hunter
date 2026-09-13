# Sprint INFO-01 — Busca interna de site e Instagram no Histórico

**Status: EM IMPLEMENTAÇÃO — INFO-01.7 parcial: API Brave funcional, refinamento conservador de precisão validado com regressões e consultas reais. A amostra atual não corroborou URLs dos leads; falta ampliar o aceite com vínculos conhecidos. Scraping desativado por padrão.**

## Objetivo

Adicionar ao detalhe de uma busca do **Histórico** uma ação manual chamada **"Buscar informações"**, posicionada imediatamente à direita de **"Buscar CNPJ"**. A ação deve pesquisar, somente para os leads vinculados àquela execução, o **site próprio** e o **perfil do Instagram** de cada estabelecimento e persistir o resultado em `observacoes`.

## Decisões de produto já definidas

- A pesquisa é executada pelo backend. A fonte principal passou a ser a **API oficial do Brave Search** (13/09/2026), com chave em variável de ambiente e sem scraping. O fallback por navegador headless (Google → DuckDuckGo → Brave) permanece no código, **desativado por padrão**.
- A rotina não chamará Google Places, Place Details nem qualquer API da Google.
- Excepcionalmente aprovado em 13/09/2026: a API do Brave Search é usada com chave própria e crédito gratuito mensal; não há consumo de cota da Google.
- Após a página HTTP simples não entregar resultados, foi aprovado o uso de Chromium headless no servidor. A rotina usa Playwright nas páginas públicas dos buscadores configurados e jsoup para converter o HTML em DTOs internos; DuckDuckGo usa a versão HTML sem JavaScript. Esse caminho está desativado por padrão e só é usado com `PESQUISA_SCRAPING_HABILITADO=true`.
- A ação parte do detalhe aberto em `/historico/:id`; não varre todos os leads nem outras buscas.
- O botão se chama **"Buscar informações"** e fica à direita de **"Buscar CNPJ"**.
- A busca considera `googlePlaceId`, nome e categoria. Endereço, município, UF, telefone e CNPJ já disponíveis no lead devem ser usados como sinais adicionais de confirmação.
- Os únicos resultados de interesse são o site oficial do estabelecimento e seu perfil no Instagram.
- A feature deve conseguir capturar o perfil do Instagram quando ele estiver publicamente indexado e puder ser associado com segurança ao lead.
- Não haverá login, navegação autenticada, leitura de posts, seguidores, mensagens, automação de interação ou scraping do conteúdo interno do Instagram.
- Precisão tem prioridade sobre cobertura: diante de dúvida entre homônimos, filiais ou candidatos conflitantes, o sistema registra que não encontrou informação em vez de escolher um link incerto.
- A operação é iniciada manualmente, impede cliques duplicados enquanto estiver ativa e avisa quando terminar.
- Observações comerciais existentes serão preservadas. A rotina cria ou substitui somente um bloco identificado da pesquisa inteligente.
- Leads cujo bloco já contenha Instagram e site próprio válidos serão ignorados em novas execuções; resultados parciais ou negativos poderão ser pesquisados novamente.
- Os links produzidos serão exibidos como links clicáveis e seguros no detalhe do Histórico, além de permanecerem em `observacoes`.
- A execução e seu progresso serão persistidos. O usuário poderá sair da página e receber o resultado ao retornar ao detalhe da busca.
- O resultado passa a fazer parte do estado atual do `Lead`; os snapshots de score e temperatura em `BuscaLead` não são alterados.
- Status, último contato, scoring, CNPJ, razão social e demais dados comerciais não são modificados.

## O que significa "pesquisa interna"

O projeto não possui hoje uma base local contendo sites e perfis de Instagram. Nome, categoria e `googlePlaceId` ajudam a identificar o estabelecimento, mas não contêm essas URLs.

Nesta sprint, **pesquisa interna** significa que o próprio backend monta as consultas e consulta a **API oficial do Brave Search** para obter URL, título e resumo, sem navegador. O caminho antigo — abrir a página pública em navegador headless e analisar o HTML — continua disponível como fallback opcional desativado. A rotina depende de internet, da chave do Brave e da disponibilidade do provedor. Sem internet e sem uma base previamente indexada, não é tecnicamente possível descobrir automaticamente uma URL que ainda não existe no banco.

O caminho legado de scraping, desativado, permanece sujeito aos termos e bloqueios de cada buscador, sem resolver CAPTCHA ou evadir restrições. O caminho ativo usa a API oficial Brave com chave e está sujeito aos limites/condições do plano. Falha técnica não é ausência de resultado.

## Formato das observações

O conteúdo automático ficará delimitado para não apagar as anotações manuais. Quando os dois links forem encontrados:

```text
--- Pesquisa inteligente ---
Instagram:
https://www.instagram.com/perfil

Site próprio:
https://www.exemplo.com.br
--- Fim da pesquisa inteligente ---
```

Quando somente um link for encontrado, registrar apenas o item correspondente, sem rótulo vazio. Quando nenhum dos dois for encontrado com confiança, o bloco conterá exatamente:

```text
--- Pesquisa inteligente ---
pesquisa inteligente não encontrou mais informações
--- Fim da pesquisa inteligente ---
```

Se já houver observações comerciais, o bloco será acrescentado após uma linha vazia. Em novas execuções, somente o conteúdo entre os delimitadores será substituído. Texto fora deles nunca será apagado. Falha de conexão, timeout, captcha/bloqueio ou página em formato inesperado **não** equivalem a ausência de resultado: quando as alternativas também falham, o bloco anterior permanece inalterado e o lead entra na contagem de falhas.

## Contexto técnico

- O histórico já usa `GET /api/buscas/{id}` e possui o botão **"Buscar CNPJ"`. O novo fluxo mantém o mesmo grupo de ações, mas terá execução persistida própria para continuar quando o usuário sair da página.
- `BuscaLead` já delimita corretamente os leads pertencentes à busca. O relacionamento continua N:N; não será criado `lead.busca_id`.
- `Lead.observacoes` já é `TEXT` e receberá somente o bloco delimitado. Uma migration será necessária apenas para registrar a execução persistente e seu progresso.
- O `googlePlaceId` poderá compor uma consulta pública ou servir de evidência adicional quando aparecer em um resultado, mas não será enviado à Places API.
- A única credencial externa é `BRAVE_SEARCH_API_KEY`, lida de variável de ambiente e nunca versionada. Não há cobrança enquanto o uso ficar dentro do crédito gratuito mensal do Brave; o fallback de scraping não exige chave.
- Timeouts, quantidade máxima de consultas simultâneas e intervalo mínimo entre acessos continuam necessários para evitar travar a aplicação ou provocar bloqueios. Esses limites são controles internos de estabilidade, não cotas de API.
- A rotina não deve acessar os sites candidatos nem o Instagram para validar conteúdo. A classificação considera somente URL, título e resumo presentes nos resultados públicos dos buscadores configurados, reduzindo risco de SSRF e respeitando a proibição de scraping do Instagram.
- Somente URLs `http`/`https` dentro do bloco automático poderão virar links. O Angular não usará `innerHTML` nem bypass de sanitização.

## Arquitetura alvo

```text
HistoricoDetalhePage
    |
    | POST /api/buscas/{id}/informacoes
    v
BuscaController
    |
    +--> POST inicia e retorna 202 + id da execução
    +--> GET consulta a execução atual/mais recente
    v
BuscaInformacoesExecucaoService
    |
    +--> PesquisaInformacoesExecucaoRepository / MySQL
    |       persiste status, progresso, resumo e erro seguro
    |
    +--> BuscaInformacoesWorker
    |       trabalho local em segundo plano, sem fila externa
    |
    +--> PesquisaWebInternaService
    |       coordena consultas e classificação de Instagram/site
    |
    +--> PesquisaWebGateway
    |       BravePesquisaApiClient (API oficial do Brave) quando há chave
    |       PesquisaWebFallbackClient (scraping) apenas se habilitado explicitamente
    |
    +--> ClassificadorUrlService
    |       classifica Instagram/site próprio e rejeita candidatos incertos
    |
    +--> FormatadorObservacoesPesquisa
    |       gera o texto determinístico
    |
    v
LeadRepository / MySQL
    |
    v
PesquisaInformacoesExecucaoResponse
```

As chamadas externas não devem manter uma transação de banco aberta. O worker lê uma projeção imutável dos leads, executa a pesquisa fora de transação e persiste cada resultado e o progresso em transações curtas. Entidades JPA não serão compartilhadas entre threads. A execução local em segundo plano não usará Redis, Kafka, RabbitMQ ou outro serviço de fila.

## Histórias e entregáveis

### INFO-01.1 — Pesquisa interna no Google Search

**Status: CONCLUÍDA em 12/09/2026; caminho desativado por padrão em 13/09/2026, quando a API do Brave passou a ser a fonte principal.** O cliente HTTP simples foi substituído, após aprovação, por um navegador Chromium headless. O backend mantém uma única instância reutilizável do browser em uma thread dedicada, cria um contexto isolado por consulta e bloqueia imagens, fontes, mídia e folhas de estilo. Isso preserva a restrição de concorrência, reduz consumo e respeita a exigência de afinidade de thread do Playwright.

O acesso inicial é fixo em `https://www.google.com/search`; redirecionamentos e recursos ficam limitados a hosts do Google, e nenhum resultado é aberto pelo navegador. Há timeout, fila limitada, intervalo mínimo entre navegações, limite de 2 MiB para o HTML renderizado e encerramento pelo ciclo de vida do Spring. Captcha, bloqueio por tráfego incomum, indisponibilidade, timeout, ausência real e formato inesperado são diferenciados sem registrar ou devolver HTML bruto.

O parser jsoup extrai URL, título e resumo para DTOs internos, resolve links de redirecionamento do Google e rejeita URLs internas. Fixtures locais cobrem resultado, ausência, captcha e mudança de estrutura. Um smoke real confirmou que o Chromium executa e que o IP atual é redirecionado pelo Google para `/sorry` por tráfego incomum; o bloqueio foi classificado com segurança, sem tentativa de captcha, disfarce do navegador ou evasão. Em outro IP, a disponibilidade continua sendo decidida pelo próprio Google.

**Objetivo:** localizar resultados públicos sem API, chave ou consumo de cota.

**Entregáveis:**

- Criar `GooglePesquisaWebClient` com Playwright Java para renderização headless e jsoup para leitura isolada do HTML.
- Acessar somente a página pública do Google Search, sem Google Places, API de pesquisa ou API key, registrando seus termos e limitações antes da implementação definitiva.
- Montar consultas específicas para site próprio e Instagram usando nome, categoria e localização do lead.
- Isolar a leitura do formato externo em DTOs internos, sem espalhar HTML ou seletores pelo domínio.
- Configurar timeout, tamanho máximo de resposta, redirecionamentos controlados, identificador HTTP transparente e concorrência pequena.
- Tratar bloqueio, captcha, timeout, formato alterado, resposta vazia e falha de rede sem expor HTML bruto ao frontend ou aos logs.
- Criar fixtures locais de páginas de resultado para que testes não dependam da internet.

**Critérios de aceite:**

- Nenhuma chamada é feita a Google Places ou a uma API de pesquisa.
- A feature não exige nova chave, conta, plano pago ou cota contratada.
- O client extrai URL, título e resumo de fixtures conhecidas e falha de forma segura diante de formato inválido.
- Testes cobrem sucesso, ausência real, timeout, bloqueio/captcha e mudança inesperada de HTML.
- Uma mudança no HTML do Google pode ser corrigida dentro do client sem alterar controller, serviço de negócio ou frontend.

**Preparação local do navegador:**

```bash
./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --only-shell chromium"
```

Em um host novo, as bibliotecas nativas exigidas pelo Chromium também precisam ser instaladas conforme o diagnóstico oficial do Playwright. O binário fica no cache local do usuário e não é empacotado no JAR.

Os padrões podem ser ajustados por propriedades Spring: `pesquisa-inteligente.google.timeout-ms` (15 s), `max-resposta-bytes` (2 MiB), `max-resultados` (10), `capacidade-fila` (1), `intervalo-minimo-ms` (15 s) e `bloqueio-cooldown-ms` (1 h). Esses limites são locais e não representam cota de API. O intervalo é aplicado entre todas as navegações, inclusive entre Instagram e site do mesmo lead.

### INFO-01.2 — Captura e classificação precisa de Instagram e site

**Status: CONCLUÍDA em 12/09/2026.** `PesquisaWebInternaService` executa uma consulta de Instagram e outra de site por lead e entrega os resultados ao `ClassificadorUrlService`. O lead é convertido em `PesquisaLeadDados`, snapshot imutável com os sinais disponíveis, evitando transportar entidade JPA para o futuro processamento assíncrono.

O canonicalizador aceita apenas `http`/`https`, remove caminho, query e fragmento de sites e converte Instagram em `https://www.instagram.com/{usuario}`. Posts, reels, stories, áreas internas do Instagram, credenciais na URL, portas não padrão, hosts locais/IPs e redes sociais, mapas, diretórios, agregadores, marketplaces, avaliações e delivery são rejeitados antes da pontuação. Sites com e sem `www` e URLs com rastreamento são deduplicados pelo host.

A identidade recebe sinais determinísticos de nome normalizado, domínio/handle, categoria, município, endereço, telefone, CNPJ, razão social e `googlePlaceId` quando este aparece no resultado público. A seleção exige nome forte, uma evidência independente ou identificador distintivo, mínimo de 70 pontos e margem de 15 pontos para o segundo candidato elegível. CNPJ explícito de outra unidade invalida o candidato; nomes genéricos sem confirmação local/forte e empates resultam em ausência. O classificador não acessa nenhuma URL candidata.

**Objetivo:** encontrar URLs suficientemente confiáveis para o estabelecimento correto, incluindo seu Instagram quando existir publicamente.

**Entregáveis:**

- Executar no Google uma consulta voltada ao Instagram e outra voltada ao site próprio, com termos derivados do lead.
- Para Instagram, aceitar somente URLs canônicas de perfil `instagram.com/{usuario}` e rejeitar post, reel, stories, explore, hashtags, páginas de login e compartilhamento.
- Para site próprio, rejeitar redes sociais, mapas, diretórios, agregadores de links, marketplaces, páginas de avaliação e delivery.
- Canonicalizar URLs, aceitar somente `http`/`https`, remover parâmetros de rastreamento e deduplicar candidatos.
- Pontuar identidade com os sinais disponíveis: nome, domínio/handle, categoria, município/UF, endereço, telefone e CNPJ.
- Usar `googlePlaceId` como sinal apenas quando estiver presente no material público encontrado; não fazer consulta à API da Google.
- Exigir candidato único acima do limiar. Empate, baixa confiança ou conflito entre unidade/matriz resultam em ausência.

**Critérios de aceite:**

- Um perfil de Instagram claramente associado ao lead é capturado mesmo quando não há site próprio.
- Homônimos em cidades diferentes não são aceitos apenas pela igualdade do nome.
- Link de post/reel do Instagram nunca é persistido como perfil.
- Linktree, Google Maps, iFood, TripAdvisor e diretórios equivalentes nunca são persistidos como site próprio.
- Um candidato único e fortemente compatível é retornado; empate, baixa confiança ou conflito resultam em ausência.
- Testes unitários cobrem acentos, caixa, nomes genéricos, filiais, homônimos, parâmetros de rastreamento e URLs malformadas.

### INFO-01.3 — Orquestração por busca e persistência

**Status: CONCLUÍDA em 12/09/2026.** `BuscaInformacoesService` carrega snapshots somente pelos vínculos `BuscaLead` da busca informada e processa os leads sequencialmente. O serviço não possui transação: cada acesso externo termina antes de `JpaBuscaInformacoesPersistencia` abrir uma transação curta para reler o lead e atualizar apenas `observacoes`.

`FormatadorObservacoesPesquisa` reconhece somente URLs válidas dentro dos delimitadores, ignora blocos já completos, mantém links válidos de um resultado parcial e substitui os blocos automáticos sem modificar os bytes do texto manual ao redor. Resultado conclusivo vazio usa a frase definida; exceção técnica não chama a persistência. O resumo considera `processados` somente os resultados conclusivos e mantém `totalLeads = processados + ignoradosJaCompletos + falhas`.

Para reduzir risco operacional sobre o IP, a execução é estritamente sequencial, a fila padrão do navegador foi reduzida para um item e o intervalo padrão passou a 15 segundos por navegação. Ao primeiro CAPTCHA/bloqueio, o restante pesquisável do lote é encerrado sem novos acessos e contabilizado como falha; o client recusa novas navegações em memória por uma hora. Três falhas técnicas consecutivas também interrompem o restante. Não há retry automático, rotação de IP, proxy, disfarce do navegador ou resolução de CAPTCHA.

Os 57 testes unitários direcionados da pesquisa e orquestração e os dois testes de integração JPA desta etapa passaram. O pacote executável foi gerado. A suíte completa executou 221 testes: 214 passaram, um smoke opt-in foi ignorado e permaneceram três falhas e três erros preexistentes da blacklist e das fixtures CNPJ locais; nenhum teste da pesquisa inteligente falhou.

**Objetivo:** processar os leads da execução aberta, formatar o resultado e atualizar somente o necessário.

**Entregáveis:**

- Criar `BuscaInformacoesService`, validando a existência da busca e carregando seus vínculos por `BuscaLeadRepository`.
- Processar somente a quantidade real de leads daquela busca, com concorrência pequena e limitada para preservar estabilidade.
- Identificar o bloco automático nas observações e ignorar leads que já possuam Instagram e site próprio válidos, contabilizando-os em `ignoradosJaCompletos`.
- Pesquisar novamente leads com apenas um dos links ou com resultado negativo, preservando o bloco anterior até existir um novo resultado conclusivo.
- Executar acessos à internet fora de transação; persistir em transação curta apenas os leads com resultado conclusivo ou ausência conclusiva.
- Formatar deterministamente o bloco automático conforme os exemplos e substituir somente o trecho delimitado, preservando integralmente o texto comercial fora dele.
- Não alterar observações quando houver falha técnica. Uma falha em um lead não desfaz resultados válidos dos demais.
- Retornar resumo com `totalLeads`, `processados`, `ignoradosJaCompletos`, `comInstagram`, `comSite`, `comAmbos`, `semInformacoes` e `falhas`.

**Critérios de aceite:**

- Somente leads vinculados ao ID informado são processados; busca inexistente retorna `404`.
- Encontrar os dois links produz os dois blocos separados por uma linha vazia e na ordem Instagram/site próprio.
- Encontrar apenas um produz somente seu bloco; ausência conclusiva produz a frase definida.
- Observações anteriores permanecem byte a byte iguais fora dos delimitadores; nova execução não duplica o bloco.
- Lead com Instagram e site válidos no bloco é ignorado sem nova consulta ao Google.
- Captcha/bloqueio do Google, timeout ou indisponibilidade preservam as observações e entram em `falhas`.
- Deduplicação por `googlePlaceId`, status, último contato, CNPJ, scoring e snapshots históricos permanecem intactos.
- Testes de serviço e integração JPA cobrem resultado completo, somente Instagram, somente site, vazio, falha externa, busca inexistente e isolamento entre buscas.

### INFO-01.4 — Execução persistente em segundo plano

**Status: CONCLUÍDA.** A migration V6 adiciona a execução relacionada à busca, seus estados, datas, contadores e erro seguro. Uma coluna gerada com índice único impede duas execuções ativas para a mesma busca; o serviço também bloqueia a busca durante a criação para devolver a mesma execução aos pedidos concorrentes.

O worker local usa uma thread e uma posição de espera. A capacidade é reservada antes da criação e liberada no rollback; o envio ao executor ocorre somente após o commit. O limite padrão é de 150 leads por execução (`pesquisa-inteligente.execucao.max-leads`, configurável entre 1 e 1000). Cada resultado e seu progresso são persistidos atomicamente em transação curta, sem acesso externo dentro da transação. Na inicialização, pendências e execuções em andamento viram `FALHA/PESQUISA_INTERROMPIDA`, preservando resultados e contadores, sem retomada automática. O modelo é de uma única instância local da aplicação.

Passaram 22 testes direcionados, incluindo 11 testes JPA da execução e três testes do worker; o pacote foi gerado. Flyway validou o schema V6 e Hibernate iniciou com `ddl-auto=validate`. A tentativa inicial no sandbox não acessou o MySQL; a execução com acesso local passou.

**Objetivo:** permitir sair da página e acompanhar a pesquisa quando voltar.

**Entregáveis:**

- Criar migration Flyway e entidade `PesquisaInformacoesExecucao`, relacionada à `Busca`, com status `PENDENTE`, `EM_ANDAMENTO`, `CONCLUIDA`, `CONCLUIDA_COM_FALHAS` ou `FALHA`.
- Persistir início, atualização, término, total, progresso, contadores do resumo e uma mensagem de erro segura quando aplicável.
- Iniciar o worker local somente depois do commit que cria a execução, usando executor limitado do Spring/Java e sem mensageria externa.
- Impedir atomicamente duas execuções ativas para a mesma busca.
- Se a aplicação reiniciar com uma execução em andamento, marcá-la como interrompida/falha e permitir nova tentativa; não retomar silenciosamente um lote incompleto.
- Persistir progresso após cada lead para que resultados já concluídos não sejam perdidos se os próximos falharem.

**Critérios de aceite:**

- O retorno HTTP não fica aberto durante toda a pesquisa.
- Sair e voltar ao detalhe recupera status, progresso e resultado da execução persistida.
- Somente uma execução pode ficar ativa por busca.
- Reinício da aplicação não deixa status `EM_ANDAMENTO` indefinidamente.
- Migration executa com Flyway e Hibernate continua usando `ddl-auto=validate`.
- Testes JPA cobrem criação, progresso, conclusão, falha, concorrência e recuperação de execução interrompida.

### INFO-01.5 — Endpoints REST e contrato de erros

**Status: CONCLUÍDA.** `POST /api/buscas/{id}/informacoes` retorna `202` com a execução e `Location`, sem esperar a pesquisa. Pedidos concorrentes recuperam a execução ativa. `GET` retorna a execução ativa/mais recente com `200`, ou `204` quando a busca existe mas ainda não houve execução; busca inexistente retorna `404`. IDs inválidos retornam `400`, e capacidade ou volume excedidos retornam `429 PESQUISA_LIMITE_EXCEDIDO`. As respostas de acompanhamento usam `Cache-Control: no-store`.

Falhas do worker são expostas pelo DTO persistido (`status`, `erroCodigo`, `erroMensagem`), com mensagens predefinidas para bloqueio, timeout, formato inválido, indisponibilidade e interrupção. Consultar uma execução com `FALHA` retorna `200`, pois o erro é do trabalho assíncrono e não da consulta HTTP. O contrato completo, estados e significado dos contadores estão em `API.md`.

Passaram 64 testes direcionados, incluindo 22 testes MVC da nova ação e um fluxo HTTP/JPA com worker real e gateway externo simulado. Esse fluxo confirmou retorno imediato, progresso parcial, deduplicação enquanto a pesquisa está bloqueada no teste e conclusão com falhas preservando observações. A suíte completa executou 258 testes: 251 passaram, um smoke opt-in foi ignorado e permaneceram as três falhas e três erros preexistentes de blacklist/CNPJ local. O pacote final foi gerado; nenhuma consulta real ao Google foi necessária nesta entrega.

**Objetivo:** expor a ação manual ao frontend com resposta resumida e segura.

**Entregáveis:**

- Adicionar `POST /api/buscas/{id}/informacoes`, sem body, criando a execução e retornando `202 Accepted` com `PesquisaInformacoesExecucaoResponse`.
- Adicionar `GET /api/buscas/{id}/informacoes`, retornando a execução ativa ou a mais recente para restaurar o acompanhamento da tela.
- Manter controller fino, DTO explícito e tratamento centralizado de erros.
- Mapear captcha/bloqueio do Google, timeout, indisponibilidade e formato inválido sem expor detalhes internos.
- Impedir processamento ilimitado e duas varreduras simultâneas para a mesma busca.
- Documentar endpoint, resposta e erros em `API.md` na implementação.

**Critérios de aceite:**

- `202` inicia a execução e retorna seu ID/status; `GET` retorna progresso e resumo, inclusive falhas parciais contabilizadas.
- `404` é retornado para busca inexistente; falha total da pesquisa usa erro padronizado coerente com a API atual.
- Requisições concorrentes para a mesma busca retornam a execução ativa sem iniciar duas varreduras.
- Testes MVC verificam método, rota, resposta, códigos de erro e delegação.

### INFO-01.6 — Botão, acompanhamento e links no Histórico

**Status: CONCLUÍDA em 13/09/2026.**

O detalhe do Histórico apresenta o botão na sequência existente, com contrato tipado em `BuscaApi` e estado por tela em Signals. O acompanhamento consulta o servidor cinco segundos após cada resposta, sem requisições sobrepostas, e usa timeout de 15 segundos por requisição. Sair da rota ou trocar o ID cancela apenas o acompanhamento local; reabrir restaura a execução persistida por GET. Falha de comunicação exige retomar a consulta antes de liberar nova pesquisa, sem repetição automática do POST.

A conclusão apresenta os contadores e recarrega o detalhe sem ocultá-lo. Falhas parciais/totais preservam resultados visíveis; erro na atualização permite recarregar os dados. Somente URLs HTTP/HTTPS rotuladas no bloco automático completo viram links seguros em nova aba; texto manual e quebras de linha são preservados, sem `innerHTML`.

Validação: 239 testes frontend passaram; build de produção sem warnings; smoke de regressão em modo mock passou com 28 requisições. `npm run e2e:informacoes` passou no Firefox com API simulada, saída/retorno à rota, restauração após recarga, falhas parcial/total/de comunicação e 24 auditorias em claro/escuro, desktop/mobile, sem overflow ou violações Axe WCAG A/AA. A execução do Firefox precisou ocorrer fora do sandbox após erro de navegação `NS_ERROR_OUT_OF_MEMORY`. Nenhuma pesquisa externa real foi iniciada nesta entrega.

**Objetivo:** permitir iniciar e acompanhar a ação no detalhe da busca.

**Entregáveis:**

- Adicionar **"Buscar informações"** imediatamente à direita de **"Buscar CNPJ"** no grupo de ações existente, reutilizando a hierarquia visual atual sem novo card, modal ou ícone decorativo.
- Centralizar as chamadas em `BuscaApi` e tipar `PesquisaInformacoesExecucaoResponse` no modelo compartilhado.
- Usar Signals para início, progresso, resumo e erro; desabilitar o botão durante a operação e quando o detalhe não estiver em estado de sucesso.
- Durante a ação, mostrar **"Buscando informações…"** e uma mensagem de status acessível.
- Consultar periodicamente `GET /api/buscas/{id}/informacoes` enquanto houver execução ativa e restaurar esse acompanhamento ao reabrir a rota.
- Ao concluir, avisar **"Busca de informações concluída"**, apresentar o resumo e recarregar `GET /api/buscas/{id}` para refletir as observações atuais.
- Exibir as URLs válidas do bloco automático como links clicáveis, abrindo em nova aba com `rel="noopener noreferrer"`, sem `innerHTML` ou bypass de segurança.
- Se houver falhas parciais, informar quantos leads não puderam ser processados sem esconder os resultados concluídos.
- Em erro total, manter o detalhe visível, apresentar `role="alert"` e permitir nova tentativa.

**Critérios de aceite:**

- A ordem visual das ações é `Voltar ao histórico` → `Buscar CNPJ` → `Buscar informações`.
- Múltiplos cliques durante o loading geram somente uma requisição.
- Sair da rota e voltar durante a execução restaura progresso; voltar depois da conclusão apresenta o resultado persistido.
- Sucesso recarrega o detalhe e mostra o texto persistido nas observações de cada lead.
- Somente URLs `http`/`https` reconhecidas dentro do bloco automático viram links; texto manual continua escapado pelo Angular.
- Estado vazio/inválido/erro do detalhe não permite iniciar a busca.
- Botão funciona por teclado, mantém foco visível, não depende apenas de cor e preserva os layouts claro/escuro e desktop/mobile sem overflow.
- Specs cobrem posição, chamada, loading, sucesso, falha parcial, erro total, recarga e bloqueio de duplicatas.

### INFO-01.7 — Validação integrada e documentação

**Status: PARCIAL em 13/09/2026 — a chave está configurada e a API foi exercitada; o refinamento abaixo corrige associações inseguras, mas o aceite amplo de cobertura ainda depende de evidências.**

**Refinamento de precisão — estado atual:**

- `BravePesquisaApiClient` solicita `extra_snippets=true`, `spellcheck=false`, `operators=false`, `text_decorations=false` e somente resultados `web`. Até cinco trechos adicionais da mesma URL são limpos, deduplicados e limitados a 500 caracteres cada, além da descrição. Não há requisições extras para obtê-los. JSON estruturalmente inválido é falha técnica, não ausência conclusiva.
- Nome/handle iguais, seguidores ou categoria isoladamente não provam identidade. Exige-se município independente do nome comercial, endereço com número ou identificador forte. Telefone nacional e internacional são comparados com DDD; números espalhados no texto não são concatenados para fabricar correspondência de telefone/CNPJ.
- Município/UF explicitamente divergentes e CNPJ conflitante vetam o candidato; DDD divergente também veta quando não há confirmação por CNPJ/Place ID. Bairro genérico não neutraliza conflitos. Query strings, paths e nome de rua/bairro não confirmam município.
- Sites precisam de relação entre nome e domínio-base inclusive para nomes genéricos. Diretório desconhecido ou subdomínio com nome do lead não basta. Instagram com handle abreviado exige identificador externo forte. Posts e reels continuam recusados, sem derivar perfis por suposição.
- Resultados das duas consultas iniciais são reaproveitados para Instagram e site. Havendo terceira consulta de Instagram sem município, a seleção considera todos os candidatos anteriores; duplicatas não apagam conflitos e homônimos não somem por mudança de consulta. Máximo mantido em três consultas por lead.
- Foram feitas **40 chamadas reais**, sem escrita no banco: 18 de referência, 18 com trechos adicionais nos mesmos seis leads, três para Michel e uma consulta diagnóstica pelo telefone. O site de Oliveira antes aceito trouxe Viamão/RS nos novos trechos; `@supermercadomichel` trouxe DDD 41 enquanto o lead de Castelo tem DDD 28. Ambas as associações foram recusadas. Nenhuma URL dos sete leads foi suficientemente corroborada. Isso não demonstra inexistência de site/Instagram nem uma taxa geral de precisão.
- A confirmação extra por telefone não trouxe evidência útil e **não** virou nova consulta automática. Os temporários locais permitem comparar o classificador sem consumir novamente a API; não contêm credenciais e não são versionados.
- `./mvnw package` passou com **333 testes: 327 aprovados, zero falhas/erros e seis opt-in**. O E2E legado de HTML simulado desativa explicitamente Brave real e espera 14 navegações (incluindo as consultas condicionais já existentes), sem alterar o comportamento da UI.
- Não houve migration, dependência, alteração de UI ou saneamento retroativo das observações. Blocos antigos completos continuam ignorados pelo comportamento existente.

Referência dos parâmetros utilizados: [documentação oficial de Web Search da Brave API](https://api-dashboard.search.brave.com/api-reference/web/search/get).

Diagnóstico opt-in (máximo de seis leads, até 18 chamadas; usar somente com orçamento disponível):

```bash
./mvnw test -Dtest=PesquisaBraveLeadsReaisLiveTest -DpesquisaBraveLeadsLive=true
# Nome exato opcional: -DpesquisaBraveNome='Supermercado Michel'
# Sem rede/banco: reutilizar o caminho temporário emitido como BRAVE_AMOSTRA
./mvnw test -Dtest=PesquisaBraveLeadsReaisLiveTest -DpesquisaBraveLeadsLive=true -DpesquisaBraveReplay=/tmp/brave-precisao-ARQUIVO.json
```

O comando live interrompe em falha técnica, espaça chamadas em 1,1 segundo, lê o banco em modo somente leitura e mede candidatos, não atesta sozinho que as URLs pertencem aos leads. Os registros abaixo descrevem as rodadas anteriores, inclusive capturas depois consideradas insuficientemente corroboradas.

**Atualização após a mudança para a API do Brave (13/09/2026):**

- Como Google, DuckDuckGo e Brave bloquearam o acesso automatizado por navegador, a fonte principal passou a ser a **API oficial do Brave Search** (`https://api.search.brave.com/res/v1/web/search`), que devolve JSON e não sofre bloqueio de scraping.
- `BravePesquisaApiClient` usa `java.net.http.HttpClient` com timeout, `Accept: application/json`, `X-Subscription-Token` e leitura limitada do corpo. A resposta é convertida em `GoogleResultadoWeb` (URL, título e resumo) e entregue ao classificador existente; o classificador continua decidindo Instagram/site com a mesma pontuação conservadora.
- `PesquisaWebGateway` (`@Primary`) escolhe a fonte: Brave quando há `BRAVE_SEARCH_API_KEY`; caso contrário, o scraping `Google → DuckDuckGo → Brave` **somente** se `PESQUISA_SCRAPING_HABILITADO=true`. Sem nenhuma das duas, a pesquisa retorna indisponibilidade sem alterar observações.
- Continua sendo **1 consulta por tipo e 2 por lead** (Instagram e site próprio), conforme decisão do produto. Com o crédito gratuito de US$ 5/mês (≈ 1.000 requisições), isso cobre cerca de 500 leads/mês.
- Erros mapeados: `429` → bloqueio (`PESQUISA_BLOQUEADA`); `401/403/5xx` → indisponível; JSON inválido → formato inválido; timeout → timeout. As mensagens passaram a ser neutras quanto ao provedor.
- `./mvnw test` passou com **281 testes, zero falhas/erros e três opt-in não habilitados**. A verificação com a API real depende de o usuário criar a chave e definir `BRAVE_SEARCH_API_KEY`; não foi executada nesta entrega.
- Arquivos: `BravePesquisaApiClient`, `PesquisaWebGateway`, testes unitários do cliente e do seletor, e configuração em `application.yml`. Nenhuma dependência nova.

**Atualização após o teste do Bing como alternativa sem chave (13/09/2026):**

- Como o usuário preferiu não cadastrar cartão, foi testado o Bing via Playwright. Um smoke controlado mostrou que o Bing responde `200` com 10 resultados orgânicos (`li.b_algo`) e que o Mojeek responde `403`.
- O Bing foi integrado como fonte principal do scraping: `FontePesquisaWeb.BING`, parser próprio que seleciona `li.b_algo` e decodifica o redirecionador `bing.com/ck/a?...&u=a1<base64url>`, e a ordem do fallback passou a ser **Bing → Google → DuckDuckGo → Brave**. O `PesquisaWebGateway` mantém a API do Brave como opcional quando `BRAVE_SEARCH_API_KEY` existir.
- Dois defeitos reais do navegador foram corrigidos: URLs inválidas em sub-recursos do Bing quebravam `URI.create` no route handler (a exceção virava "formato inválido") e a leitura de conteúdo podia falhar quando a página ainda navegava (agora há retry limitado de `content()`).
- **Limitação comprovada:** a validação real não encontrou links confiáveis. O Bing respondeu de forma geoviesada (retornou padarias de Porto Seguro/BA mesmo para consultas de Sorocaba/SP) e devolveu resultados genéricos e ruidosos para nomes de estabelecimento, além de fechar a página sob consultas repetidas. O classificador conservador rejeitou corretamente todos os candidatos, portanto **não é possível afirmar captura/precisão reais pelo Bing**. O smoke opt-in de captura foi removido por não ser confiável.
- `./mvnw test` passou com **284 testes, zero falhas/erros e quatro opt-in não habilitados**, incluindo os novos testes de parser do Bing (decodificação de redirecionamento, ausência e bloqueio) e o teste de ordem do fallback.
- Arquivos: `FontePesquisaWeb`, `PesquisaAlternativaHtmlParser`, `PlaywrightGooglePesquisaNavigator`, `application.yml`, fixtures `bing-*.html`, `PesquisaWebFallbackClientTest`, `PesquisaInformacoesE2eTest` e `PesquisaFontesCandidatasLiveTest`.

**Decisão final (13/09/2026): Brave é a fonte; scraping desativado.**

- A medição com 6 leads reais (Castelo/ES) capturou **0 Instagram e 0 site**. O Bing devolveu resultados genéricos de "armazém" de outras cidades e **idênticos** para as consultas de Instagram e site, ignorando nome, município e `site:`. Não é falha do parser nem do classificador: o Bing degrada a resposta para o cliente headless.
- Por isso, `pesquisa-inteligente.scraping.habilitado` voltou ao padrão `false`. Sem `BRAVE_SEARCH_API_KEY`, a pesquisa retorna indisponibilidade rápida em vez de executar consultas lentas que não produzem resultado.
- A API do Brave é a fonte efetiva. O código do Bing e o fallback permanecem no projeto, desativados, para eventual reavaliação futura. As correções do navegador (URL inválida de sub-recurso e leitura durante navegação) foram mantidas por serem benéficas ao fallback.

**Validação real com a API do Brave (13/09/2026):**

- A chave do Brave foi configurada e a API respondeu `200`. A consulta no formato do Google (aspas + `site:`) retornava `0` resultados no Brave; por isso o `BravePesquisaApiClient` passou a montar sua **própria consulta**, sem operadores do Google: `nome município UF` e `nome município UF instagram`.
- Um defeito foi corrigido: o Brave exige `search_lang=pt-br` (não `pt`); a chamada anterior retornava `422` e era classificada como indisponível.
- Na leitura de 6 leads reais (Castelo/ES), a captura foi de **2 Instagram e 0 site**. Sem a consulta própria, era 0/0.
- O classificador passou a exigir, para **site próprio**, que o domínio contenha algum token distintivo do nome do estabelecimento. Isso rejeitou diretórios que apareciam com o nome exato no título (ex.: `guiaja.net`, `supermercado.net.br`), que antes eram aceitos por engano.
- As 2 capturas de Instagram são de nomes genéricos ("Atacado e varejo", "Supermercado Oliveira"); a associação ainda precisa ser conferida caso a caso. Nomes de estabelecimento muito genéricos continuam sendo o principal risco de falso positivo.
- Suíte completa: **287 testes, zero falhas/erros e seis opt-in não habilitados**.
- Arquivos: `BravePesquisaApiClient` (consulta própria e `pt-br`), `ClassificadorUrlService` (relação domínio/nome para site), `PesquisaBraveLeadsReaisLiveTest` (diagnóstico somente leitura).

**Aprimoramento de cobertura com precisão (13/09/2026):**

- Caso reportado: o lead "Supermercado Michel" (Castelo/ES) não capturava nada, embora o Instagram `@supermercadomichel` exista e a busca manual pelo nome o encontre. O trecho do resultado não trazia a cidade.
- Três ajustes:
  - `ClassificadorUrlService` passou a reconhecer **handles e domínios concatenados** (ex.: `supermercadomichel` contém o token distintivo `michel`), antes exigido como token separado.
  - Foi adicionado **conflito de localização**: se o trecho cita uma UF diferente da do lead (ou a UF do lead sem o município/bairro), o candidato não é corroborado apenas por nome+identificador. Isso rejeita o site de Curitiba e mantém homônimos de outra cidade fora.
  - `PesquisaWebInternaService` faz uma **segunda consulta de Instagram sem o município** quando a primeira não encontra perfil aceitável, permitindo achar `@supermercadomichel` pelo nome.
- Resultado verificado no lead real: `instagram.com/supermercadomichel` capturado e site de Curitiba rejeitado.
- Custo: a consulta extra só ocorre quando não há Instagram aceitável; em lotes grandes pode aumentar o consumo do crédito mensal.
- Suíte completa: **290 testes, zero falhas/erros e seis opt-in não habilitados**.

**Atualização após autorização de DuckDuckGo e Brave:**

- `PesquisaWebFallbackClient` usa Google → DuckDuckGo → Brave por consulta. Só falha técnica aciona a próxima fonte; resultado válido, inclusive vazio, encerra a consulta. Fila local ocupada e interrupção não disparam acessos extras.
- O navegador único mantém o intervalo global de 15 segundos, incluindo trocas de fonte. A espera síncrona também considera esse intervalo, evitando consumir o timeout de navegação enquanto aguarda o próximo acesso permitido.
- Cada fonte fica em cooldown em memória por uma hora após bloqueio ou cinco minutos após outra falha técnica. Não há retry, troca de IP, login, execução de desafio ou resolução de CAPTCHA. O cooldown continua local à instância; não reiniciar para contorná-lo.
- DuckDuckGo usa sua página HTML sem JavaScript; Brave usa a página pública de busca. Hosts/caminhos são fixos e recursos são restritos à fonte ativa. Candidatos, anúncios, APIs e redirecionamentos para destinos arbitrários não são acessados. Parsers separados extraem somente resultados orgânicos; o classificador conservador existente decide os links.
- As falhas preexistentes de CNPJ/blacklist foram resolvidas isolando o banco, sem alterar expectativas dos testes ou limpar dados reais. Um inicializador exclusivo do classpath de testes cria um catálogo MySQL aleatório `lh_test_<uuid>`, aplica Flyway e usa Hibernate em `validate`. O catálogo é compartilhado pelos contextos da mesma JVM e removido no encerramento normal; a base da aplicação nunca é fallback.
- O E2E integrado agora executa dois cenários: Google principal e Google/DuckDuckGo bloqueados com captura pelo Brave simulado. Ambos passaram, incluindo onze navegações simuladas por cenário e um único POST pela tela.
- `./mvnw package` passou com **269 testes: 266 aprovados, zero falhas/erros e três opt-in não habilitados**. Os 17 testes direcionados de fallback/consulta/isolamento e os dois E2Es passaram. O inicializador de banco e suas configurações foram conferidos como ausentes do JAR de produção.
- O novo `PesquisaFallbackWebLiveTest` exige ao menos um link classificado, não aceita somente tratamento de bloqueio como sucesso. Às **12:29 de 13/09/2026**, Google, DuckDuckGo e Brave bloquearam uma tentativa cada; o smoke terminou com erro de indisponibilidade, sem URLs inventadas ou retry. Nenhuma nova consulta foi realizada após esses bloqueios.

Referências públicas consultadas: [DuckDuckGo sem JavaScript](https://duckduckgo.com/duckduckgo-help-pages/features/non-javascript), [política de uso do DuckDuckGo](https://duckduckgo.com/acceptable-use), [busca e operadores do Brave](https://search.brave.com/help) e [termos do Brave](https://brave.com/terms-of-use/). A integração não garante disponibilidade e não deve contornar restrições dos provedores.

**Registro da primeira rodada da INFO-01.7, antes desses ajustes:**

Foi adicionado `PesquisaInformacoesE2eTest`, opt-in com `pesquisaE2e=true`, usando o build Angular em Chromium, servidor HTTP Spring em porta aleatória, worker, parser/classificador, Flyway/Hibernate e MySQL reais. Somente `PlaywrightGooglePesquisaNavigator` recebe páginas HTML simuladas. O teste inicia pelo botão, sai da rota por mais de um intervalo de polling, retorna durante a execução e recarrega após concluir. Confirma ambos os links, somente Instagram, somente site, ausência conclusiva, falha técnica, lead completo ignorado, lead de outro histórico intacto, observações manuais preservadas, status/contato/scoring e snapshots preservados, links seguros e nenhuma chamada ao Places. Seus registros temporários são removidos por IDs próprios após o worker terminar; não há limpeza global do banco.

O smoke público foi ajustado para usar os limites conservadores existentes e imprimir somente um diagnóstico seguro que diferencie resultados de bloqueio. Em **13/09/2026**, uma única consulta real retornou `PESQUISA_PUBLICA=BLOQUEADA; captura_e_precisao=NAO_VALIDADAS; sem_retry=true`. Não houve segundo acesso, tentativa de captcha, troca de IP, API paga ou alteração dos limites de produção. O teste passou por reconhecer corretamente o bloqueio, não por encontrar URLs reais.

**Resultados executados:**

| Validação | Resultado |
| --- | --- |
| Backend completo, após as alterações | 259 testes: 251 passaram, três falhas, três erros e dois opt-in não habilitados nesta execução. |
| Pacote com testes backend direcionados | Gerado; 104 testes passaram, dois opt-in executados separadamente. |
| E2E Angular → HTTP → worker → HTML simulado → MySQL | Um teste passou, com nove navegações simuladas e um único POST pelo botão. |
| Smoke público controlado | Um teste passou no ramo de bloqueio; captura/precisão reais não validadas. |
| Frontend direcionado | 35 testes passaram. |
| Frontend completo | 239 testes passaram. |
| Build Angular de produção | Passou sem warnings. |
| Smoke de regressão no Firefox | Passou em modo mock, 28 requisições. |
| Matriz da pesquisa no Firefox | 24 auditorias em claro/escuro, 1440 × 1000 e 390 × 844, sem overflow ou violações Axe WCAG A/AA. |

As seis ocorrências do backend já existiam: `BuscaServiceJpaIntegrationTest` tem uma falha de correspondência CNPJ e um erro por termo já bloqueado; `CnpjRepositoryTest` espera um registro e recebe 200; `BlacklistFlowIntegrationTest` recebe 400 por termo já existente; `NomeBloqueadoRepositoryTest` tem dois erros por unicidade de termo. Nenhum teste existente foi removido ou desabilitado. Os dois opt-in são o smoke público já existente e o novo E2E, ambos também executados explicitamente nesta etapa. O acesso ao MySQL/navegador exigiu execução fora do sandbox; Playwright emitiu aviso de dependências do host, mas o Chromium executou o E2E e o smoke público.

**Como reproduzir, a partir da raiz:**

```bash
npm --prefix frontend test -- --watch=false
npm --prefix frontend run build
./mvnw test
./mvnw package
./mvnw test -Dtest=PesquisaInformacoesE2eTest -DpesquisaE2e=true
```

O E2E requer MySQL acessível e Chromium instalado para o Playwright Java. Os testes reutilizam servidor/credenciais configurados, mas substituem o catálogo por um banco temporário aleatório antes de iniciar Flyway/JPA. O usuário MySQL precisa de permissão CREATE/DROP DATABASE; sem ela, o teste falha sem recorrer à base da aplicação. Em término forçado da JVM, o catálogo temporário pode permanecer para inspeção/remoção manual pelo nome exato informado nos logs. O build é servido somente pelo harness do teste; não há novos endpoints de teste na aplicação ou novas dependências.

Para repetir as auditorias visuais, iniciar `npm --prefix frontend start -- --host 127.0.0.1 --port 4300` e executar em outro terminal:

```bash
E2E_BASE_URL=http://127.0.0.1:4300 npm --prefix frontend run e2e:informacoes
E2E_BASE_URL=http://127.0.0.1:4300 npm --prefix frontend run e2e:smoke
```

**Somente em uma nova verificação manual controlada**, sem automatizar repetição ou reiniciar a aplicação para contornar cooldown:

```bash
./mvnw -Dtest=GooglePesquisaWebLiveTest -DpesquisaGoogleLive=true test
./mvnw test -Dtest=PesquisaFallbackWebLiveTest -DpesquisaFallbackLive=true
```

**Pendência para o aceite final:** obter acesso permitido por ao menos uma fonte configurada e comprovar a associação correta de URLs reais. O isolamento dos testes CNPJ/blacklist foi concluído e a suíte completa está verde. Não é possível declarar captura real funcionando com a evidência atual.

**Objetivo:** fechar a sprint com evidência automatizada e documentação fiel.

**Entregáveis:**

- Executar testes backend direcionados e `./mvnw test`; gerar o pacote com `./mvnw package` ou `./mvnw verify`.
- Executar testes frontend direcionados, `npm test` e `npm run build`.
- Executar smoke E2E com páginas do Google simuladas, cobrindo: ambos encontrados, somente Instagram, somente site, nenhum resultado, saída/retorno à rota e falha técnica.
- Validar a página em claro/escuro, desktop/mobile, sem overflow e com Axe WCAG A/AA.
- Executar uma verificação manual controlada da pesquisa pública, sem chamar APIs ou registrar conteúdo externo desnecessário.
- Atualizar `API.md`, `fluxo.md`, `HISTORICO_IMPLEMENTACOES.md` e este documento com resultados reais.

**Critérios de aceite:**

- Código, contratos, testes, documentação e mensagens exibidas representam o mesmo comportamento.
- Nenhum teste é removido ou ignorado; falhas preexistentes são diferenciadas de regressões.
- A sprint só recebe status concluído após precisão dos vínculos reais e validações comprovadas. Brave Search API é a fonte ativa; os mecanismos antigos de scraping continuam desativados.

## Ordem de execução

```text
INFO-01.1
    |
    v
INFO-01.2
    |
    v
INFO-01.3
    |
    v
INFO-01.4
    |
    v
INFO-01.5
    |
    v
INFO-01.6
    |
    v
INFO-01.7
```

INFO-01.1 a INFO-01.6 estão concluídas. A INFO-01.7 entregou testes isolados, E2E, integração Brave e refinamento de precisão. A API funciona e a suíte completa passou; a pendência é ampliar o aceite com uma amostra de vínculos reais conhecidos, sem confundir respostas da API com identificação correta do estabelecimento.

## Fora de escopo

- Google Places, Place Details, Custom Search JSON API ou qualquer outra API da Google.
- API paga: a API do Brave Search é usada apenas dentro do crédito gratuito mensal; qualquer cobrança exige nova aprovação.
- Scraping de Instagram, login em rede social, leitura de posts, seguidores, mensagens ou dados privados.
- Disparo automático ou em massa por Instagram ou WhatsApp.
- Pesquisa global em todos os leads ou execução automática após `POST /api/buscas`.
- Alteração de score/temperatura com base na presença de site ou Instagram.
- Uso de LLM para inventar, completar ou escolher URLs sem evidência verificável.
- Persistência de múltiplos perfis/sites candidatos ou histórico detalhado das consultas.
- Fila, RabbitMQ, Kafka, Redis, notificações push, e-mail ou processamento distribuído.
- Autenticação, deploy e multiusuário.

## Definição de pronto

- Fluxo completo iniciado pelo novo botão e restrito aos leads do histórico aberto.
- Nenhuma chamada à Google Places ou a APIs da Google durante o enriquecimento; a fonte principal é a API do Brave Search.
- Instagram capturado quando houver perfil público indexado com correspondência confiável.
- Site/Instagram persistidos somente quando a correspondência supera o limiar de confiança.
- Observações comerciais preservadas e bloco automático atualizado sem duplicação.
- Leads já completos ignorados sem nova pesquisa no Google.
- Links válidos do bloco automático clicáveis de forma segura no detalhe.
- Execução e progresso persistidos, permitindo sair e retornar à página.
- Formatação do bloco e frase de ausência exatamente conforme definido.
- Falhas de internet ou bloqueios não são confundidos com resultado vazio nem apagam observações.
- Testes, builds, smoke, acessibilidade e documentação concluídos com resultados reais.
