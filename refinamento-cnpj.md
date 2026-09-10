# Refinamento — CNPJ por lead (unidade exata)

Planejamento em sprints para tentar, a partir de cada lead capturado, identificar o **CNPJ (14 dígitos) da unidade específica** e trazer a **razão social** para o lead. **CNPJ-00 a CNPJ-07 estão implementadas e validadas no respectivo escopo.** Este arquivo registra o plano executado.

## Objetivo

Guardar no Lead o CNPJ e a razão social quando houver correspondência confiável com a unidade capturada — por exemplo, a unidade do Coco Bambu em Vitória e a de Vila Velha têm **CNPJs diferentes**, cada uma com seu endereço. A captura deve ser "certinha": nunca apontar para a matriz ou para outra filial.

## Contexto e limites técnicos (importante)

- **Não existe API pública gratuita de busca reversa "nome → CNPJ".** ReceitaWS, BrasilAPI, CNPJ.ws, CNPJá e OpenCNPJ consultam **por CNPJ** (exigem o número). Elas não resolvem o problema do lead.
- Caminho gratuito real: **espelho local dos Dados Abertos do CNPJ (Receita Federal)** + correspondência reversa offline.
- A base da Receita separa **Empresas** (razão social, chave = 8 primeiros dígitos) de **Estabelecimentos** (cada unidade = CNPJ completo de 14 dígitos com logradouro, número, bairro, CEP, município IBGE, nome fantasia, situação). É isso que permite casar a **unidade**, não a rede.
- A base é **mensal** e grande; guardamos somente um subset indexado (municípios/UF de interesse, estabelecimentos ativos). Requer reingestão periódica (manual, como `tools/idhm/`).
- Correspondência é **best effort**: sem candidato confiável ou com ambiguidade, o lead fica **sem CNPJ** (nunca inventar).
- Precisão da unidade depende do **endereço estruturado** (CEP/rua/número) vindo do Google (`addressComponents`), não de heurística sobre texto.

## Decisões de produto

- Capturar **endereço estruturado** do Google (`places.addressComponents`) para CEP, logradouro, número e bairro.
- Procurar o CNPJ **na captura** (`BuscaService`), logo após a geografia/município, apenas para novos resultados processados.
- Estratégia de correspondência por município (IBGE) → CEP exato quando presente → similaridade de logradouro/número e de nome fantasia/razão social → exigir candidato único acima de limiar.
- Persistir no lead: `cnpj`, `razao_social`, data da correspondência, competência da base e confiança. Não alterar `ScoringService`.
- Expor CNPJ/razão social nos contratos, exportação e drawer (rotulagem neutra quando ausente).
- Base local reingerida manualmente quando houver nova base mensal ou novos municípios de interesse.

## Arquitetura alvo

- **Dados**: ferramenta `tools/cnpj/` (como `tools/idhm/`) que baixa/consome os arquivos de Empresas e Estabelecimentos da Receita, filtra municípios de interesse e grava o subset normalizado.
- **Backend**
  - Tabelas locais `cnpj_empresa` (cnpj base 8, razão social) e `cnpj_estabelecimento` (cnpj 14, nome fantasia, logradouro/número/bairro/CEP, município IBGE, UF, situação, situação cadastral, data da base), alimentadas pela ingestão.
  - Campos de endereço estruturado no `Lead` (cep, logradouro, número, bairro) capturados do Google, além de competência e confiança da correspondência CNPJ.
  - `CnpjService` de correspondência com pontuação e limiar; integração no fluxo de captura.
  - Migration Flyway nova com colunas do lead + tabelas locais.
- **Frontend**: campos opcionais no modelo, exibição no drawer, colunas de exportação.

## Custo e manutenção mensal

**Nenhum custo financeiro adicional.** O Google Places já cobra por requisição — adicionar `addressComponents` ao field mask não muda o valor nem a quantidade de chamadas. A base de CNPJ da Receita Federal é pública e gratuita, e a correspondência roda localmente, sem API paga, captcha ou serviço externo em runtime.

O único trabalho recorrente é a **ingestão mensal manual** da base, feita por você (idealmente uma vez por mês, quando a Receita publica a atualização). O procedimento completo, formato do manifesto e limites de segurança estão em `tools/cnpj/README.md`:

