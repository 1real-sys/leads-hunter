# Revisão de Segurança — Leads Hunter

**Última auditoria:** 2026-08-29  
**Última reavaliação de threat model:** 2026-08-29  
**Escopo:** backend completo presente no worktree, incluindo API REST, serviços, persistência, integração Google Places, exportações, tratamento de erros, configuração e dependências resolvidas pelo Maven.  
**Método:** revisão estática do código e dos testes, inspeção da árvore de dependências e confronto com `.opencode/skills/leadradar-security/SKILL.md` e avisos oficiais dos fornecedores. Nenhum teste de invasão ou validação em ambiente publicado foi executado.  
**Estado do produto considerado:** ferramenta pessoal e local, sem intenção atual de exposição pública ou uso por terceiros.

## Threat Model

Esta revisão considera obrigatoriamente o seguinte cenário atual:

- a aplicação roda localmente;
- existe um único usuário, que é o proprietário da máquina;
- o sistema não é um SaaS e não está exposto publicamente;
- não existem usuários externos, contas, tenants, organizações ou papéis distintos;
- autenticação de usuários não é necessária atualmente;
- a máquina e o navegador são controlados pelo proprietário;
- o backend se comunica com a Google Places API quando necessário;
- os dados e o banco MySQL são persistidos localmente;
- arquivos CSV/XLSX exportados podem ser abertos em aplicações externas de planilha.

Ficam fora do cenário atual atacantes da Internet, usuários maliciosos autenticados, acesso entre tenants e comprometimento prévio completo do sistema operacional. Permanecem relevantes falhas acionáveis sem atacante — como indisponibilidade externa, consumo excessivo de memória e crescimento de dados —, conteúdo não confiável recebido da Google, abertura de exports em planilhas e exposição acidental de segredos por Git, backup ou compartilhamento.

A aplicação deve continuar restrita à máquina controlada. O código atual não configura `server.address`; portanto, a premissa de uso exclusivamente local depende também da forma de inicialização e das regras de firewall/rede. Se o processo aceitar conexões de outras máquinas, o threat model deve ser revisto imediatamente.

### Classificação de aplicabilidade

- **REAL:** existe vetor ou falha operacional concreta no cenário local atual.
- **HARDENING:** boa prática defensiva, sem ameaça relevante que justifique prioridade imediata.
- **FUTURO:** passa a ser risco se houver exposição em rede pública, hospedagem ou mudança do modelo de uso.
- **NÃO APLICÁVEL:** pressupõe usuários, papéis ou arquitetura que não existem no cenário atual.

O status `PENDENTE` preserva o acompanhamento histórico. Itens `FUTURO` ou `NÃO APLICÁVEL` não representam trabalho obrigatório enquanto este threat model permanecer válido.

## Resumo dos achados

| ID | Severidade original | Severidade revisada | Aplicabilidade atual | Status | Resumo |
| --- | --- | --- | --- | --- | --- |
| SEC-008 | MEDIUM | MEDIUM | REAL | PENDENTE | Exportação CSV permite injeção de fórmula em planilhas |
| SEC-005 | HIGH | LOW | REAL | PENDENTE | Listagens e exportações não têm limites e podem esgotar memória |
| SEC-007 | MEDIUM | LOW | REAL | PENDENTE | Cliente Google Places não define timeouts explícitos |
| SEC-001 | HIGH | LOW | REAL | PENDENTE | Credencial privilegiada do MySQL está literal na configuração atual |
| SEC-004 | HIGH | LOW | REAL | PENDENTE | Rate limiting não cobre repetições atendidas pelo cache |
| SEC-009 | LOW | LOW | HARDENING | PENDENTE | Dependências resolvidas possuem avisos de segurança publicados |
| SEC-002 | HIGH | INFO | FUTURO | PENDENTE | Endpoints não exigem autenticação no modelo local atual |
| SEC-003 | HIGH | INFO | NÃO APLICÁVEL | PENDENTE | Não existe autorização por recurso, papel ou proprietário |
| SEC-006 | MEDIUM | INFO | HARDENING | PENDENTE | Corpos JSON e campos graváveis têm limites incompletos |
| SEC-010 | LOW | INFO | FUTURO | PENDENTE | Não há baseline de transporte e headers para uma implantação futura |
| SEC-011 | INFO | INFO | HARDENING | PENDENTE | Não há automação de verificações de segurança e testes de controles críticos |

- CRITICAL pendentes: 0
- HIGH pendentes: 0
- MEDIUM pendentes: 1
- LOW pendentes: 5
- INFO pendentes: 5
- Total corrigidos: 0

Totais por aplicabilidade atual:

- REAL: 5
- HARDENING: 3
- FUTURO: 2
- NÃO APLICÁVEL: 1

## Ordem recomendada

Pelo threat model atual, a ordem recomendada é: SEC-008; depois SEC-005, SEC-007, SEC-001 e SEC-004. SEC-009, SEC-006 e SEC-011 são hardening sem urgência. SEC-002 e SEC-010 só devem entrar no backlog se o modelo de exposição mudar. SEC-003 não exige correção enquanto a aplicação permanecer single-user. Os detalhes abaixo permanecem na ordem histórica dos IDs permanentes.

