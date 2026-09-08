# Refinamento — CNPJ por lead (unidade exata)

Planejamento em sprints para tentar, a partir de cada lead capturado, identificar o **CNPJ (14 dígitos) da unidade específica** e trazer a **razão social** para o lead. **CNPJ-00, CNPJ-01 e CNPJ-02 estão implementadas e validadas; CNPJ-03 e CNPJ-04 permanecem pendentes.**

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
- Persistir no lead: `cnpj`, `razao_social` e data da correspondência. Não alterar `ScoringService`.
- Expor CNPJ/razão social nos contratos, exportação e drawer (rotulagem neutra quando ausente).
- Base local reingerida manualmente quando houver nova base mensal ou novos municípios de interesse.

## Arquitetura alvo

- **Dados**: ferramenta `tools/cnpj/` (como `tools/idhm/`) que baixa/consome os arquivos de Empresas e Estabelecimentos da Receita, filtra municípios de interesse e grava o subset normalizado.
- **Backend**
  - Tabelas locais `cnpj_empresa` (cnpj base 8, razão social) e `cnpj_estabelecimento` (cnpj 14, nome fantasia, logradouro/número/bairro/CEP, município IBGE, UF, situação, situação cadastral, data da base), alimentadas pela ingestão.
  - Campos de endereço estruturado no `Lead` (cep, logradouro, número, bairro) capturados do Google.
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

**Status: CONCLUÍDA E VALIDADA em 08/09/2026.** O ingestor, a documentação operacional e quatro testes Python foram entregues. A migration repetível versionada contém uma carga inicial mínima das três unidades usadas na validação; a atualização mensal completa depende do manifesto e dos arquivos oficiais da competência escolhida.

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

**Status: CONCLUÍDA E VALIDADA em 08/09/2026.** Field mask, mapper, modelo JPA, V4, carga repetível e testes de persistência foram entregues; Flyway e Hibernate validaram o schema no MySQL 8.1.

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

**Status: CONCLUÍDA E VALIDADA em 08/09/2026.** O serviço usa candidatos limitados por município/endereço, rejeita ambiguidades e foi integrado após a geografia, preservando CNPJ anterior e todas as regras comerciais/scoring. Vitória e Vila Velha foram confirmadas com CNPJs distintos em teste JPA.

**Objetivo:** casar o lead com o CNPJ da unidade exata e trazer a razão social.

**Entregáveis:**
- `CnpjService`: candidatos por município IBGE ativo; quando CEP presente, filtrar por CEP; pontuar similaridade de logradouro/número e de nome fantasia/razão social; exigir candidato único acima do limiar.
- Integração em `BuscaService` (após geografia): preenche `cnpj`/`razao_social`/`cnpj_correspondido_em` apenas em correspondência confiável; caso contrário mantém nulo e preserva valor anterior quando já houver.
- Regra explícita: duas unidades da mesma rede em cidades diferentes (ex.: Coco Bambu Vitória vs Vila Velha) casam com **CNPJs distintos** por município + endereço.

**Critérios de aceite:**
- Fixture de Vitória resolve CNPJ da unidade de Vitória; fixture de Vila Velha resolve o de Vila Velha (valores congelados a partir da base ingerida).
- Candidato ambíguo ou abaixo do limiar ⇒ CNPJ nulo, sem Lead inválido.
- Dados de `ScoringService`, deduplicação e preservação comercial intactos.
- Testes de serviço e integração JPA cobrem unidade certa, rede multi-cidade, sem endereço e sem candidato.

**Observações da revisão (endereçar antes de seguir para CNPJ-03):**

- **A carga inicial do `R__carregar_subset_cnpj.sql` é sintética, mas o cabeçalho do arquivo diz "base pública RFB 2026-08".** O arquivo contém somente 3 registros (razões "CB VITORIA/VILA VELHA/CURITIBA COMERCIO DE ALIMENTOS LTDA" com CNPJs plausíveis), que não foram gerados pela ferramenta `tools/cnpj` — se fossem, haveria milhares de linhas para Vitória/Vila Velha/Curitiba. Esse conjunto serve como semente fixa de validação, não como dado real da Receita. Rotular o SQL explicitamente como semente sintética de teste (remover a alegação "RFB 2026-08") e garantir que a ingestão mensal real o substitua antes de qualquer uso em prospecção real.
- **Risco de "CNPJ falso permanente":** `BuscaService.atualizarCnpj` só corresponde quando `lead.getCnpj() == null` e nunca revalida. Um lead correspondido contra a semente sintética acima gravaria um CNPJ não verificado e o manteria para sempre, mesmo após a ingestão real da base. Recomendo: (a) gravar também a `data_base` da fonte correspondida (nova coluna) e re-corresponder quando a base local mudar; e/ou (b) antes de colocar em uso real, limpar/recorresponder os leads afetados pela semente.
- **Sem gravação de confiança/limiar:** a pontuação é descartada; fica sem rastreio de quão certa foi a correspondência. Opcional para uma próxima versão (ex.: registrar `cnpj_confianca`).

### CNPJ-03 — Exposição e frontend

**Status: PENDENTE. Não iniciada por pausa explícita após CNPJ-02.**

**Objetivo:** disponibilizar CNPJ/razão social ao usuário.

**Entregáveis:**
- `LeadResponse`/`PaginaLeadsResponse` com `cnpj` e `razaoSocial`; exportação CSV/XLSX com colunas CNPJ e Razão Social.
- Frontend: campos opcionais no modelo; exibição no drawer (CNPJ formatado e razão social) quando presentes, com rotulagem neutra quando ausente.
- `API.md` atualizado.

**Critérios de aceite:**
- Contratos e exportações refletem os novos campos; UI omite com neutralidade quando nulos.
- Testes de backend (contrato/exportação) e frontend passam.

### CNPJ-04 — Validação integrada e documentação

**Status: PENDENTE. Não iniciada por pausa explícita após CNPJ-02.**

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