1. **Baixar** a base mensal dos Dados Abertos do CNPJ no portal da Receita Federal (gov.br → Dados Abertos → CNPJ) — arquivos de **Empresas** e **Estabelecimentos**.
2. **Colocar** os arquivos no diretório de fontes esperado pelo ingestor (ex.: `tools/cnpj/sources/`).
3. **Executar a ingestão** com `python3 tools/cnpj/gerar_dataset.py --manifest <manifesto> --source-dir tools/cnpj/sources --output-sql /tmp/cnpj-subset.sql --no-json --workers 0` — o script valida origem/checksum, expande as UFs e gera o subset normalizado.
4. **Carregar/atualizar** as tabelas locais `cnpj_empresa` e `cnpj_estabelecimento` importando o SQL revisado no banco local; a carga mensal volumosa não deve ser commitada.
5. **Incluir regiões novas**, se necessário: adicione a sigla em `ufs` para uma UF completa ou código/nome/UF em `municipiosInteresse` para uma cidade pontual; as duas formas compõem uma união.
6. **(Opcional) Validar**: conferir que leads conhecidos de Vitória/ES, Vila Velha/ES e Curitiba/PR continuam resolvendo o CNPJ da unidade correta.

---

## Sprints

### CNPJ-00 — Dados: fontes e ingestor

**Status: CONCLUÍDA E VALIDADA em 08/09/2026.** O ingestor, a documentação operacional e cinco testes Python foram entregues. A migration repetível versionada é um placeholder sem estabelecimentos e só passa a alimentar o runtime quando for regenerada com o manifesto e todos os arquivos oficiais da competência escolhida.

**Objetivo:** obter e indexar, local e reproduzivelmente, o subset de CNPJ dos municípios de interesse.

**Entregáveis:**
- `tools/cnpj/` com `README.md` (fontes, layout, licença, procedência) e script de ingestão reproduzível (download/fontes fixas + checksum, como `tools/idhm/`).
- Leitura dos arquivos **Empresas** e **Estabelecimentos** (encoding/layout oficiais), filtro por municípios de interesse (ex.: IBGE de Vitória/ES, Vila Velha/ES, Curitiba/PR) e estabelecimentos ativos.
- Normalização (sem acento/minúsculas) de fantasia, razão social, logradouro e bairro; CEP só dígitos.
- Saída determinística (ordenada) consumida pelo backend; teste do parser e da normalização.

**Critérios de aceite:**
- Dataset contém as unidades das cidades de interesse com CNPJ 14, razão social, endereço e município IBGE.
- A ferramenta falha se fonte/checksum mudar; registro de data da base.
- Testes unitários do ingestor passam.

### CNPJ-01 — Backend: endereço estruturado e persistência

**Status: CONCLUÍDA E VALIDADA em 08/09/2026.** Field mask, mapper, modelo JPA, V4, estrutura da carga repetível e testes de persistência foram entregues; Flyway e Hibernate validaram o schema no MySQL 8.1. As três unidades de validação foram isoladas em fixtures transacionais de teste e não são carregadas em runtime.

**Objetivo:** receber endereço estruturado do Google e preparar o modelo.

**Entregáveis:**
- Field mask do Places ganha `places.addressComponents`; mapeamento para um record estruturado (CEP, logradouro, número, bairro) em `PlacesSearchResponse.PlaceResult`.
- Migration (ex.: `V4`): colunas no `Lead` para `cep`, `logradouro`, `numero`, `bairro`, `cnpj` (14), `razao_social` e `cnpj_correspondido_em`; tabelas `cnpj_empresa` e `cnpj_estabelecimento`.
- Carga inicial do subset indexado no backend (a partir da saída da CNPJ-00).
- Atualização dos testes/mocks do cliente Places e das fixtures de endereço.

**Critérios de aceite:**
- Captura grava CEP/logradouro/número/bairro estruturados quando o Google fornecer `addressComponents`.
- Flyway aplica a nova migration com `ddl-auto: validate`.
- Testes unitários/integração do mapper e da persistência passam.

### CNPJ-02 — Backend: correspondência da unidade na captura

