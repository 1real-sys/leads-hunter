# Refinamento — Correspondência CNPJ por endereço exato

Planejamento em sprints para destravar a correspondência de CNPJ dos leads que hoje ficam sem CNPJ **mesmo com o estabelecimento presente na base**. O problema não é falta de dado: é a regra de nome, conservadora demais para uma base em que 75% dos estabelecimentos ativos não têm nome fantasia.

## Objetivo

Aumentar o **recall** da correspondência entre `Lead` e `cnpj_estabelecimento` quando o endereço é inequívoco, **sem perder precisão**. Hoje um lead com rua, número e CEP exatamente iguais ao estabelecimento é recusado porque a razão social não se parece com o nome comercial — e, quando `nome_fantasia` está vazio, só a razão social sobra para comparar.

## Diagnóstico reproduzido

### Caso real (lead 653)

| Campo | Lead | Estabelecimento na base |
| --- | --- | --- |
| Nome | `Farmácia São Miguel` | razão social `DROGARIA DE SOUSA ALVES LTDA`; `nome_fantasia` **NULL** |
| Logradouro | `Rua Padre Simão Civalero` | `RUA PADRE SIMÃO CIVALERO` |
| Número | `48` | `48` |
| CEP | `29780000` | `29780000` |
| Município | `3204708` (São Gabriel da Palha) | `3204708` |

O endereço bate, existe **um** estabelecimento ativo naquele endereço e mesmo assim o lead ficou sem CNPJ: `similaridadeNome("farmácia são miguel", "drogaria de sousa alves ltda")` não tem token em comum e fica abaixo de `MINIMO_NOME = 0.45`, então `pontuar` descarta o candidato antes mesmo de comparar endereço.

### Estado da base e dos leads

- Base do ES carregada: `cnpj_empresa = 589.690`, `cnpj_estabelecimento = 608.030` em 78 municípios.
- **75,2% dos estabelecimentos ativos têm `nome_fantasia_normalizado` vazio** (457.353 de 608.030). Ou seja, para 3 em cada 4 não existe nome fantasia para comparar; só a razão social.
- Leads: 192 no total, **10 com CNPJ**; 142 no ES e **132 no ES sem CNPJ**.
- Medição preliminar (SQL conservador, sem a normalização Java, portanto **subestimada**): dos leads ES sem CNPJ com CEP e número, **61 têm ao menos um estabelecimento ativo no mesmo município+CEP+número**; parte relevante tem endereço idêntico ao do lead. O número exato precisa do diagnóstico da CNPJMATCH-00.

### Por que a regra atual recusa

`CnpjService.pontuar` exige, ao mesmo tempo:

- `logradouro >= MINIMO_LOGRADOURO (0.78)`;
- `nome >= MINIMO_NOME (0.45)` — **pré-requisito**, não desempate;
- `pontuacao >= LIMIAR_COM_CEP (0.82)` ou `>= LIMIAR_SEM_CEP (0.90)`.

Como `nome` entra com peso 0.30 (com CEP) e é pré-requisito, uma razão social sem relação derruba o candidato correto. O endereço, que é o dado mais confiável aqui, não consegue compensar.

## Decisões de produto