### SEC-001 — Credencial privilegiada do MySQL está literal na configuração atual

**Status:** PENDENTE  
**Severidade:** LOW  
**Severidade original:** HIGH  
**Severidade revisada:** LOW  
**Aplicabilidade atual:** REAL

**Threat model:**  
Não há atacante remoto no cenário atual. O risco concreto é operacional: a credencial pode entrar em um commit, backup ou arquivo compartilhado, e a aplicação local usa uma conta com privilégios maiores que os necessários. Um malware que já controle integralmente a máquina está fora do escopo e não depende deste arquivo para causar dano.

**Arquivo:**  
`src/main/resources/application.yml`

**Local:**  
Configuração `spring.datasource`, linhas 4 a 8.

**Problema:**  
O worktree atual contém usuário administrativo do MySQL e senha escritos diretamente no arquivo versionado de configuração. O valor do segredo não é reproduzido neste documento. A inspeção do Git indicou que essa alteração está local e não foi encontrada no histórico consultado, mas o arquivo é rastreado e pode ser commitado, copiado para artefatos ou reutilizado em outro ambiente por engano. A URL também não declara transporte TLS para uma eventual conexão não local.

**Risco:**  
No uso exclusivamente local, o alcance é limitado ao banco da própria máquina. Ainda assim, um vazamento acidental permite uso da credencial onde ela também for válida, e um erro da aplicação executado como `root` pode afetar mais objetos do MySQL do que deveria. A severidade original HIGH pressupunha exposição/compartilhamento mais amplo; no threat model atual ela é LOW.

**Referência da skill:**  
Seções 6 (proteção da chave e de secrets), 11 (banco e credenciais), 32 (separação de ambientes), 34 (menor privilégio no banco) e 51 (checklist de produção).

**Correção recomendada:**  
Mover a senha para configuração local não versionada ou variável de ambiente e usar um usuário MySQL exclusivo, limitado ao schema da aplicação. Secret manager e TLS de banco não são necessários para este ambiente estritamente local; passam a ser relevantes se o banco sair da máquina. Rotacionar a senha somente se houver indício de compartilhamento ou exposição.

**Impacto esperado da correção:**

- API e contratos: nenhum impacto esperado.
- Autenticação/autorização da API: nenhum impacto direto.
- Banco: nova conta e concessões mínimas; pode exigir ajuste no processo que executa migrations.
- Infraestrutura: pequena mudança na forma local de fornecer a credencial.
- Frontend: nenhum impacto esperado.
- Testes: testes de contexto e integração precisarão receber credenciais próprias do ambiente de teste.
- Comportamento existente: a aplicação deve falhar de forma explícita na inicialização quando o segredo obrigatório não estiver configurado.

---

### SEC-002 — Endpoints não exigem autenticação

**Status:** PENDENTE  
**Severidade:** INFO  
**Severidade original:** HIGH  
**Severidade revisada:** INFO  
**Aplicabilidade atual:** FUTURO

**Threat model:**  
Não existe usuário externo do qual seja necessário proteger os dados, e autenticar o único proprietário não cria uma fronteira de confiança útil. O risco surgiria se o backend passasse a aceitar conexões de dispositivos não confiáveis, fosse hospedado ou se outra pessoa utilizasse a máquina/aplicação. A premissa local deve ser garantida pela interface de rede/firewall, não por JWT desnecessário.

**Arquivo:**  
`pom.xml`  
`src/main/java/dev/jlm/leadshunter/busca/BuscaController.java`  
`src/main/java/dev/jlm/leadshunter/lead/LeadController.java`  
`src/main/java/dev/jlm/leadshunter/exportacao/ExportController.java`

**Local:**  
Dependências, linhas 32 a 88; todos os métodos mapeados nos três controllers.

**Problema:**  
Não existe dependência ou configuração de Spring Security, filtro de autenticação, usuário, sessão ou validação JWT. Isso é coerente com uma ferramenta local e single-user. Qualquer cliente que alcance o processo consegue usar todos os endpoints, mas clientes externos não fazem parte do threat model informado. O código não fixa `server.address`, portanto a restrição à máquina local precisa ser garantida pela configuração efetiva de rede/firewall. Não há JWT mal configurado: JWT simplesmente não foi implementado nem é necessário agora.

**Risco:**  
No cenário atual, o único cliente legítimo e o proprietário dos dados são a mesma pessoa; logo, a ausência de login não cria quebra de confidencialidade entre usuários. O risco passa a existir se a porta ficar acessível a outra máquina ou se o sistema for publicado. A severidade original HIGH assumia essa exposição e foi reduzida para INFO.

**Referência da skill:**  
Seções 2 (proteção de rotas), 8 (rotas mutáveis), 14 (OWASP API), 35 a 49 (autenticação/JWT) e 51 (checklist de produção).

**Correção recomendada:**  
Não implementar autenticação, JWT ou login no cenário atual. Confirmar que o backend aceita conexões somente da própria máquina — por binding em loopback e/ou firewall — e não publicar a porta no roteador, túnel ou container. Se a exposição ou o modelo de usuários mudar, reabrir este item e então escolher autenticação proporcional à arquitetura real; JWT não deve ser adotado por padrão.