**Status: CONCLUÍDA E VALIDADA em 08/09/2026, incluindo as correções da revisão.** O serviço usa candidatos limitados por município/endereço, rejeita ambiguidades e foi integrado após a geografia. Cada correspondência registra competência e confiança; um CNPJ anterior é preservado enquanto a competência municipal não muda e é revalidado quando houver uma carga nova. Vitória e Vila Velha foram confirmadas com CNPJs distintos em teste JPA, sem alterar regras comerciais ou scoring.

**Objetivo:** casar o lead com o CNPJ da unidade exata e trazer a razão social.

**Entregáveis:**
- `CnpjService`: candidatos por município IBGE ativo; quando CEP presente, filtrar por CEP; pontuar similaridade de logradouro/número e de nome fantasia/razão social; exigir candidato único acima do limiar.
- Integração em `BuscaService` (após geografia): preenche `cnpj`/`razao_social`/`cnpj_correspondido_em`/`cnpj_data_base`/`cnpj_confianca` apenas em correspondência confiável; caso contrário mantém nulo. Um valor anterior só é reavaliado quando a competência local do município muda; se a nova base não o confirmar, todos os campos CNPJ são limpos.
- Regra explícita: duas unidades da mesma rede em cidades diferentes (ex.: Coco Bambu Vitória vs Vila Velha) casam com **CNPJs distintos** por município + endereço.

**Critérios de aceite:**
- Fixture de Vitória resolve CNPJ da unidade de Vitória; fixture de Vila Velha resolve o de Vila Velha (valores conferidos nas páginas oficiais das unidades e isolados do runtime).
- Candidato ambíguo ou abaixo do limiar ⇒ CNPJ nulo, sem Lead inválido.
- Dados de `ScoringService`, deduplicação e preservação comercial intactos.
- Testes de serviço e integração JPA cobrem unidade certa, rede multi-cidade, sem endereço e sem candidato.

**Observações da revisão — análise e resolução:**

- **Procede parcialmente — proveniência da antiga carga.** Os três CNPJs não eram sintéticos: as páginas oficiais do Coco Bambu publicam os mesmos números. Porém, o arquivo não havia sido gerado pelo snapshot RFB declarado e não podia ser tratado como subset mensal. Os registros foram removidos do runtime e movidos para `src/test/resources/cnpj/fixtures.sql`; `R__carregar_subset_cnpj.sql` agora é um placeholder explícito até ser regenerado com a competência oficial completa. O SQL produzido pela ferramenta registra competência, URLs e checksums no cabeçalho.
- **Procede — risco de permanência sem revalidação.** A V5 adiciona `cnpj_data_base`, remove os antigos registros bootstrap e limpa enriquecimentos que apontavam para eles. Em novas correspondências, o lead recebe a competência escolhida; quando a base ativa daquele município muda, `BuscaService` tenta corresponder novamente e limpa o CNPJ anterior se a nova competência não o confirmar.
- **Aplicada — confiança da correspondência.** A pontuação aprovada passa a ser persistida em `cnpj_confianca` com quatro casas decimais e restrição entre 0 e 1. O campo permanece interno nesta etapa e só deverá entrar nos contratos caso uma decisão de produto o exija em CNPJ-03.

### CNPJ-03 — Exposição e frontend

**Status: CONCLUÍDA E VALIDADA em 08/09/2026.** `LeadResponse` e sua paginação expõem CNPJ/razão social sem publicar confiança ou data-base; CSV/XLSX incluem as duas colunas, e o drawer formata os 14 dígitos e representa ausências sem sugerir uma correspondência inexistente.

**Objetivo:** disponibilizar CNPJ/razão social ao usuário.

**Entregáveis:**
- `LeadResponse`/`PaginaLeadsResponse` com `cnpj` e `razaoSocial`; exportação CSV/XLSX com colunas CNPJ e Razão Social.
- Frontend: campos opcionais no modelo; exibição no drawer (CNPJ formatado e razão social) quando presentes, com rotulagem neutra quando ausente.
- `API.md` atualizado.

**Critérios de aceite:**
- Contratos e exportações refletem os novos campos; UI omite com neutralidade quando nulos.
- Testes de backend (contrato/exportação) e frontend passam.

### CNPJ-04 — Validação integrada e documentação

**Status: CONCLUÍDA E VALIDADA em 08/09/2026.**

**Objetivo:** fechar com validações e registros.