- **Endereço exato é igualdade após normalização.** Exige CEP válido com oito dígitos, número canônico não nulo e igual nos dois lados e `normalizarLogradouro(lead)` igual ao valor normalizado do candidato. Tolerância a erro de digitação não é `ENDERECO_EXATO`.
- **Nunca afrouxar** CEP, número, município ou `situacao_cadastral = ativa`. A unicidade considera somente candidatos que passam todos esses campos e o logradouro normalizado.
- **Nome vazio não impede o caminho exato.** Um candidato único é aprovado mesmo com nome divergente; com múltiplos candidatos, o nome só pode desempatar quando exatamente um atingir `0.55`.
- **Empate continua recusado.** O diagnóstico classifica como `ENDERECO_DESEMPATADO_POR_NOME` quando o nome resolve a multiplicidade e como `ENDERECO_MULTIPLO` quando não resolve.
- **Registrar a origem da decisão**, para auditoria e para uma futura revalidação. Tanto `ENDERECO_UNICO` quanto `ENDERECO_DESEMPATADO_POR_NOME` persistem `cnpj_origem = ENDERECO_EXATO`; o caminho legado persiste `NOME_ENDERECO`.
- **A regra nova é controlada por política fail-closed.** O caminho exato começa desligado, usa allowlist de municípios/UF e, fora dela, somente o caminho legado é executado. O diagnóstico pode avaliar a regra com o flag desligado, mas o runner só escreve quando a política permite.
- Sem chamada externa, sem custo: tudo roda contra a base local.

## Sprints

### CNPJMATCH-00 — Diagnóstico e amostra rotulada

**Status: IMPLEMENTADA LOCALMENTE; promoção manual pendente.**

**Objetivo:** medir o tamanho real do problema com a **mesma normalização do Java** e montar uma amostra para medir precisão antes de mudar a regra.

**Entregáveis:**

- Teste opt-in somente leitura, sem depender do flag de produção, que usa a base local configurada de verdade, percorre todos os leads sem CNPJ, usa o avaliador compartilhado e compara o resultado legado com o novo. Nesse modo, o inicializador de catálogo isolado não substitui a base, o Flyway fica desligado, o pool JDBC é read-only e o worker de startup é neutralizado.
- A consulta do endereço exato usa município, situação ativa, CEP e `numero_normalizado`, com `Slice.hasNext()` como recusa por truncamento. O número sem CEP também usa `numero_normalizado`.
- Classificação mutuamente exclusiva, nesta ordem: `CONSULTA_TRUNCADA`, `ENDERECO_UNICO`, `ENDERECO_DESEMPATADO_POR_NOME`, `ENDERECO_MULTIPLO`, `NOME_RESOLVE`, `SEM_CANDIDATO`, `SEM_CORRESPONDENCIA`.
- O relatório JSONL registra classificação, `politicaPermitida`, CNPJ, competência, origem, pontuações, gap entre primeiro e segundo candidato, `resultadoLegadoAntes`, `resultadoNovo`, `normalizacaoNumeroAlterada` e origem final. O cabeçalho também inventaria os números brutos descartados dos estabelecimentos CNPJ, agrupados por valor, classificação (`SENTINELA` ou `NUMERO_DESCONHECIDO`) e quantidade.
- Amostra manual de 20 a 30 leads para baseline. A amostra não autoriza promoção; todos os candidatos que seriam aprovados precisam ser revisados antes da escrita.

**Critérios de aceite:**

- Nenhuma escrita no banco e nenhuma chamada automatizada externa.
- O conjunto avaliado deve ser não vazio e o caso 653 obrigatoriamente aparece como `ENDERECO_UNICO`; ausência do lead ou dos dados falha o teste, sem skip por premissa.
- Baseline de precisão atual documentado. A revisão usa `CERTO`, `ERRADO` e `INCONCLUSIVO`; somente `CERTO` entra na promoção, `ERRADO` veta e `INCONCLUSIVO` é reportado como limitação de cobertura.
- A revisão pode consultar fonte pública read-only, registrando URL/evidência. Nome nunca é prova sozinho; o critério é vínculo físico por endereço, telefone, site ou CNPJ.

**Validação:**

```bash
./mvnw -Dtest=CnpjMatchDiagnosticoLiveTest -DcnpjLive=true test
```

Última execução em 22/09/2026: dois testes passaram, nenhum foi ignorado, contra `jdbc:mysql://localhost:3306/leadsradar`, com Flyway desligado e conexão read-only.

### CNPJMATCH-01 — Caminho de endereço exato

**Status: IMPLEMENTADA LOCALMENTE; promoção manual pendente.**

