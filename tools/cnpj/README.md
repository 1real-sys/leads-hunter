# Importação local dos Dados Abertos do CNPJ

Este diretório transforma os arquivos mensais da Receita Federal em um **recorte de estabelecimentos ativos por município** para o Leads Hunter. O processamento ocorre fora da aplicação. Durante uma busca, o backend consulta apenas o MySQL local; ele não baixa arquivos nem consulta a Receita Federal.

> A base mensal não acompanha o repositório. Sem importar um recorte, a aplicação funciona, mas não encontra CNPJ por correspondência local.

## O que você precisa

- Python 3.11 ou superior, com a biblioteca padrão; o ingestor não exige pacotes Python adicionais.
- MySQL com o schema `leadsradar` criado e as migrations Flyway aplicadas. O [README principal](../../README.md#executar-localmente) explica essa configuração.
- Espaço para os ZIPs de uma competência completa, arquivos temporários e o SQL gerado. Uma carga ampla pode ocupar vários GiB.
- Acesso ao [catálogo oficial dos Dados Abertos do CNPJ](https://dados.gov.br/dados/conjuntos-dados/cadastro-nacional-da-pessoa-juridica---cnpj) e ao [leiaute publicado pela Receita Federal](https://www.gov.br/receitafederal/dados/cnpj-metadados.pdf/@@download/file).

O ingestor usa `Empresas*.zip`, `Estabelecimentos*.zip` e `Municipios.zip` **da mesma competência**. Empresas fornece a razão social; Estabelecimentos traz CNPJ, nome fantasia, situação cadastral e endereço; Municipios relaciona o código usado pela Receita ao nome. O catálogo versionado [`municipios-ibge.csv`](municipios-ibge.csv) associa nome e UF ao código IBGE usado no projeto.

Execute os comandos abaixo **na raiz do repositório**.

## 1. Baixe uma competência e registre as fontes

Crie `tools/cnpj/sources/` e baixe para lá **todos** os lotes `Empresas*.zip` e `Estabelecimentos*.zip` publicados para a competência escolhida, além de `Municipios.zip`:

```bash
mkdir -p tools/cnpj/sources
sha256sum tools/cnpj/sources/*.zip
```

O `sha256sum` deve ser executado depois do download. Confira os nomes e as URLs de origem no catálogo e registre os checksums calculados em um manifesto, por exemplo `tools/cnpj/fontes-2026-08.json`. O formato é:

```json
{
  "dataBase": "2026-08-08",
  "fontes": [
    {
      "tipo": "empresas",
      "arquivo": "Empresas0.zip",
      "url": "https://arquivos.receitafederal.gov.br/dados/cnpj/dados_abertos_cnpj/2026-08/Empresas0.zip",
      "sha256": "COLE_O_SHA256_REAL_DE_64_CARACTERES"
    },
    {
      "tipo": "estabelecimentos",
      "arquivo": "Estabelecimentos0.zip",
      "url": "https://arquivos.receitafederal.gov.br/dados/cnpj/dados_abertos_cnpj/2026-08/Estabelecimentos0.zip",
      "sha256": "COLE_O_SHA256_REAL_DE_64_CARACTERES"
    },
    {
      "tipo": "municipios",
      "arquivo": "Municipios.zip",
      "url": "https://arquivos.receitafederal.gov.br/dados/cnpj/dados_abertos_cnpj/2026-08/Municipios.zip",
      "sha256": "COLE_O_SHA256_REAL_DE_64_CARACTERES"
    }
  ],
  "ufs": ["ES"],
  "municipiosInteresse": [
    {"codigoIbge": "4106902", "nome": "Curitiba", "uf": "PR"}
  ]
}
```

**Este é um modelo, não um manifesto pronto para executar.** Substitua data, URLs e checksums pelos da competência escolhida e repita as entradas de `fontes` para **todos** os lotes numerados de Empresas e Estabelecimentos. Não misture competências.

O recorte pode usar `ufs`, `municipiosInteresse` ou ambos. `"ufs": ["ES"]` seleciona os 78 municípios capixabas; o município de Curitiba no exemplo é acrescentado ao recorte. Os dois campos são opcionais separadamente, mas pelo menos um deles precisa ter itens. O ingestor rejeita municípios sem estabelecimentos ativos na competência escolhida.

## 2. Gere o SQL

Com os ZIPs já baixados e o manifesto completo:

```bash
python3 tools/cnpj/gerar_dataset.py \
  --manifest tools/cnpj/fontes-2026-08.json \
  --source-dir tools/cnpj/sources \
  --output-sql /tmp/cnpj-es.sql \
  --no-json \
  --workers 0
```

Adapte os nomes `fontes-2026-08.json` e `cnpj-es.sql` ao recorte usado. `--source-dir` lê os arquivos locais; sem essa opção, o ingestor baixa as URLs do manifesto. `--workers 0` escolhe automaticamente até oito processos. Use `--workers 1` para reduzir o pico de memória; valores explícitos até 16 são aceitos.

`--no-json` gera somente o SQL. Sem essa opção, o script também escreve `src/main/resources/cnpj/cnpj-subset.json`, a menos que `--output` indique outro caminho. O JSON é um artefato opcional de inspeção; o backend usa as tabelas carregadas no MySQL.

A ferramenta confere o SHA-256 de cada ZIP antes de ler os CSVs, valida o manifesto e mantém somente estabelecimentos com situação cadastral `02` (ativa) nos municípios selecionados. Ao terminar, imprime os totais e o SHA-256 do arquivo produzido. Confira o cabeçalho do SQL, sobretudo a competência e a lista de fontes:

```bash
head -n 30 /tmp/cnpj-es.sql
```

O SQL informa a proveniência de cada fonte, substitui os estabelecimentos dos municípios selecionados, remove empresas sem estabelecimento restante e insere registros em lotes. A mesma carga pode ser executada novamente para o mesmo recorte e competência. **O arquivo não abre uma transação única:** faça backup dos dados locais antes de atualizar uma base importante; uma importação interrompida pode deixar a carga parcial.

## 3. Importe no MySQL

Com o cliente `mysql` instalado no host:

```bash
mysql -u leadhunter -p leadsradar < /tmp/cnpj-es.sql
```

Use o usuário configurado no seu `.env`; o cliente pedirá a senha. Se o MySQL estiver em um contêiner e não houver cliente no host, copie o SQL para o contêiner e execute a importação lá. Substitua `mysql-db` pelo nome do seu contêiner:

```bash
docker cp /tmp/cnpj-es.sql mysql-db:/tmp/cnpj-es.sql
docker exec -it mysql-db sh -c 'mysql -u leadhunter -p leadsradar < /tmp/cnpj-es.sql'
```

A opção `-it` permite informar a senha no terminal enquanto o MySQL lê o SQL copiado dentro do contêiner. Não inclua a senha na linha de comando nem no arquivo SQL.

Confira contagens exatas e as competências presentes:

```bash
mysql -u leadhunter -p leadsradar -e \
  'SELECT data_base, COUNT(*) AS estabelecimentos FROM cnpj_estabelecimento GROUP BY data_base; SELECT COUNT(*) AS empresas FROM cnpj_empresa;'
```

Se usar apenas o cliente do contêiner, execute a mesma consulta com `docker exec -it mysql-db mysql -u leadhunter -p leadsradar -e '...'`. Contagens aproximadas mostradas pelo phpMyAdmin/InnoDB podem divergir; `COUNT(*)` retorna o valor exato.

O arquivo versionado [`R__carregar_subset_cnpj.sql`](../../src/main/resources/db/migration/R__carregar_subset_cnpj.sql) é apenas um placeholder seguro. Não coloque o SQL mensal volumoso, os ZIPs nem dados reais de CNPJ no Git; a carga vive no banco local.

## Validar o ingestor

```bash
python3 tools/cnpj/test_gerar_dataset.py
```

Os testes usam ZIPs pequenos e temporários: não baixam a competência real nem alteram o MySQL. Eles cobrem filtros, checksums, municípios, saída determinística e geração do SQL. O catálogo `municipios-ibge.csv` pode ser reproduzido a partir da fonte geográfica versionada por `python3 tools/cnpj/gerar_municipios_ibge.py`; faça isso somente ao atualizar o catálogo.

## Como a aplicação usa o recorte

O backend cruza município, endereço e nome do lead com as tabelas `cnpj_empresa` e `cnpj_estabelecimento`. Uma correspondência ambígua ou insuficiente não preenche CNPJ. A busca comum tenta esse enriquecimento para leads elegíveis; o detalhe do Histórico oferece a ação explícita **Buscar CNPJ** para os leads daquela busca. Uma nova competência pode levar à revalidação de CNPJs previamente associados. As regras de correspondência estão descritas no [fluxo interno](../../fluxo.md#1-criar-uma-busca).