**Entregáveis:**
- Backend `./mvnw test` e build; frontend `npm test` e `npm run build`.
- Revisão ponta a ponta com leads conhecidos (Vitória/ES e Curitiba/PR) verificando CNPJ/razão social corretos da unidade.
- Atualizar: `fluxo.md`, `HISTORICO_IMPLEMENTACOES.md`, `API.md` e este arquivo.

**Critérios de aceite:**
- Suítes e builds passam; documentação fiel ao comportamento real.

**Resultado:** o backend passou com 154 testes e gerou o pacote executável; o frontend passou com 197 testes e build de produção sem warnings. A revisão integrada das unidades conhecidas usa as fixtures transacionais (exclusivas de teste) e confirma a resolução por unidade — Vitória/ES e Vila Velha/ES casam com CNPJs distintos e Curitiba/PR com o seu — sem chamadas à Receita em runtime. `fluxo.md`, `HISTORICO_IMPLEMENTACOES.md` (entrada 53), `API.md` e este arquivo foram sincronizados. A migration `R__carregar_subset_cnpj.sql` permanece como placeholder vazio: a resolução real de CNPJ passa a valer depois que esse arquivo for substituído pela saída revisada do `tools/cnpj` na ingestão mensal da competência RFB (passo a passo em "Custo e manutenção mensal").

### CNPJ-05 — Exibir CNPJ no detalhe do histórico

**Status: CONCLUÍDA E VALIDADA NO ESCOPO em 08/09/2026.**

**Objetivo:** ao abrir uma busca no **Histórico**, exibir o CNPJ de cada estabelecimento logo **abaixo do endereço**, na coluna "Estabelecimento" do detalhe. Quando o CNPJ estiver `null` no banco, mostrar **"CNPJ não encontrado"**; quando existir, exibi-lo normalmente (formatado `00.000.000/0000-00`).

**Contexto anterior (antes desta sprint):**
- O detalhe do histórico (`frontend/src/app/features/historico/historico-detalhe-page.html`) renderizava por lead nome, categoria e `enderecoFormatado` na coluna "Estabelecimento".
- O contrato desse endpoint ainda não expunha CNPJ: `BuscaDetalheResponse.LeadHistoricoResponse` (Java) não tinha `cnpj`, `BuscaService.toLeadHistoricoResponse` não o preenchia e `LeadHistoricoResponse` (TS, `busca.model.ts`) tampouco tinha o campo.
- O `cnpj` deveria refletir o estado **atual** do `Lead` (não um snapshot da execução), como já acontecia com status/observações/último contato no histórico.

**Entregáveis:**
- Backend: adicionar `cnpj` em `BuscaDetalheResponse.LeadHistoricoResponse` e preencher em `BuscaService.toLeadHistoricoResponse` com `lead.getCnpj()`; atualizar construtores/assertions de testes que montam esse record.
- Frontend: `cnpj?: string | null` em `LeadHistoricoResponse` (TS); na página de detalhe do histórico, linha abaixo do endereço com o CNPJ formatado quando presente e **"CNPJ não encontrado"** quando ausente/nulo, usando o util `formatarCnpj` já existente (`shared/utils/cnpj.ts`).
- `API.md`: exemplo do detalhe de busca com o novo campo; notas de docs (`fluxo.md`, `HISTORICO_IMPLEMENTACOES.md` e este arquivo).

**Critérios de aceite:**
- Lead do histórico com CNPJ persistido exibe o CNPJ formatado abaixo do endereço.
- Lead com `cnpj = null` exibe "CNPJ não encontrado" (sem inventar valor).
- Respostas antigas sem o campo continuam funcionando (campo opcional no TS).
- Testes de backend (contrato/montagem do record) e de frontend (página de detalhe) passam; builds sem warnings.

**Resultado:** `GET /api/buscas/{id}` passou a expor o CNPJ atual do lead, e o detalhe do Histórico o apresenta abaixo do endereço com formatação ou fallback neutro. Os 18 testes backend diretamente afetados e os 8 testes da página passaram; a suíte frontend passou com 198 testes, e os builds backend e frontend concluíram sem warnings da aplicação. A suíte backend completa executou 154 testes, mas terminou com três falhas e três erros preexistentes, causados por dados persistidos no MySQL local e pela carga CNPJ local já populada enquanto testes de integração ainda esperam o placeholder vazio.

