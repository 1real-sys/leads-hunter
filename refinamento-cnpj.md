# Refinamento — CNPJ por lead (unidade exata)

Planejamento em sprints para tentar, a partir de cada lead capturado, identificar o **CNPJ (14 dígitos) da unidade específica** e trazer a **razão social** para o lead. **CNPJ-00 a CNPJ-03 estão implementadas e validadas; CNPJ-04 permanece pendente.**

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
3. **Executar a ingestão** com `python3 tools/cnpj/gerar_dataset.py --manifest <manifesto> --source-dir tools/cnpj/sources --output-sql src/main/resources/db/migration/R__carregar_subset_cnpj.sql` — o script valida origem/checksum e gera o subset normalizado dos municípios de interesse.
4. **Carregar/atualizar** as tabelas locais `cnpj_empresa` e `cnpj_estabelecimento` aplicando a migration Flyway repetível gerada, que registra a data da base.
5. **Incluir cidades novas**, se você passou a prospectar outros municípios: adicione o código IBGE delas à lista de interesse antes de rodar.
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

**Status: PENDENTE. Não iniciada.**

**Objetivo:** fechar com validações e registros.

**Entregáveis:**
- Backend `./mvnw test` e build; frontend `npm test` e `npm run build`.
- Revisão ponta a ponta com leads conhecidos (Vitória/ES e Curitiba/PR) verificando CNPJ/razão social corretos da unidade.
- Atualizar: `fluxo.md`, `HISTORICO_IMPLEMENTACOES.md`, `API.md` e este arquivo.

**Critérios de aceite:**
- Suítes e builds passam; documentação fiel ao comportamento real.

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
- Enriquecimento retroativo de leads existentes (a captura cobre novos; retroativo pode ser sprint futura).
- Uso do CNPJ no `ScoringService` (futuro, depende destas colunas).