**Impacto esperado da correção:**

- API e contratos: nenhum impacto se apenas o acesso de rede for restrito a loopback.
- Autenticação/autorização: nenhuma implementação recomendada atualmente.
- Banco: nenhum impacto.
- Frontend: deve continuar acessando o backend local normalmente.
- Testes: um smoke test pode confirmar acesso local; testes de login/JWT não são necessários.
- Comportamento existente: acesso vindo de outra máquina deixaria de funcionar, o que é coerente com o threat model.

---

### SEC-003 — Não existe autorização por recurso, papel ou proprietário

**Status:** PENDENTE  
**Severidade:** INFO  
**Severidade original:** HIGH  
**Severidade revisada:** INFO  
**Aplicabilidade atual:** NÃO APLICÁVEL

**Threat model:**  
IDOR/BOLA exige ao menos dois contextos de autorização distintos: usuários, organizações, tenants ou papéis. Nenhum deles existe. Todos os leads e buscas pertencem ao único operador da ferramenta, portanto não há recurso “de outro usuário” a ser acessado.

**Arquivo:**  
`src/main/java/dev/jlm/leadshunter/busca/BuscaService.java`  
`src/main/java/dev/jlm/leadshunter/lead/LeadService.java`  
`src/main/java/dev/jlm/leadshunter/exportacao/ExportService.java`  
`src/main/java/dev/jlm/leadshunter/busca/BuscaRepository.java`  
`src/main/java/dev/jlm/leadshunter/lead/LeadRepository.java`

**Local:**  
`BuscaService.listarHistorico` e `buscarHistoricoPorId`, linhas 73 a 99; `LeadService.listar`, `buscarPorId` e `atualizar`, linhas 23 a 63; `ExportService.listarLeads`, linhas 123 a 129; consultas por `id` e listagens globais nos repositories.

**Problema:**  
Os serviços consultam recursos globalmente e aceitam IDs sem contexto de usuário, tenant, organização, propriedade ou papel. Esse desenho é compatível com o domínio single-user atual. Ele somente se tornará BOLA/IDOR se forem introduzidos múltiplos proprietários ou níveis de acesso sem adaptar as consultas.

**Risco:**  
Não há risco de acesso cruzado no cenário atual porque não existem contas nem propriedade separada. A severidade original HIGH descrevia um SaaS/multiusuário inexistente e foi reduzida para INFO. O item permanece registrado para impedir que uma futura mudança de produto reaproveite silenciosamente as consultas globais.

**Referência da skill:**  
Seções 5 (IDOR/BOLA), 14 (Broken Object Level Authorization), 36 (authorization server-side), 45 (multiusuário/tenant) e 48 (roles e permissões).

**Correção recomendada:**  
Não implementar ownership, tenant, roles ou RBAC agora. Manter documentada a premissa single-user. Se o produto mudar para múltiplos usuários, este item deverá ser reclassificado antes da implementação: recursos precisarão de proprietário/tenant e consultas por `id + escopo`, com autorização server-side. Autenticação isolada ou UUID não resolveriam essa futura necessidade.

**Impacto esperado da correção:**

- API, contratos, banco e frontend: nenhum impacto no cenário atual.
- Autenticação/autorização: não devem ser adicionadas para corrigir um risco inexistente.
- Testes: testes de ownership/roles não são aplicáveis atualmente.
- Comportamento existente: as consultas globais são intencionais para o único proprietário.
- Futuro: uma mudança para multiusuário exigirá alterações de API, schema, serviços e testes antes de disponibilização.

---

### SEC-004 — Rate limiting não cobre repetições atendidas pelo cache

**Status:** PENDENTE  
**Severidade:** LOW  
**Severidade original:** HIGH  
**Severidade revisada:** LOW  
**Aplicabilidade atual:** REAL

**Threat model:**  
Sem atacante externo, o vetor plausível é uso acidental ou automação local defeituosa: duplo clique, retry agressivo do frontend ou script em loop. Cache misses atingem uma API com cota/custo; cache hits repetidos continuam gravando novas buscas no banco.

**Arquivo:**  
`src/main/java/dev/jlm/leadshunter/integracao/places/PlacesRateLimiter.java`  
`src/main/java/dev/jlm/leadshunter/busca/BuscaService.java`  
`src/main/java/dev/jlm/leadshunter/busca/BuscaPlacesCache.java`  
`src/main/java/dev/jlm/leadshunter/busca/BuscaController.java`  
`src/main/java/dev/jlm/leadshunter/lead/LeadController.java`  
`src/main/java/dev/jlm/leadshunter/exportacao/ExportController.java`

**Local:**  
`PlacesRateLimiter.executar`, linhas 35 a 40; `BuscaService.criar`, linhas 36 a 58; `BuscaPlacesCache.buscarOuCarregar`, linhas 33 a 38; todos os endpoints de negócio.