### CNPJ-06 — Ingestão por UF inteira, rápida e leve (`tools/cnpj`)

**Status: CONCLUÍDA E VALIDADA em 08/09/2026.**

**Objetivo:** permitir carregar **toda uma UF** (ex.: `ES`) com o mínimo de esforço, tempo e memória. Antes o manifesto exigia listar município por município; a sprint adiciona a forma `{"ufs": ["ES"]}` (expansão automática para todos os municípios da UF) e otimiza o ingestor para ser **rápido e leve** — lema da sprint. Isso resolve carregar o ES inteiro sem listar os 78 municípios manualmente e mantém o fluxo mensal simples.

**Especificação funcional:**
- Manifesto passa a aceitar também `"ufs": ["ES"]` (uma ou mais UFs), mantendo `municipiosInteresse` para recortes pontuais; pelo menos uma das duas formas; se ambas, é a união. Validação de UF com 2 letras.
- Expansão de UF → municípios usando o mapeamento IBGE (código + nome + UF) já presente no projeto. **Decisão de independência:** gerar e versionar um CSV pequeno e dedicado (`tools/cnpj/municipios-ibge.csv`, 5.570 linhas, derivado de `src/main/resources/geo/municipios-idhm.json`) em vez de acoplar o tool ao artefato da feature IDHM; o CSV vira a fonte única de expansão.
- Casar os nomes da Receita (`Municipios.zip`) com o mapeamento IBGE pela chave normalizada `(uf, nome sem acento)`; se algum município da UF não casar, falhar listando os divergentes para ajuste, nunca silenciar.

**Otimizações (rápido e leve):**
- **Single-pass streaming:** abrir cada zip uma única vez e percorrer os registros sem descompactar tudo em disco; descartar a linha **cedo** (checagem de UF/município via lookup em dicionário) antes de normalizar fantasia/logradouro/bairro dos registros fora do alvo.
- **Pré-índices:** construir uma única vez o mapa de alvos `(uf, nomeMunicipioNormalizado) → codigoIbge` e o conjunto de nomes dos municípios da UF, evitando normalização repetida por linha.
- **Empresas enxutas:** coletar apenas os CNPJ-base que pertencem a estabelecimentos alvo e processar `Empresas` filtrando por esse conjunto, sem reter razões sociais fora do recorte.
- **Menos alocação no hot path:** reutilizar normalizações baratas (sem regex desnecessária por linha quando o município já foi descartado); avaliar `zipfile` com leitura por bloco.
- **Paralelismo (decisão da sprint):** avaliar processar lotes de arquivos em paralelo respeitando saída determinística; só adotar se houver ganho real no hardware-alvo (Ryzen 7 5700X, 16 threads) sem complicar o streaming.
- **Artefatos leves:** flag `--no-json` para gerar só o SQL (evitar o JSON intermediário de dezenas de MB quando não for necessário); SQL em lotes e determinístico; documentar que a base carregada vive no banco e **não** é commitada mensalmente (o `R__` local de dezenas de MB fica fora do Git — ver política de artefatos).

**Entregáveis:**
- CSV `tools/cnpj/municipios-ibge.csv` + gerador/verificação (ou documento de origem) — leve, commitado.
- Suporte a `ufs` no manifesto, expansão e filtro por UF no `gerar_dataset.py`; `README` atualizado com o fluxo mensal por UF.
- Otimizações de hot path acima + micro-benchmark/smoke de carga com o recorte ES real registrando **tempo e pico de memória (RSS)**.
- Testes Python: expansão de UF (ES → 78 municípios com IBGE correto), compatibilidade com `municipiosInteresse`, determinismo, UF inválida/inexistente, município sem registros e erro de nome divergente.

**Critérios de aceite:**
- Manifesto com `{"ufs": ["ES"]}` gera as unidades ativas de **todos** os municípios do ES com `codigoIbge` IBGE correto e razão social das empresas correspondentes.
- Duas execuções com as mesmas fontes produzem os **mesmos bytes** (JSON e SQL).
- O SQL gerado limpa apenas os municípios das UFs alvo (DELETE por `municipio_codigo_ibge` da UF).
- Smoke de carga ES (mesmas fontes de 2026-08) conclui em poucos minutos com pico de memória estável e baixo (meta orientativa: pico < 2 GiB); números registrados no relatório da sprint.
- Suíte Python passa; README documenta o fluxo mensal por UF e a política de artefatos leves.