**Objetivo:** aprovar candidato por endereço exato quando ele é único, usando o nome apenas para desempatar.

**Regra proposta (substitui o pré-requisito de nome apenas nesse caminho):**

1. Exige CEP válido, número canônico não nulo e igual e logradouro normalizado igual. A comparação do número usa `numero_normalizado` na base e no lead.
2. Reúne somente candidatos ativos do mesmo município, CEP e número normalizado. Se `Slice.hasNext()` for verdadeiro, recusa por consulta truncada.
3. Se houver exatamente um candidato, aprova como `ENDERECO_UNICO`, mesmo sem nome ou com nome divergente.
4. Se houver mais de um, aprova somente quando exatamente um candidato tiver `max(similaridadeNome(fantasia), similaridadeNome(razão)) >= LIMIAR_DESEMPATE_NOME`, atualmente `0.55`. A classificação é `ENDERECO_DESEMPATADO_POR_NOME`; dois candidatos acima ou nenhum acima recusam. O gap é registrado, e todos os aprovados dessa classificação aguardam revisão manual na primeira promoção.
5. Se o endereço não for exato, mantém o caminho legado, com nome obrigatório e pontuação/limiares atuais.

**Entregáveis:**

- Avaliador puro e determinístico, sem repositório ou transação, compartilhado pela produção e pelo diagnóstico; o diagnóstico também carrega um adaptador versionado do comportamento legado.
- `CnpjService`: aplicar o caminho exato somente quando a política configurável permitir; fora da allowlist, executar apenas o caminho legado. `municipiosPermitidos=null` permite fallback para UFs; uma lista municipal explicitamente configurada, inclusive vazia, tem precedência e ignora UFs.
- V9 com `cnpj_estabelecimento.numero_normalizado`, backfill SQL, índice para o caminho sem CEP e índice para município/situação/CEP/número normalizado. `tools/cnpj` passa a emitir a coluna em cargas futuras.
- Normalização única: trim, maiúsculas, remoção de não alfanuméricos, descarte de sentinelas/valores sem dígito, e remoção de zeros iniciais seguidos de dígito (`048 = 48`, `048A = 48A`, `000A = 000A`).
- Testes em `CnpjServiceTest` e na unidade do normalizador para caso 653, unicidade, desempate, truncamento, número divergente, logradouro divergente, sem CEP, `48/048`, `48A/048A`, `SN` e valores desconhecidos.

**Critérios de aceite:**

- Caso 653 resolve para `51526147000150` sem regra específica por estabelecimento.
- Nenhum caso novo aprovado sem CEP, sem número, fora da política ou com logradouro divergente.
- Nenhum caso de truncamento ou ambiguidade passa a ser aprovado.
- A confiança do CNPJ reutiliza a fórmula atual como evidência, sem forçar valor alto; `ENDERECO_EXATO` pode ter confiança nominal abaixo do limiar por desenho.
- Os testes existentes de ambiguidade/limiar continuam passando, e a mudança de zeros à esquerda no caminho legado é coberta explicitamente.

**Validação:**

```bash
./mvnw -Dtest=CnpjServiceTest,CnpjMatchDiagnosticoLiveTest test
```

### CNPJMATCH-02 — Proveniência da decisão

**Status: IMPLEMENTADA LOCALMENTE; promoção manual pendente.**

**Objetivo:** saber **por que** um lead recebeu CNPJ, para auditar e revalidar.

**Entregáveis:**