**Problema:**  
O único bucket limita chamadas que realmente chegam ao Google Places. Ele é global, fica somente na memória do processo e é consumido dentro do carregador do cache. Repetições de uma busca já em cache não consomem o bucket, mas cada requisição ainda cria uma nova `Busca` e relações no banco. A ausência de rate limiting nos demais endpoints e o comportamento em múltiplas instâncias não são riscos relevantes enquanto o sistema permanecer local e single-user.

**Risco:**  
No cenário local, não há cliente anônimo remoto tentando negar serviço. Porém, repetição acidental pode preencher o histórico, consumir recursos e, com parâmetros diferentes, gastar cota da Google. O bucket atual já reduz rajadas externas; por isso a severidade original HIGH, baseada em abuso público e múltiplas instâncias, foi reduzida para LOW.

**Referência da skill:**  
Seções 7 (custos/DoS), 14 (Unrestricted Resource Consumption), 26 (rate limiting), 31 (integrações externas) e 51 (checklist de produção).

**Correção recomendada:**  
Manter o limitador específico da Google e configurar cotas/alertas no Google Cloud. Para o uso local, priorizar prevenção simples de repetição: desabilitar envio duplicado no frontend, idempotência curta ou limite local apenas no `POST /api/buscas`, incluindo cache hits que persistem dados. Não são necessários rate limit por usuário/IP, gateway ou armazenamento distribuído enquanto houver um único processo local.

**Impacto esperado da correção:**

- API: o `POST /api/buscas` pode responder `429` ou reutilizar uma operação recente, conforme a solução futura.
- Contratos: documentar somente o limite local escolhido.
- Autenticação/autorização: nenhum impacto; identidade não é necessária.
- Banco: pode haver idempotência/deduplicação curta, sem infraestrutura distribuída.
- Frontend: deverá evitar reenvio enquanto uma busca estiver em andamento.
- Testes: cobrir repetição acidental, cache hit/miss e cota externa.
- Comportamento existente: buscas intencionalmente repetidas podem exigir espera curta.

---

### SEC-005 — Listagens e exportações não têm limites e podem esgotar memória

**Status:** PENDENTE  
**Severidade:** LOW  
**Severidade original:** HIGH  
**Severidade revisada:** LOW  
**Aplicabilidade atual:** REAL

**Threat model:**  
Este risco não depende de atacante. O uso normal da ferramenta acumula leads ao longo do tempo, e o próprio proprietário pode solicitar uma listagem ou exportação grande. O vetor é o crescimento legítimo da base somado à construção integral dos arquivos no heap.

**Arquivo:**  
`src/main/java/dev/jlm/leadshunter/lead/LeadService.java`  
`src/main/java/dev/jlm/leadshunter/busca/BuscaService.java`  
`src/main/java/dev/jlm/leadshunter/exportacao/ExportService.java`

**Local:**  
`LeadService.listar`, linhas 23 a 37; `BuscaService.listarHistorico`, linhas 73 a 77; `ExportService.exportarLeads` e `exportarLeadsExcel`, linhas 56 a 120.

**Problema:**  
As listagens carregam todos os registros correspondentes em memória e não oferecem paginação nem teto de resultados. A exportação CSV mantém lista, `StringBuilder` e `byte[]` completos. A exportação XLSX mantém lista, `XSSFWorkbook`, `ByteArrayOutputStream` e resultado final, além de executar `autoSizeColumn` em todas as linhas. Não há limite de quantidade exportada, tamanho resultante, concorrência ou duração.

**Risco:**  
Com crescimento do banco, uma exportação legítima pode consumir grande parte do heap, causar pausas extensas de GC ou `OutOfMemoryError`. No cenário single-user, concorrência hostil e derrubada remota não são plausíveis; o impacto esperado é reiniciar a ferramenta e repetir a operação, sem evidência de perda persistente. Por isso a severidade foi reduzida de HIGH para LOW, embora o risco operacional continue real.

**Referência da skill:**  
Seções 7 (DoS), 14 (Unrestricted Resource Consumption), 27 (paginação e exaustão), 30 (exportações) e 51 (checklist de produção).

**Correção recomendada:**  
Paginar listagens quando o volume real justificar e impor um máximo explícito nas exportações. Para preservar a função pessoal da ferramenta, preferir streaming no CSV e avaliar `SXSSFWorkbook`/remoção de auto-size irrestrito no XLSX. Job assíncrono e controle sofisticado de concorrência só são necessários se medições futuras mostrarem volumes grandes.

**Impacto esperado da correção:**

- API: respostas de listagem poderão mudar para contrato paginado; exportações poderão usar streaming ou rejeitar volumes excessivos.
- Contratos: inclusão de `page`, `size`, metadados e limites documentados.
- Autenticação/autorização: nenhum impacto no cenário single-user.
- Banco: consultas paginadas e, possivelmente, índices adicionais.
- Frontend: adaptação para paginação, se ela for introduzida.
- Testes: cobrir máximos, grandes volumes, memória/concorrência e streaming.
- Comportamento existente: deixa de ser possível obter conjuntos arbitrariamente grandes em uma única resposta síncrona.

---