**Fora de escopo:** mudanças no backend/contratos; parse multi-região; busca por APIs; alteração do modelo de dados; resolução dos três erros preexistentes da suíte integrada (causados pela base CNPJ já populada no MySQL local — tratados em tarefa própria).

**Resultado:** o manifesto aceita `ufs` e/ou `municipiosInteresse`, e `"ufs": ["ES"]` expande os 78 municípios capixabas a partir do catálogo dedicado e reproduzível de 5.570 registros. A reconciliação usa município normalizado e UF, interrompe a carga diante de divergências e o SQL limpa somente os códigos IBGE selecionados. O hot path passou a descartar registros antes das normalizações caras, reter apenas empresas necessárias, processar arquivos em streaming com paralelismo limitado e escrever JSON/SQL atomicamente sem construir uma segunda cópia textual completa; `--no-json` elimina o intermediário opcional.

No Ryzen 7 5700X, com Python 3.14.7 e as 21 fontes locais completas de `2026-08-08`, o smoke real do ES gerou 589.690 empresas e 608.030 estabelecimentos dos 78 municípios. Com 8 workers concluiu em 113,21 s e atingiu pico RSS agregado de 1.541,53 MiB; com 1 worker levou 245,21 s e 1.011,41 MiB. A saída SQL dos dois modos teve os mesmos 168.526.384 bytes e SHA-256 `da5caa912ecb5c3322d57a4cc9246548d0c9e942da424c0c5488b25d2aa6251a`, justificando o padrão automático de até 8 processos com opção sequencial para ambientes de menor memória. Os 13 testes Python e a compilação dos quatro scripts passaram. Nenhum arquivo Java, contrato ou comportamento do backend foi alterado nesta sprint; o Java 25 permanece no backend, enquanto o ingestor otimizado executa em Python fora da JVM.

### CNPJ-07 — Botão "Buscar CNPJ" no detalhe do histórico (backfill sob demanda de uma busca)

**Status: CONCLUÍDA E VALIDADA NO ESCOPO em 09/09/2026.**

**Objetivo:** no detalhe de uma busca no **Histórico** (que lista os leads capturados naquela execução), oferecer um botão **"Buscar CNPJ"** ao lado direito de **"Voltar ao histórico"**. Ao clicar, o backend tenta, **localmente** (nas tabelas `cnpj_*` já carregadas), casar o CNPJ de cada lead daquela busca que **ainda não tem CNPJ**; leads já preenchidos são **ignorados**. O CNPJ/razão social encontrados são persistidos no lead, e a tela é recarregada para exibi-los (a CNPJ-05 já mostra o CNPJ abaixo do endereço).

**Contexto (como está hoje):**
- O detalhe (`historico-detalhe-page.html`) tem o link "Voltar ao histórico" no header (linha ~7) e lista os leads da busca.
- A correspondência já existe e é local e barata: `CnpjService.corresponder(Lead)` resolve unidade por município/endereço/nome com limiar; o preenchimento na captura vive em `BuscaService` (revalida quando a base do município muda).
- Leads antigos sem CNPJ podem faltar endereço estruturado/município — nesses casos não há candidato e o resultado é "sem correspondência".

**Entregáveis:**
- Backend — endpoint novo, ex.: `POST /api/buscas/{id}/cnpj` (ou `/corresponder-cnpj`):
  - valida a busca (`404` no padrão), carrega os leads daquela execução via `BuscaLead`;
  - para cada lead com `cnpj` nulo, chama `cnpjService.corresponder` e, se confiável, persiste `cnpj`/`razao_social`/`cnpj_correspondido_em`/`cnpj_data_base`/`cnpj_confianca` (mesma regra da captura);
  - resposta com resumo `{ totalLeads, ignoradosJaComCnpj, encontrados, semCorrespondencia }`; sem chamada externa e sem novo rate limit.
  - **Refactor pequeno:** extrair o preenchimento confiável do lead em método reutilizado pela captura (`BuscaService`) e pelo novo serviço, sem mudar o comportamento atual (revalidação por mudança de base permanece como está).
