# Sprint INFO-01 — Busca interna de site e Instagram no Histórico

**Status: EM IMPLEMENTAÇÃO — INFO-01.1, INFO-01.2 e INFO-01.3 concluídas em 12/09/2026; INFO-01.4 é o próximo passo.**

## Objetivo

Adicionar ao detalhe de uma busca do **Histórico** uma ação manual chamada **"Buscar informações"**, posicionada imediatamente à direita de **"Buscar CNPJ"**. A ação deve pesquisar, somente para os leads vinculados àquela execução, o **site próprio** e o **perfil do Instagram** de cada estabelecimento e persistir o resultado em `observacoes`.

## Decisões de produto já definidas

- A pesquisa será executada por uma rotina interna do backend, acessando a página pública do Google Search sem usar API.
- A rotina não chamará Google Places, Place Details nem qualquer API da Google.
- Não será contratada API de pesquisa, não haverá chave adicional e não haverá consumo de cota de API para esta feature.
- Após a página HTTP simples não entregar resultados, foi aprovado o uso de Chromium headless no servidor. A rotina usa Playwright somente para renderizar a página pública do Google e jsoup para converter o HTML renderizado em DTOs internos.
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

Nesta sprint, **pesquisa interna** significa que o próprio backend montará consultas no Google Search, renderizará a página pública em um navegador headless local e analisará os resultados sem consumir uma API de busca. A rotina continua dependendo de acesso à internet. Sem internet e sem uma base previamente indexada, não é tecnicamente possível descobrir automaticamente uma URL que ainda não existe no banco.

A implementação acessa a página pública do Google Search sem chave, mas isso continua sujeito aos termos, instruções automatizadas e bloqueios definidos pelo Google. O Google identifica pesquisas enviadas por programas como tráfego automatizado; portanto, intervalo e baixa concorrência reduzem agressividade, mas não garantem permissão nem ausência de bloqueio. Como o HTML pode mudar ou o Google pode apresentar captcha/bloqueio, a extração permanece isolada em um componente substituível e validada com fixtures locais. Se o Google bloquear a automação, o sistema reporta falha e entra em cooldown, sem tentar CAPTCHA, evasão ou invenção de resultado.

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

Se já houver observações comerciais, o bloco será acrescentado após uma linha vazia. Em novas execuções, somente o conteúdo entre os delimitadores será substituído. Texto fora deles nunca será apagado. Falha de conexão, timeout, captcha/bloqueio do Google ou página em formato inesperado **não** equivalem a ausência de resultado: o bloco anterior permanece inalterado e o lead entra na contagem de falhas.

## Contexto técnico

- O histórico já usa `GET /api/buscas/{id}` e possui o botão **"Buscar CNPJ"`. O novo fluxo mantém o mesmo grupo de ações, mas terá execução persistida própria para continuar quando o usuário sair da página.
- `BuscaLead` já delimita corretamente os leads pertencentes à busca. O relacionamento continua N:N; não será criado `lead.busca_id`.
- `Lead.observacoes` já é `TEXT` e receberá somente o bloco delimitado. Uma migration será necessária apenas para registrar a execução persistente e seu progresso.
- O `googlePlaceId` poderá compor uma consulta pública ou servir de evidência adicional quando aparecer em um resultado, mas não será enviado à Places API.
- Não será adicionada configuração de API key, cobrança, orçamento ou controle de cota externa.
- Timeouts, quantidade máxima de consultas simultâneas e intervalo mínimo entre acessos continuam necessários para evitar travar a aplicação ou provocar bloqueios. Esses limites são controles internos de estabilidade, não cotas de API.
- A rotina não deve acessar os sites candidatos nem o Instagram para validar conteúdo. A primeira versão classifica somente URL, título e resumo presentes na página do Google Search, reduzindo risco de SSRF e respeitando a proibição de scraping do Instagram.
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
    |       monta consultas no Google e coordena a pesquisa
    |
    +--> GooglePesquisaWebClient
    |       Chromium headless na página pública, sem API key
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

**Status: CONCLUÍDA em 12/09/2026.** O cliente HTTP simples foi substituído, após aprovação, por um navegador Chromium headless. O backend mantém uma única instância reutilizável do browser em uma thread dedicada, cria um contexto isolado por consulta e bloqueia imagens, fontes, mídia e folhas de estilo. Isso preserva a restrição de concorrência, reduz consumo e respeita a exigência de afinidade de thread do Playwright.

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
- A sprint só recebe status concluído após o acesso ao Google Search, a precisão e as validações serem comprovados.

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

INFO-01.1, INFO-01.2 e INFO-01.3 estão concluídas: o navegador renderiza a fonte pública quando disponível, a classificação retorna somente candidato único confiável e a orquestração limita a execução aos vínculos da busca, preserva observações manuais e persiste resultados conclusivos em transações curtas. INFO-01.4 e as etapas seguintes permanecem pendentes; a próxima entrega criará a execução persistente em segundo plano e seu progresso.

## Fora de escopo

- Google Places, Place Details, Custom Search JSON API ou qualquer outra API de pesquisa.
- API key, conta paga ou consumo de cota externa para localizar as URLs.
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
- Nenhuma chamada a API de pesquisa ou Places durante o enriquecimento.
- Instagram capturado quando houver perfil público indexado com correspondência confiável.
- Site/Instagram persistidos somente quando a correspondência supera o limiar de confiança.
- Observações comerciais preservadas e bloco automático atualizado sem duplicação.
- Leads já completos ignorados sem nova pesquisa no Google.
- Links válidos do bloco automático clicáveis de forma segura no detalhe.
- Execução e progresso persistidos, permitindo sair e retornar à página.
- Formatação do bloco e frase de ausência exatamente conforme definido.
- Falhas de internet ou bloqueios não são confundidos com resultado vazio nem apagam observações.
- Testes, builds, smoke, acessibilidade e documentação concluídos com resultados reais.