- Migration **V10** com `leads.cnpj_origem` (`VARCHAR`) e `CHECK` explícito para `ENDERECO_EXATO` e `NOME_ENDERECO`; V7 e V8 já existem, e a **V9** (`numero_normalizado`) pertence à CNPJMATCH-01.
- `CnpjOrigem` no domínio, `Correspondencia` e `preencherLead` propagam a origem junto com CNPJ, razão social, competência e confiança.
- `LeadResponse` e `BuscaDetalheResponse.LeadHistoricoResponse` expõem `cnpjOrigem` anulável; o frontend apresenta rótulo humano. CSV/XLSX ficam fora desta rodada.
- CNPJs antigos ficam com origem nula. Limpeza zera todos os campos CNPJ e a origem.
- Na revalidação por nova competência: mesmo CNPJ atualiza apenas metadados; CNPJ diferente limpa e encerra sem trocar identidade; sem correspondência limpa como hoje. A configuração não apaga dado existente por si só.

**Critérios de aceite:**

- CNPJs antigos ficam com origem nula e novos preenchimentos sempre têm origem.
- Limpar por competência zera a origem junto com os demais campos.
- Nenhuma mudança no `ScoringService`; a confiança CNPJ continua representando a fórmula de evidência da correspondência.

**Validação:**

```bash
./mvnw -Dtest=CnpjServiceTest,BuscaCnpjServiceTest test
```

### CNPJMATCH-03 — Backfill dos leads existentes e validação

**Status: IMPLEMENTADA LOCALMENTE; promoção manual pendente.**

**Objetivo:** aplicar a regra nova aos leads já capturados e comprovar que não houve acerto errado.

**Entregáveis:**

- O endpoint por histórico continua disponível para uso manual e processa somente leads sem CNPJ. O backfill usa runner opt-in sobre leads distintos sem CNPJ, primeiro em modo relatório.
- O runner gera JSONL imutável; a autorização humana fica em CSV separado, ligado por `leadId + cnpj` e contendo SHA-256 do relatório gerado.
- Antes de consultar ou gravar qualquer lead, `--aplicar` valida a revisão completa de 100% das aprovações permitidas. Linha ausente ou inválida veta a rodada inteira; qualquer `ERRADO` também veta toda a promoção. O resumo registra `CERTO`, `ERRADO`, `INCONCLUSIVO`, pendências e cobertura conclusiva (`CERTO + ERRADO` sobre o total).
- `--aplicar` só aplica linhas `CERTO`, dentro da allowlist, com a mesma competência e a mesma avaliação atual. `INCONCLUSIVO` não autoriza a própria linha nem entra na precisão; CNPJ divergente, competência divergente ou mudança no avaliador são recusados.
- A aplicação é idempotente e limitada: mesmo CNPJ já presente vira `PULADO_IDEMPOTENTE`, CNPJ diferente vira `REJEITADO_CNPJ_DIVERGENTE`, CNPJ nulo é revalidado e aplicado. Cada linha registra código de saída.
- Medir antes/depois, incluindo aprovações `ENDERECO_EXATO`, `ENDERECO_DESEMPATADO_POR_NOME` e aprovações novas causadas por `048 = 48`.
- Preservar status, observações, último contato, score e snapshots históricos; nenhuma escrita em massa fora do enriquecimento de CNPJ.

**Critérios de aceite:**

- Aumento real de leads com CNPJ, sem nenhum falso positivo na amostra conferida.
- Dados comerciais, score e snapshots históricos preservados.
- Nenhuma escrita em massa fora do enriquecimento de CNPJ.

**Validação:**

```bash
./mvnw test
./mvnw -DskipTests package
```

## Riscos e pontos de atenção