- Frontend: botão **"Buscar CNPJ"** no header do detalhe, ao lado de "Voltar ao histórico"; estados de carregando/sucesso/erro; ao concluir com sucesso, recarregar o detalhe para exibir os CNPJs encontrados (via CNPJ-05). Desabilitar enquanto roda.
- Testes: serviço (ignora preenchido, encontra, sem correspondência, busca inexistente), controller HTTP (`200` resumo / `404`), integração com uma busca contendo lead sem CNPJ (resolve) e lead já preenchido (ignorado); spec da página (botão presente ao lado do voltar, chamada e estados).
- Docs: `API.md` (endpoint + exemplo de resposta), `fluxo.md`, `HISTORICO_IMPLEMENTACOES.md` e este arquivo.

**Critérios de aceite:**
- Ao clicar em "Buscar CNPJ" numa busca com leads sem CNPJ (e base carregada para o município), os leads correspondidos passam a exibir CNPJ/razão social no detalhe após recarregar.
- Leads já com CNPJ não são tocados (ignorados e contabilizados).
- Leads sem endereço estruturado/município ou sem candidato confiável ficam sem CNPJ e entram em "sem correspondência" (sem inventar valor).
- Resposta e UI são neutras e seguras; testes backend/frontend e builds passam.

**Fora de escopo:** rodar a varredura em todas as buscas de uma vez ou retroativamente no banco todo; revalidar/recalcular leads que já têm CNPJ nesta ação; processo assíncrono/fila; resolução dos erros preexistentes da suíte integrada.

**Resultado:** `POST /api/buscas/{id}/cnpj` valida a busca e processa seus vínculos em transação, preenchendo somente leads sem CNPJ por correspondência local confiável. O preenchimento dos cinco campos foi extraído para `Correspondencia.preencherLead`, reutilizado pela captura sem mudar a revalidação por competência. O botão à direita do retorno ao histórico bloqueia duplicatas, informa carregamento/resumo/erro e recarrega o detalhe, que também passou a expor e mostrar a razão social atual. Leads fora da busca, preenchidos ou sem correspondência permanecem preservados.

Passaram 41 testes backend selecionados, incluindo integração JPA isolada com flush/releitura e repetição da ação, 201 testes frontend e ambos os builds. O smoke Firefox com API controlada confirmou a atualização em desktop/mobile (1440 × 1000 e 390 × 844), sem overflow nem violações Axe WCAG A/AA. A suíte backend completa executou 161 testes, com 155 aprovados e as mesmas três falhas/três erros preexistentes de blacklist e fixtures na base local populada; nenhum teste novo falhou. Essas falhas permanecem fora do escopo. A primeira tentativa de integração foi bloqueada pela rede do sandbox e passou após execução com acesso ao MySQL; uma expectativa do teste frontend foi ajustada ao fallback seguro de erros já existente.

---

## Critérios de aceite gerais

- CNPJ sempre da **unidade** (14 dígitos), nunca inventado nem da matriz quando houver filial com endereço distinto.
- Sem candidato confiável ⇒ `cnpj`/`razao_social` nulos.
- Nenhuma API paga/captcha; somente base pública + dados Google `addressComponents`.
- Comportamento de busca, deduplicação, geografia, IDHM, scoring e blacklist preservado.

## Riscos e mitigações

- **Defasagem da base CNPJ (mensal):** registrar `cnpj_correspondido_em` e a data da base; reingestão manual periódica documentada.
- **Nome do Google ≠ fantasia/razão:** usar endereço (CEP/rua/número) como discriminante forte + nome só como reforço.
- **CEP ausente no `addressComponents`:** caos menor — recuar para município + logradouro + número + nome com limiar maior.
- **Falso positivo por rede com filiais na mesma rua/cidade:** exigir unicidade e endereço; ambiguidade ⇒ nulo.
- **Volume/localidade:** manter apenas municípios de interesse; crescer conforme novos leads.

## Fora de escopo

- Busca por APIs pagas ou captcha; scraping de consulta pública.
- Dados de sócios, CNAE, situação cadastral detalhada, matriz/filial além do necessário para identificação.
- Enriquecimento retroativo global de leads existentes (CNPJ-07 permite somente a ação manual por busca para leads sem CNPJ).
- Uso do CNPJ no `ScoringService` (futuro, depende destas colunas).
