# Subset local dos Dados Abertos do CNPJ

Este diretório contém o ingestor one-off da CNPJ-00. Ele transforma os arquivos mensais da Receita Federal em um JSON pequeno, ordenado e consumível pelo backend. A aplicação não baixa nem consulta CNPJ em runtime.

## Fonte, procedência e licença

- Catálogo oficial: [Cadastro Nacional da Pessoa Jurídica — CNPJ](https://dados.gov.br/dados/conjuntos-dados/cadastro-nacional-da-pessoa-juridica---cnpj).
- Órgão responsável: Secretaria Especial da Receita Federal do Brasil.
- Metadados e leiaute: [CNPJ — Metadados](https://www.gov.br/receitafederal/dados/cnpj-metadados.pdf/@@download/file).
- Arquivos consumidos: `Empresas*.zip`, `Estabelecimentos*.zip` e `Municipios.zip` da mesma competência mensal.
- Formato oficial: CSV sem cabeçalho, separado por ponto e vírgula, delimitado por aspas e codificado em Latin-1.
- Licença/uso: dados cadastrais públicos disponibilizados conforme a política de Dados Abertos do Governo Federal. A origem Receita Federal deve ser preservada ao redistribuir o recorte.

`Empresas` fornece o CNPJ básico e a razão social. `Estabelecimentos` fornece ordem/dígitos verificadores, nome fantasia, situação cadastral, endereço, UF e o código municipal interno da Receita. `Municipios` converte esse código interno em nome; o manifesto associa nome + UF ao código IBGE usado pelo Leads Hunter.

## Segurança e reprodutibilidade

O manifesto é obrigatório e congela `dataBase`, municípios, URL HTTPS e SHA-256 de cada arquivo. O ingestor:

- aceita download somente dos hosts oficiais declarados no código;
- rejeita redirecionamento para outro host, nomes de arquivo/caminhos inseguros, ZIPs com quantidade ou tamanho fora dos limites e CNPJ/CEP inválidos;
- calcula o SHA-256 antes de ler qualquer CSV e para se a fonte mudar;
- mantém somente estabelecimentos com situação cadastral `02` (ativa);
- exige pelo menos uma unidade ativa em cada município configurado;
- grava o resultado atomicamente e sem timestamp variável.

Os arquivos brutos são grandes e não devem ser versionados. `tools/cnpj/sources/` está ignorado pelo Git.

## Preparar a competência mensal

1. No catálogo oficial, escolha uma única competência e baixe todos os arquivos `Empresas*.zip`, `Estabelecimentos*.zip` e `Municipios.zip` para `tools/cnpj/sources/`.
2. Crie um manifesto JSON revisado, por exemplo `tools/cnpj/fontes-2026-08.json`:

```json
{
  "dataBase": "2026-08-08",
  "fontes": [
    {
      "tipo": "empresas",
      "arquivo": "Empresas0.zip",
      "url": "https://arquivos.receitafederal.gov.br/dados/cnpj/dados_abertos_cnpj/2026-08/Empresas0.zip",
      "sha256": "SHA256_DE_64_CARACTERES_REVISADO"
    },
    {
      "tipo": "estabelecimentos",
      "arquivo": "Estabelecimentos0.zip",
      "url": "https://arquivos.receitafederal.gov.br/dados/cnpj/dados_abertos_cnpj/2026-08/Estabelecimentos0.zip",
      "sha256": "SHA256_DE_64_CARACTERES_REVISADO"
    },
    {
      "tipo": "municipios",
      "arquivo": "Municipios.zip",
      "url": "https://arquivos.receitafederal.gov.br/dados/cnpj/dados_abertos_cnpj/2026-08/Municipios.zip",
      "sha256": "SHA256_DE_64_CARACTERES_REVISADO"
    }
  ],
  "municipiosInteresse": [
    {"codigoIbge": "3205309", "nome": "Vitória", "uf": "ES"},
    {"codigoIbge": "3205200", "nome": "Vila Velha", "uf": "ES"},
    {"codigoIbge": "4106902", "nome": "Curitiba", "uf": "PR"}
  ]
}
```

Repita as entradas para todos os lotes numerados. Calcule o checksum local com `sha256sum tools/cnpj/sources/*.zip`, confira os nomes/URLs no catálogo e só então registre os valores no manifesto. Não reutilize arquivos ou checksums de competências diferentes.

## Gerar

Com fontes já baixadas:

```bash
python3 tools/cnpj/gerar_dataset.py \
  --manifest tools/cnpj/fontes-2026-08.json \
  --source-dir tools/cnpj/sources \
  --output-sql src/main/resources/db/migration/R__carregar_subset_cnpj.sql
```

Para o ingestor baixar exatamente as URLs congeladas no manifesto, omita `--source-dir`. O download pode ocupar vários gigabytes e só deve ser feito manualmente durante a atualização mensal.

A saída JSON padrão é `src/main/resources/cnpj/cnpj-subset.json`. `--output-sql` gera a migration Flyway repetível consumida pelo backend: ela substitui somente os municípios do manifesto, remove empresas que ficaram órfãs e faz a carga em lotes de 500. Para conferir sem substituir os artefatos usados pelo backend, passe saídas em `/tmp`.

## Validar

```bash
python3 tools/cnpj/test_gerar_dataset.py
```

Os testes constroem ZIPs oficiais mínimos em diretório temporário e cobrem parser Latin-1, normalização, filtro de ativos/localidades, ordenação determinística, CNPJ/endereço e falha por checksum ou município sem registros.

## Saída

O JSON contém metadados da competência, fontes/checksums e municípios, seguidos por:

- `empresas`: CNPJ básico, razão social original/normalizada e data da base;
- `estabelecimentos`: CNPJ 14, nome fantasia, logradouro, número, bairro, CEP, município IBGE, UF, situação cadastral e data da base, incluindo as formas textuais normalizadas.

Empresas sem estabelecimento ativo nos municípios escolhidos não entram no artefato. A lista é ordenada por CNPJ básico e CNPJ completo, tornando a geração reproduzível.