- **Endereço com vários CNPJs** (edifício, galeria, shopping): a regra só aprova por endereço quando há um único candidato, ou quando exatamente um nome desempata. Ambiguidade continua recusada.
- **Número ausente ou desconhecido:** `normalizarNumero` retorna `null`; o caminho exato não se aplica. Sentinelas conhecidas são esperadas e outros valores sem dígito são reportados como `NUMERO_DESCONHECIDO`.
- **CEP ou número normalizado com mais de 200 candidatos:** `Slice.hasNext()` provoca recusa segura; não se assume unicidade em consulta truncada.
- **Normalização legada:** remover zeros à esquerda também muda o caminho legado; o diagnóstico preserva o adaptador antigo para comparar antes/depois e revisar todas as aprovações alteradas.
- **Allowlist:** listas de municípios têm precedência sobre UFs. Município não configurado (`null`) permite fallback para UFs; lista municipal explicitamente vazia nega a regra e ignora UFs. Sem configuração aplicável, o caminho novo fica desabilitado. O diagnóstico mede fora da política, mas o runner não escreve.
- **MEI e razão social de pessoa física:** o endereço resolve, mas a confiança deve continuar sendo registrada para o usuário ponderar.
- **Precisão:** nenhuma mudança deve aumentar falsos positivos. A amostra é baseline; a promoção exige revisão de 100% dos aprovados e zero `ERRADO`. `INCONCLUSIVO` não autoriza escrita.
- **Revalidação:** configuração não apaga CNPJ existente. Nova competência confirma e atualiza metadados, ou limpa sem substituir silenciosamente por outro CNPJ.

## Fora de escopo

- Consulta externa à Receita ou qualquer API paga.
- Deduplicação de leads ou mudança no modelo N:N.
- Correspondência fora do município do lead.
- Uso de CNPJ no `ScoringService`.

## Decisões da revisão — perguntas e respostas

Esta seção registra literalmente as decisões tomadas durante o grilling do planejamento. Ela continua sendo a fonte de decisão da implementação; nenhuma decisão abaixo foi reaberta.

### Q1 — O que significa “endereço exato”?

**Pergunta:** Devemos aprovar automaticamente somente quando CEP válido, número normalizado igual e `normalizarLogradouro(lead)` igual ao candidato?

**Resposta:** Sim. Endereço exato é igualdade após normalização nos dois lados. Tolerância a erro de digitação fica para outro nível de evidência e nunca recebe o rótulo `ENDERECO_EXATO`.

### Q2 — Nome ausente no lead

**Pergunta:** Um lead sem nome pode ser aprovado pelo caminho de endereço exato?

**Resposta:** Sim, se houver um único candidato exato. Com múltiplos candidatos exatos e sem nome, recusa. Nome continua pré-requisito somente no caminho legado.

### Q3 — Algoritmo do diagnóstico

**Pergunta:** O diagnóstico deve reutilizar exatamente o avaliador de candidatos da produção?

**Resposta:** Sim. Extrair um avaliador determinístico e puro, sem `@Transactional` e sem repositório, que receba os campos do lead e o candidato e devolva decisão, motivo e pontuação. Produção e diagnóstico usam essa unidade; o diagnóstico também mantém um adaptador legado versionado para a comparação antes/depois.

### Q4 — Barreira para promover a regra

**Pergunta:** Uma amostra manual de 20 a 30 casos autoriza o backfill?

**Resposta:** Não. Ela serve como baseline. Antes da escrita, revisar 100% dos candidatos que a regra aprovaria na base atual, incluindo aprovações alteradas por `048 = 48`, e exigir zero falso positivo. Aprovações fora do ES/allowlist ficam bloqueadas até haver base e revisão.

### Q5 — Escopo do backfill

**Pergunta:** O backfill deve continuar sendo executado por histórico?

**Resposta:** Não como mecanismo principal. Usar runner opt-in, limitado, idempotente e inicialmente em modo relatório, percorrendo leads distintos sem CNPJ. O endpoint por histórico continua para uso manual.

### Q6 — Modelo da origem

**Pergunta:** A origem deve ser um enum persistido como `VARCHAR` com `CHECK`?

**Resposta:** Sim. `CnpjOrigem` terá `ENDERECO_EXATO` e `NOME_ENDERECO`, na migration V10. CNPJs históricos ficam com origem `NULL`.

### Q7 — Auditoria visível

**Pergunta:** A origem deve aparecer na API e no histórico?