### SEC-006 — Corpos JSON e campos graváveis têm limites incompletos

**Status:** PENDENTE  
**Severidade:** INFO  
**Severidade original:** MEDIUM  
**Severidade revisada:** INFO  
**Aplicabilidade atual:** HARDENING

**Threat model:**  
Requests são produzidos pelo frontend local e pelo próprio proprietário, não por usuários hostis. O cenário plausível é um bug do frontend, chamada manual equivocada ou dado muito grande colado em observações, causando erro de validação/persistência local — não um ataque remoto de payload.

**Arquivo:**  
`src/main/java/dev/jlm/leadshunter/busca/BuscaRequest.java`  
`src/main/java/dev/jlm/leadshunter/lead/AtualizarLeadRequest.java`  
`src/main/java/dev/jlm/leadshunter/lead/Lead.java`  
`src/main/resources/application.yml`

**Local:**  
`BuscaRequest.categorias`, linhas 33 e 34; `AtualizarLeadRequest.observacoes`, linhas 6 a 14; `Lead.observacoes`, linhas 63 e 64; configuração HTTP, linhas 18 e 19.

**Problema:**  
Há validações adequadas para coordenadas, raio e tamanho do endereço, e o PATCH usa DTO explícito. Porém, `categorias` não limita o número de elementos/duplicatas, `observacoes` não possui `@Size`, e não há política explícita de tamanho máximo do corpo JSON, profundidade/complexidade ou tempo de leitura no servidor/gateway. Uma lista com repetições também é serializada para coluna de 500 caracteres e pode terminar em erro de persistência em vez de `400`.

**Risco:**  
Entradas acidentalmente excessivas podem provocar `500`, aumentar o armazenamento e piorar exports. Sem exposição a clientes não confiáveis, o risco de negação de serviço deliberada é irrelevante; a questão atual é robustez e consistência do contrato. A severidade foi reduzida de MEDIUM para INFO.

**Referência da skill:**  
Seções 7 (DoS), 9 (validação/fuzzing), 14 (Unrestricted Resource Consumption), 15 (DTO/allowlist), 29 (limite de payload) e 51 (checklist de produção).

**Correção recomendada:**  
Como hardening, definir um limite funcional razoável para observações, rejeitar/normalizar categorias duplicadas e alinhar DTO, entidade e schema para retornar `400` em vez de erro de banco. Limites globais de proxy, profundidade JSON e proteção contra payload hostil podem esperar até existir exposição a clientes não confiáveis.

**Impacto esperado da correção:**

- API: entradas acima dos limites funcionais passarão a receber `400`.
- Contratos: novos máximos deverão ser documentados.
- Autenticação/autorização: nenhum impacto direto.
- Banco: redução do risco de truncamento/erro; migration apenas se o limite escolhido exigir alteração da coluna.
- Frontend: campos e seletores deverão aplicar/mostrar os mesmos limites.
- Testes: casos de fronteira e duplicação serão úteis; fuzzing amplo não é prioridade atual.
- Comportamento existente: requisições atualmente aceitas com conteúdo excessivo deixarão de ser aceitas.

---

### SEC-007 — Cliente Google Places não define timeouts explícitos

**Status:** PENDENTE  
**Severidade:** LOW  
**Severidade original:** MEDIUM  
**Severidade revisada:** LOW  
**Aplicabilidade atual:** REAL

**Threat model:**  
A Google Places API e a rede são fronteiras externas mesmo em uma aplicação local. Indisponibilidade, latência extrema ou conexão que não conclui podem ocorrer sem ação maliciosa e bloquear threads enquanto o proprietário usa a ferramenta.

**Arquivo:**  
`src/main/java/dev/jlm/leadshunter/integracao/places/PlacesApiClient.java`

**Local:**  
Construtor, linhas 39 a 60; `executarBusca`, linhas 73 a 114.

**Problema:**  
O `RestClient` é construído diretamente com `RestClient.builder().build()`, sem configuração explícita e versionada de timeout de conexão, timeout de leitura/resposta ou limite de duração da operação. O código traduz falhas de acesso em `503`, mas essa tradução só ocorre depois que a camada HTTP desiste. A resposta esperada do Google tem no máximo 20 resultados, porém não existe uma política explícita de tamanho máximo recebido.

**Risco:**  
Uma conexão lenta, blackhole de rede ou resposta que não termina pode ocupar uma thread da API por tempo excessivo e deixar a busca aparentemente travada. Em uso local, exaustão hostil do pool por alta concorrência é improvável; o proprietário pode interromper/reiniciar a aplicação. A chamada ocorre no fluxo transacional de criação da busca, ampliando a duração total da operação. A severidade foi reduzida de MEDIUM para LOW.

**Referência da skill:**  
Seções 7 (DoS), 14 (Unrestricted Resource Consumption), 31 (integrações externas seguras) e 51 (checklist de produção).

**Correção recomendada:**  
Configurar o request factory do cliente com timeouts explícitos de conexão e leitura, usando valores adequados ao uso local, e manter limite de resposta compatível com o contrato do Google. Circuit breaker e isolamento adicional não são necessários no cenário atual. Manter mensagens externas genéricas e registrar falhas sem incluir a chave ou corpo sensível. Testar timeout de conexão, leitura lenta, resposta inválida e recuperação.