**Resposta:** Sim. Expor `cnpjOrigem` anulável em `LeadResponse` e no detalhe do histórico, com rótulo humano no frontend. CSV/XLSX ficam fora desta rodada.

### Q8 — Conjunto de candidatos exatos

**Pergunta:** A unicidade deve contar apenas candidatos ativos que passem por município, CEP, número normalizado e logradouro normalizado? O que fazer quando `Slice.hasNext()` for verdadeiro?

**Resposta:** Sim. Candidatos no mesmo CEP com outro número/logradouro ficam fora da ambiguidade. Consulta truncada recusa, mesmo com um único item na página. O repositório terá consulta por município/situação/CEP/número normalizado para reduzir truncamento.

### Q9 — Nome fortemente conflitante

**Pergunta:** Um candidato único com endereço exato é aprovado mesmo com nome totalmente divergente?

**Resposta:** Sim. O nome não veta o caminho exato; apenas compõe a pontuação/evidência. A origem é `ENDERECO_EXATO`. Esse é o caso da Farmácia São Miguel.

### Q10 — Restrição ao ES

**Pergunta:** A restrição ao ES deve ser um `if` fixo no serviço?

**Resposta:** Não. O matcher é genérico. A captura e o runner usam allowlist configurável por município/UF, inicialmente ES. Fora dela, o caminho novo não é aplicado.

### Q11 — Promoção do relatório para escrita

**Pergunta:** O relatório revisado deve virar uma lista explícita de leads aprovados?

**Resposta:** Sim. A escrita é etapa separada, explícita e idempotente, com `--aplicar`. Antes de escrever, revalida competência e executa novamente o avaliador compartilhado; não confia cegamente no relatório.

### Q12 — Validação da suíte completa

**Pergunta:** A suíte completa deve ser executada em catálogo isolado?

**Resposta:** Sim. Executar testes direcionados no ambiente atual e `./mvnw test` no catálogo MySQL isolado `lh_test_<uuid>`, documentando diferenças sem mascarar falhas novas.

### Q13 — Normalização e consulta do número

**Pergunta:** Como garantir que `048` e `48` sejam tratados como o mesmo número?

**Resposta:** Criar `numero_normalizado` em `cnpj_estabelecimento` na V9, fazer backfill dos registros atuais, indexar e consultar por essa coluna. `tools/cnpj` passa a emitir a coluna nas cargas futuras. Os caminhos com e sem CEP usam `numero_normalizado`.

### Q14 — Habilitação da regra nova

**Pergunta:** O caminho exato deve ser fail-closed?

**Resposta:** Sim. Desligado por padrão; sem valores configurados não há habilitação. Municípios configurados têm precedência sobre UFs; sem municípios, usa UFs; ambas vazias desabilitam. O diagnóstico avalia mesmo com o flag desligado, mas a produção e a escrita respeitam a política.

### Q15 — Desempate nominal

**Pergunta:** Como desempatar múltiplos candidatos exatos?

**Resposta:** Aprovar somente quando exatamente um candidato tiver `max(similaridadeNome(fantasia), similaridadeNome(razão)) >= 0.55`. Dois acima ou nenhum acima recusa. Não há margem adicional nesta rodada; o relatório registra o gap para calibração futura.

### Q16 — Mudança no caminho legado

**Pergunta:** Remover zeros à esquerda também deve alterar o comportamento legado?

**Resposta:** Sim. `048 = 48` e `048A = 48A` em ambos os caminhos. A mudança é documentada como ganho de recall e coberta com regressão para `48`, `048`, `48A`, `048A`, `SN` e `SEM NUMERO`.

### Q17 — Semântica de `cnpj_confianca`

**Pergunta:** O caminho exato deve forçar confiança alta?

**Resposta:** Não. Reutiliza a fórmula atual como evidência e persiste a pontuação resultante, mesmo que fique em torno de `0.60`–`0.70` sem nome. `cnpj_origem=ENDERECO_EXATO` explica a aprovação abaixo do limiar nominal.

### Q18 — Classificação do diagnóstico

**Pergunta:** Como tornar a classificação mutuamente exclusiva?

**Resposta:** Usar esta ordem: `CONSULTA_TRUNCADA`, `ENDERECO_UNICO`, `ENDERECO_DESEMPATADO_POR_NOME`, `ENDERECO_MULTIPLO`, `NOME_RESOLVE`, `SEM_CANDIDATO`, `SEM_CORRESPONDENCIA`. `ENDERECO_MULTIPLO` significa que o desempate foi recusado.

### Q19 — Ground truth manual

**Pergunta:** Como rotular um caso como correto?

**Resposta:** Usar `CERTO`, `ERRADO` ou `INCONCLUSIVO`. Correto significa vínculo físico com o mesmo estabelecimento por endereço, telefone, site ou CNPJ; nome sozinho nunca basta. Só `CERTO` entra na promoção, `ERRADO` veta e `INCONCLUSIVO` fica fora dos dois cálculos, com taxa de cobertura reportada. A revisão pode consultar fonte pública read-only e registra URL/evidência.

### Q20 — Precedência da allowlist

**Pergunta:** O que acontece quando municípios e UFs estão configurados?

**Resposta:** Municípios configurados substituem as UFs. Sem municípios, UFs permitem qualquer município daquela UF. Ambas vazias desabilitam a regra. Valores nulos e listas vazias são distintos, e códigos IBGE devem ter sete dígitos.

### Q21 — Quase-empates

**Pergunta:** Como revisar quase-empates sem criar margem arbitrária?

**Resposta:** Runtime usa somente `0.55` e registra o gap. Na primeira promoção, revisar manualmente 100% dos aprovados `ENDERECO_DESEMPATADO_POR_NOME`. Nenhuma margem fixa nesta rodada; uma margem futura será baseada nos gaps coletados.

### Q22 — Legado sem CEP

**Pergunta:** A V9 deve trocar também a consulta legada sem CEP?

**Resposta:** Sim. O caminho sem CEP usa `findBy...NumeroNormalizado` e índice `(municipio_codigo_ibge, situacao_cadastral, numero_normalizado)`. O caminho exato usa índice adicional `(municipio_codigo_ibge, situacao_cadastral, cep, numero_normalizado)`.

### Q23 — Casos-limite do número

**Pergunta:** Como tratar zeros, sentinelas e valores desconhecidos?

**Resposta:** Aplicar `^0+(?=\d)`: `000 -> 0`, `048A -> 48A` e `000A -> 000A`. Após normalizar, qualquer valor com pelo menos um dígito é número; sem dígito vira `null`. `SN`, `SEM`, `SEMNU`, `SEMNM`, `NAOINF`, `SNR` e `S` são sentinelas esperadas; outros descartes sem dígito viram `NUMERO_DESCONHECIDO` no relatório. O valor `O` não é convertido automaticamente em zero.

### Q24 — Diagnóstico fora da allowlist

**Pergunta:** O diagnóstico deve avaliar leads fora da política?

**Resposta:** Sim. Avalia todos os leads sem CNPJ, mantém a classificação do matcher e registra `politicaPermitida=false`. Somente casos permitidos entram na promoção e no `--aplicar`.

### Q25 — Evidência da revisão manual

**Pergunta:** A revisão pode consultar fontes externas?

**Resposta:** Pode consultar fonte pública read-only manualmente, sem chamadas automatizadas do sistema, registrando URL/evidência. Sem evidência suficiente, o caso é `INCONCLUSIVO`; nome nunca é prova isolada.

### Q26 — Desabilitação posterior da regra

**Pergunta:** Desligar a configuração deve apagar CNPJs já gravados?