**Impacto esperado da correção:**

- API: falhas lentas passarão a terminar previsivelmente com `503`.
- Contratos: nenhum formato precisa mudar, mas a semântica de timeout deve ser documentada.
- Autenticação/autorização: nenhum impacto direto.
- Banco: transações abortarão mais cedo em indisponibilidade externa.
- Infraestrutura: novos parâmetros de timeout por ambiente.
- Frontend: poderá receber erro mais cedo e aplicar retry com backoff controlado.
- Testes: simulação determinística de conexão e leitura lentas.
- Comportamento existente: chamadas que antes aguardavam o padrão da biblioteca serão interrompidas no limite configurado.

---

### SEC-008 — Exportação CSV permite injeção de fórmula em planilhas

**Status:** PENDENTE  
**Severidade:** MEDIUM  
**Severidade original:** MEDIUM  
**Severidade revisada:** MEDIUM  
**Aplicabilidade atual:** REAL

**Threat model:**  
Os dados de nome/endereço/telefone vêm de estabelecimentos na Google Places e não são controlados pelo proprietário da máquina. Um terceiro pode cadastrar ou alterar conteúdo de um estabelecimento para começar com fórmula; o proprietário então coleta o lead, exporta CSV e abre o arquivo em uma planilha que interpreta fórmulas.

**Arquivo:**  
`src/main/java/dev/jlm/leadshunter/exportacao/ExportService.java`

**Local:**  
`valores`, linhas 157 a 179; `valor`, linhas 181 a 195.

**Problema:**  
O escape atual torna o CSV sintaticamente válido para vírgulas, aspas e quebras de linha, mas não neutraliza valores iniciados por `=`, `+`, `-` ou `@` — inclusive após espaços/controles. Campos vindos do Google Places (`nome`, endereço e telefone) e `observacoes` gravadas pelo PATCH entram no arquivo. Aplicativos de planilha podem interpretar esses valores como fórmulas. O XLSX atual usa `setCellValue(String)` para textos e não foi identificado como vulnerável a essa fórmula específica.

**Risco:**  
Ao abrir um CSV exportado, uma fórmula maliciosa pode executar funções da planilha, induzir requisições externas, exfiltrar conteúdo acessível pela planilha ou apresentar links/comandos enganosos ao operador. Esse fluxo é compatível com o uso real da ferramenta e não depende de o backend ser público; a severidade MEDIUM foi mantida.

**Referência da skill:**  
Seção 30 (segurança de CSV/Excel).

**Correção recomendada:**  
Antes do escape CSV, classificar toda célula textual como não confiável e neutralizar prefixos de fórmula, inclusive após caracteres de espaço, tabulação, CR/LF e outros controles relevantes. Uma estratégia comum é prefixar apóstrofo em valores perigosos, validando o resultado nos softwares suportados. Não remover o escape RFC do CSV. Adicionar testes para todos os prefixos, variações com whitespace, aspas e quebras de linha.

**Impacto esperado da correção:**

- API: o endpoint e o media type permanecem iguais.
- Contratos: alguns valores textuais perigosos terão prefixo de neutralização no CSV.
- Autenticação/autorização: nenhum impacto e nenhuma proteção desse tipo é necessária para eliminar a fórmula.
- Banco: nenhum impacto.
- Frontend: nenhum impacto esperado além do conteúdo baixado.
- Testes: atualizar/adicionar casos de fórmula sem remover os testes de escape existentes.
- Comportamento existente: células perigosas serão exibidas como texto, não avaliadas como fórmula.

---

### SEC-009 — Dependências resolvidas possuem avisos de segurança publicados

**Status:** PENDENTE  
**Severidade:** LOW  
**Severidade original:** LOW  
**Severidade revisada:** LOW  
**Aplicabilidade atual:** HARDENING

**Threat model:**  
O backend não está exposto a atacantes remotos e não usa as configurações/queries exigidas pelos CVEs identificados. A preocupação atual é preventiva: uma versão vulnerável pode tornar-se explorável após mudança futura ou conter outras correções relevantes. Atualizações também trazem risco de regressão para a ferramenta local.

**Arquivo:**  
`pom.xml`

**Local:**  
Parent Spring Boot 4.1.0, linhas 5 a 10, e dependências gerenciadas a partir da linha 32.

**Problema:**  
A árvore Maven resolvida na auditoria contém Tomcat Embedded 11.0.22 e Spring Data JPA 4.1.0. O Tomcat 11.0.22 está em faixas afetadas por avisos posteriores, incluindo CVE-2026-55956 e CVE-2026-65182. Spring Data JPA 4.1.0 é afetado por CVE-2026-47834. O código atual não satisfaz as condições conhecidas de exploração desses casos: não configura constraints de servlet, não usa `@Query(nativeQuery=true)`/`@NativeQuery` e não aceita `Sort` do cliente. Ainda assim, manter versões afetadas deixa uma regressão de configuração/código transformar o risco em explorável e perde correções cumulativas.