**Resposta:** Não. A configuração impede novos preenchimentos, mas não apaga dados existentes. Na nova competência, a revalidação roda o avaliador independentemente do flag: mesmo CNPJ atualiza metadados; endereço sem confirmação limpa; CNPJ diferente limpa e encerra, sem troca silenciosa.

### Q27 — Origem do desempate nominal

**Pergunta:** Um caso `ENDERECO_DESEMPATADO_POR_NOME` deve persistir `cnpj_origem=ENDERECO_EXATO`?

**Resposta:** Sim. A origem distingue o caminho por endereço do caminho legado por nome+endereço. A distinção fina fica somente na classificação do relatório.

### Q28 — Mudanças causadas por zeros à esquerda

**Pergunta:** O relatório deve comparar explicitamente o resultado antes/depois da nova normalização?

**Resposta:** Sim. Registrar `resultadoLegadoAntes`, `resultadoNovo`, `normalizacaoNumeroAlterada` e `origemFinal`. Toda aprovação nova causada por `048 = 48` entra na revisão.

### Q29 — Número alfanumérico desconhecido

**Pergunta:** Valores como `48A` e `A48` devem ser aceitos?

**Resposta:** Sim, qualquer valor normalizado com pelo menos um dígito é aceito (`48A`, `A48`). Quem não tem dígito vira `null`: se estiver no conjunto de sentinelas (`SN`, `SEM`, `SEMNU`, `SEMNM`, `NAOINF`, `SNR`, `S`), é classificado como sem número esperado; caso contrário (`O`, `BLOCOA`, ...) vira `NUMERO_DESCONHECIDO`. `O` não é convertido em zero automaticamente.

### Q30 — Aplicação com estado concorrente

**Pergunta:** Como o `--aplicar` trata um lead alterado depois do relatório?

**Resposta:** Mesmo CNPJ já presente vira `PULADO_IDEMPOTENTE`; CNPJ diferente vira `REJEITADO_CNPJ_DIVERGENTE`; CNPJ nulo é revalidado e aplicado. Divergência de competência ou avaliação rejeita sem alterar. Cada linha registra código de saída.

### Q31 — Formato do relatório

**Pergunta:** O relatório gerado e a revisão humana devem ser arquivos separados?

**Resposta:** Sim. O relatório gerado é JSONL imutável, adequado a campos aninhados; a autorização humana é CSV separado, ligado por `leadId + cnpj`. O CSV carrega o hash do relatório gerado.

### Q32 — Revalidação apontando outro CNPJ

**Pergunta:** O sistema deve substituir imediatamente o CNPJ antigo?

**Resposta:** Não. Mesmo CNPJ apenas atualiza metadados; CNPJ diferente limpa todos os campos CNPJ e encerra; sem correspondência limpa como hoje. O novo preenchimento fica para execução explícita posterior.

### Q33 — Aplicação com estado concorrente no runner

**Pergunta:** Como o runner diferencia idempotência de conflito?

**Resposta:** Mesmo CNPJ autorizado já presente é `PULADO_IDEMPOTENTE`; CNPJ diferente é `REJEITADO_CNPJ_DIVERGENTE`; CNPJ nulo é revalidado e aplicado. Nenhum conflito altera o lead.

### Q34 — Comparação legado versus novo

**Pergunta:** O diagnóstico deve preservar o comportamento antigo após a V9?

**Resposta:** Sim. Produção usa somente a regra nova; o diagnóstico carrega adaptador legado versionado, com normalizador antigo e consultas cruas, para que o antes/depois seja fiel. Métodos e índices necessários ao adaptador não são removidos imediatamente.

### Q35 — Integridade dos artefatos

**Pergunta:** O que o hash deve cobrir?

**Resposta:** SHA-256 dos bytes exatos do JSONL gerado, acompanhado da versão do schema do relatório, competência da base, versão dos normalizadores e fingerprint da allowlist. O `--aplicar` recusa qualquer hash divergente antes de tocar em qualquer lead.