Fontes oficiais consultadas:

- [Avisos do Apache Tomcat 11](https://tomcat.apache.org/security-11.html)
- [CVE-2026-47834 — Spring Data JPA](https://spring.io/security/cve-2026-47834/)
- [Spring Boot 4.1.1](https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/)

**Risco:**  
Uma futura ativação de security constraints, query nativa com ordenação externa ou configuração afetada pode expor bypass de autorização ou injeção sem que a versão mude. No código e cenário atuais, não foi identificado caminho de exploração; a severidade LOW foi mantida como hardening de supply chain.

**Referência da skill:**  
Seções 23 (dependências e supply chain), 50 (testes de segurança) e 51 (checklist de produção).

**Correção recomendada:**  
Planejar uma atualização testada para uma release de manutenção do Spring Boot/BOM que gerencie versões corrigidas e compatíveis. Para os avisos citados, validar Spring Data JPA 4.1.1 ou superior e Tomcat 11.0.25 ou superior. Não há justificativa atual para override urgente de dependências isoladas; conferir os pré-requisitos dos avisos e executar a suíte completa antes de atualizar.

**Impacto esperado da correção:**

- API e contratos: nenhum impacto planejado, mas regressões devem ser verificadas.
- Autenticação/autorização: testar especialmente filtros e constraints quando forem adicionados.
- Banco: testar repositories, paginação e migrations após atualização do Spring Data/Hibernate.
- Frontend: nenhum impacto esperado.
- Build: versões transitivas e plugins gerenciados serão alterados.
- Testes: executar suíte completa, integração JPA, controllers e smoke test HTTP.
- Comportamento existente: deve ser preservado; mudanças incompatíveis precisam ser tratadas antes do deploy.

---

### SEC-010 — Não há baseline de transporte e headers para uma implantação futura

**Status:** PENDENTE  
**Severidade:** INFO  
**Severidade original:** LOW  
**Severidade revisada:** INFO  
**Aplicabilidade atual:** FUTURO

**Threat model:**  
HTTPS, reverse proxy, HSTS e headers de produção protegem tráfego entre máquinas/origens e usuários de uma implantação web. O frontend e backend atuais rodam na mesma máquina controlada, sem publicação. Esses controles passam a ser relevantes somente se a aplicação for hospedada, acessada pela rede ou distribuída a terceiros.

**Arquivo:**  
`src/main/resources/application.yml`

**Local:**  
Configuração completa; servidor nas linhas 18 e 19 e datasource nas linhas 4 a 8.

**Problema:**  
Existe apenas uma configuração comum voltada ao uso local. Não há perfil de produção, política documentada de HTTPS/reverse proxy, validação de forwarded headers, HSTS ou headers como `X-Content-Type-Options`, política de cache para respostas sensíveis e `Referrer-Policy`. A aplicação não define CORS; no Spring MVC atual isso mantém a política padrão sem liberação cross-origin, portanto não foi identificado CORS permissivo. Se um frontend em outra origem for introduzido, a allowlist ainda precisará ser explícita e específica por ambiente.

**Risco:**  
Não há risco relevante de interceptação entre cliente e servidor no uso loopback atual. O risco descrito ocorreria em uma implantação futura: tráfego HTTP atravessando rede, proxy interpretado incorretamente ou CORS aberto por conveniência. A severidade original LOW foi reduzida para INFO.

**Referência da skill:**  
Seções 18 (CORS), 19 (headers), 20 (HTTPS), 32 (separação de ambientes) e 51 (checklist de produção).

**Correção recomendada:**  
Não criar configuração de produção, HTTPS, reverse proxy ou CORS agora. Manter CORS sem liberação e a aplicação restrita à máquina local. Se houver decisão concreta de hospedagem/acesso em rede, reavaliar antes do deploy e então definir HTTPS, proxy confiável, headers, CORS por allowlist e TLS do datasource remoto.

**Impacto esperado da correção:**

- API, contratos, banco e frontend: nenhum impacto agora.
- Autenticação/autorização: não são necessárias no cenário atual.
- Testes: não há necessidade de testar uma infraestrutura inexistente.
- Comportamento existente: manter HTTP local e CORS fechado.
- Futuro: uma implantação em rede exigirá configuração e testes próprios antes de disponibilização.

---

### SEC-011 — Não há automação de verificações de segurança e testes de controles críticos

**Status:** PENDENTE  
**Severidade:** INFO  
**Severidade original:** INFO  
**Severidade revisada:** INFO  
**Aplicabilidade atual:** HARDENING

**Threat model:**  
O projeto tem um único mantenedor e não há pipeline público ou equipe distribuída. O vetor plausível é erro operacional: dependência com aviso não percebido, segredo commitado por engano ou regressão em um controle já existente. A ausência de automação não é, isoladamente, uma vulnerabilidade explorável.

**Arquivo:**  
`pom.xml`  
`src/test/java/`

**Local:**  
Plugins Maven, linhas 91 a 141; suíte de testes completa.

**Problema:**  
Não foi identificada automação de SCA/SBOM, secret scanning ou SAST no build/repositório. Os testes existentes cobrem validações funcionais, erros, rate limiter da integração e formato das exportações, mas ainda não cobrem os riscos reais de timeout, volume e fórmula CSV. Testes de autenticação, BOLA/IDOR e infraestrutura de produção não são aplicáveis ao threat model atual. A auditoria local também não encontrou scanners instalados, portanto a checagem de dependências foi manual e não deve ser tratada como inventário exaustivo de CVEs.

**Risco:**  
Novas vulnerabilidades em dependências ou regressões em controles podem entrar sem alerta e permanecer até outra revisão manual. Segredos podem ser commitados acidentalmente. A ausência de testes adversariais torna correções futuras mais fáceis de quebrar silenciosamente.

**Referência da skill:**  
Seções 9 (fuzzing), 23 (supply chain), 50 (testes de segurança) e 51 (checklist de produção).

**Correção recomendada:**  
Como melhoria opcional, usar verificação de dependências e secret scanning compatíveis com o fluxo pessoal, sem criar um pipeline complexo. Ao corrigir riscos reais, adicionar testes proporcionais para timeout, volume/streaming, repetição de busca e fórmula CSV. Não criar testes de login, roles, tenant, CORS de produção ou outros controles que não existem no produto.

**Impacto esperado da correção:**

- API e contratos: nenhum impacto direto.
- Autenticação/autorização: nenhum teste necessário enquanto permanecerem fora do escopo.
- Banco e frontend: nenhum impacto direto.
- Build/CI: maior duração e possibilidade de bloqueio por política de severidade ou segredo.
- Testes: expansão pequena e focada nos controles realmente implementados.
- Comportamento existente: nenhum em runtime; o fluxo de entrega ficará mais rigoroso.

## Controles verificados e itens não aplicáveis no estado atual

Esta seção registra o que foi procurado para evitar que uma sessão futura interprete ausência de achado como ausência de análise.

- **SQL Injection:** não foram encontradas queries nativas, SQL/JDBC construído por concatenação ou ordenação controlada pelo cliente. Os repositories usam métodos derivados, Query by Example e ordenação constante do servidor.
- **Mass assignment:** controllers recebem DTOs/records explícitos; o PATCH permite somente `status`, `observacoes` e `ultimoContatoEm`. Entidades JPA não são usadas como request body.
- **Exposição direta de entidades:** respostas HTTP são DTOs. Os campos comerciais e observações pertencem ao único usuário local; não há exposição entre contas no threat model atual.
- **JWT, senhas e enumeração de usuários:** não há implementação de usuário, login, senha ou JWT, e ela não é necessária no cenário local/single-user. Reavaliar apenas se SEC-002 se tornar aplicável.
- **CORS:** nenhuma configuração permissiva foi encontrada; vale o padrão sem liberação cross-origin. Para o frontend/backend local, não há necessidade atual de abrir origens adicionais.
- **CSRF:** não há autenticação baseada em cookie/sessão no estado atual. Reavaliar e habilitar proteção apropriada se cookies autenticados forem introduzidos.
- **XSS:** o backend não renderiza HTML. `observacoes` continua sendo entrada não confiável e deverá ser escapada pelo frontend conforme o contexto de saída.
- **SSRF:** a URL do Google Places vem da configuração da aplicação, não da requisição HTTP. Não há fetch de URL fornecida pelo cliente.
- **Upload, path traversal e command injection:** não há upload, caminho de arquivo externo nem execução de processo/comando no backend atual.
- **Secrets do Google:** a chave é recebida por `GOOGLE_PLACES_API_KEY`, não está hardcoded e não é registrada pelo código revisado.
- **Cache:** o Caffeine possui expiração e tamanho máximo. O risco restante é o efeito de cache hits sobre persistência/rate limiting, registrado no SEC-004.
- **Tratamento de erros e logs:** o `ApiExceptionHandler` usa formato uniforme, resposta genérica para erros inesperados e não devolve stack trace. O log inesperado registra método, URI e classe da exceção, sem mensagem/corpo/token. Não foi encontrado vazamento claro de dados sensíveis.
- **Excel XLSX:** textos são gravados como células string, não como fórmulas. A injeção identificada e registrada no SEC-008 afeta o CSV.
- **Health check:** `/api/health` retorna apenas `status=UP` e não expõe detalhes internos. Ele não comprova saúde do banco ou Google Places; deve ser tratado apenas como liveness básico.

## Procedimento para correções futuras

Para corrigir qualquer item:

1. Ler esta revisão e a skill de segurança antes da alteração.
2. Alterar somente o item escolhido para `EM CORREÇÃO`.
3. Implementar o menor escopo que elimine a causa, preservando os demais contratos quando possível.
4. Executar os testes relevantes e verificar o cenário de exploração original.
5. Somente então alterar o status para `CORRIGIDO`.
6. Acrescentar ao item, sem remover a descrição original: `Correção aplicada`, `Arquivos modificados` e `Validação`.
7. Atualizar a tabela e os totais do topo.

IDs nunca devem ser reutilizados. Novos achados devem receber o próximo ID disponível, atualmente `SEC-012`.
