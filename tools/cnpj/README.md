# Subset local dos Dados Abertos do CNPJ

Este diretório contém o ingestor offline das sprints CNPJ-00 e CNPJ-06. Ele transforma os arquivos mensais da Receita Federal em um recorte ordenado e consumível pelo backend, em JSON e/ou SQL, sem baixar nem consultar CNPJ em runtime.

## Fonte, procedência e licença

- Catálogo oficial: [Cadastro Nacional da Pessoa Jurídica — CNPJ](https://dados.gov.br/dados/conjuntos-dados/cadastro-nacional-da-pessoa-juridica---cnpj).
- Órgão responsável: Secretaria Especial da Receita Federal do Brasil.
- Metadados e leiaute: [CNPJ — Metadados](https://www.gov.br/receitafederal/dados/cnpj-metadados.pdf/@@download/file).
- Arquivos consumidos: `Empresas*.zip`, `Estabelecimentos*.zip` e `Municipios.zip` da mesma competência mensal.
- Formato oficial: CSV sem cabeçalho, separado por ponto e vírgula, delimitado por aspas e codificado em Latin-1.
- Licença/uso: dados cadastrais públicos disponibilizados conforme a política de Dados Abertos do Governo Federal. A origem Receita Federal deve ser preservada ao redistribuir o recorte.

`Empresas` fornece o CNPJ básico e a razão social. `Estabelecimentos` fornece ordem/dígitos verificadores, nome fantasia, situação cadastral, endereço, UF e o código municipal interno da Receita. `Municipios` converte esse código interno em nome. O catálogo dedicado `municipios-ibge.csv` associa nome + UF ao código IBGE usado pelo Leads Hunter e permite expandir uma UF inteira sem acoplar o ingestor ao artefato de geografia/IDHM.

## Segurança e reprodutibilidade

O manifesto é obrigatório e congela `dataBase`, recorte geográfico, URL HTTPS e SHA-256 de cada arquivo. O ingestor:

- aceita download somente dos hosts oficiais declarados no código;
- rejeita redirecionamento para outro host, nomes de arquivo/caminhos inseguros, caracteres de controle nas URLs, ZIPs com quantidade ou tamanho fora dos limites e CNPJ/CEP inválidos;
- calcula o SHA-256 antes de ler qualquer CSV e para se a fonte mudar;
- aceita `ufs` e/ou `municipiosInteresse`, expande cada UF pelo catálogo versionado e falha se um nome municipal não corresponder à relação normalizada da Receita;
- mantém somente estabelecimentos com situação cadastral `02` (ativa);
- exige pelo menos uma unidade ativa em cada município configurado;
- descarta cedo os registros fora do recorte, mantém somente as empresas necessárias e grava o resultado atomicamente, em lotes e sem timestamp variável.

Os arquivos brutos são grandes e não devem ser versionados. `tools/cnpj/sources/` está ignorado pelo Git.

## Estado do artefato de runtime

O conteúdo versionado de `src/main/resources/db/migration/R__carregar_subset_cnpj.sql` deve permanecer como um placeholder seguro. A base volumosa vive no banco local: o SQL mensal gerado em `/tmp` pode ser importado diretamente pelo cliente MySQL. Se a migration repetível for usada para uma carga local via Flyway, sua alteração de dezenas ou centenas de MB deve permanecer fora do commit; nunca inclua o recorte mensal no Git.

`tools/cnpj/municipios-ibge.csv` é diferente: ele contém somente 5.570 registros pequenos (`codigo_ibge,nome,uf`) e é versionado. Foi derivado de `src/main/resources/geo/municipios-idhm.json` pelo script `gerar_municipios_ibge.py`; o SHA-256 atual é `50ea7cb2d7cc34cd3d581dc197cd45619579be9fad5d7b630dd3a9d04efdddbc`.

Os três casos Coco Bambu usados no desenvolvimento ficam exclusivamente em `src/test/resources/cnpj/fixtures.sql`. Seus CNPJs não são inventados: foram conferidos nas páginas oficiais das unidades [Vitória](https://www.cocobambu.com/unidades/cb-vitoria), [Vila Velha](https://cocobambu.com/unidades/cb-vila-velha) e [Curitiba](https://cocobambu.com/unidades/cb-curitiba). Essas fixtures não afirmam representar um recorte da Receita nem são carregadas no runtime.

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
  "ufs": ["ES"],
  "municipiosInteresse": [
    {"codigoIbge": "4106902", "nome": "Curitiba", "uf": "PR"}
  ]
}
```

`ufs` e `municipiosInteresse` são opcionais individualmente, mas pelo menos um deles deve ter itens. Quando ambos são informados, o resultado é a união sem duplicar um município já abrangido pela UF. Cada UF deve ter duas letras e existir no catálogo; `"ufs": ["ES"]` expande deterministicamente os 78 municípios capixabas.

Repita as entradas para **todos** os lotes numerados de Empresas e Estabelecimentos publicados naquela competência. Calcule o checksum local com `sha256sum tools/cnpj/sources/*.zip`, confira os nomes/URLs no catálogo e só então registre os valores no manifesto. Não reutilize arquivos ou checksums de competências diferentes.

## Gerar

Com fontes já baixadas:

```bash
python3 tools/cnpj/gerar_dataset.py \
  --manifest tools/cnpj/fontes-2026-08.json \
  --source-dir tools/cnpj/sources \
  --output-sql /tmp/cnpj-es.sql \
  --no-json \
  --workers 0
```

Para o ingestor baixar exatamente as URLs congeladas no manifesto, omita `--source-dir`. O download pode ocupar vários gigabytes e só deve ser feito manualmente durante a atualização mensal.

`--workers 0` seleciona automaticamente até 8 processos, limitado pela quantidade de lotes; valores explícitos de 1 a 16 permitem priorizar memória ou throughput. O processamento por processos foi mantido porque apresentou ganho real no hardware-alvo. A ferramenta é Python e executa fora da JVM: o Java 25 do backend não acelera esse hot path automaticamente nem exige uma reescrita Java fora do escopo.

`--no-json` evita o artefato intermediário quando somente a carga SQL interessa. Sem essa flag, a saída JSON padrão é `src/main/resources/cnpj/cnpj-subset.json` e pode ser alterada com `--output`. `--output-sql` produz a carga consumível pelo backend: ela identifica competência, UFs, URL e checksum de cada fonte no cabeçalho, substitui somente os municípios selecionados, remove empresas que ficaram órfãs e faz os `INSERT` em lotes determinísticos de 500.

Para conferir sem alterar nenhum recurso do backend, mantenha a saída em `/tmp`. Depois de revisar o cabeçalho e os totais, importe o SQL no MySQL local com o cliente já usado no ambiente. A carga é idempotente para o mesmo recorte e competência; não versione o arquivo gerado.

O catálogo de expansão pode ser reproduzido a partir da fonte geográfica versionada:

```bash
python3 tools/cnpj/gerar_municipios_ibge.py
```

Antes de iniciar prospecção real, revise no SQL gerado se todos os lotes esperados constam no cabeçalho e execute a suíte de persistência. Alterar a competência do recorte faz o backend revalidar, na próxima captura, os CNPJs já correspondidos naquele município.

## Importar no MySQL local

Depois de gerar o SQL (ex.: `/tmp/cnpj-es.sql`), importe no banco `leadsradar` usando o cliente do container MySQL já usado no ambiente:

```bash
docker exec -i mysql-db mysql -u root -p leadsradar < /tmp/cnpj-es.sql
```

Se você tiver o `mysql` instalado no host, o equivalente é `mysql -u root -p leadsradar < /tmp/cnpj-es.sql`. O comando pede a senha interativamente; nunca coloque a senha no arquivo SQL nem no README. A carga é idempotente para o mesmo recorte/competência (faz `DELETE` só dos municípios alvo e re-insere).

Confira com contagens exatas e atualize a estimativa do phpMyAdmin:

```bash
docker exec mysql-db mysql -u root -p leadsradar -e \
  "SELECT COUNT(*) FROM cnpj_estabelecimento; SELECT COUNT(*) FROM cnpj_empresa; ANALYZE TABLE cnpj_estabelecimento, cnpj_empresa;"
```

> Observação: na listagem do phpMyAdmin, "Rows ≈" é uma **estimativa** do InnoDB que fica desatualizada após carga em massa (ex.: mostrava ~78.567 com 608.030 reais). Use `SELECT COUNT(*)` para o valor exato; o `ANALYZE TABLE` atualiza a estimativa.

O arquivo SQL gerado é grande e **não deve ser versionado** (mantenha o `R__` do repositório como placeholder; a base volumosa vive no banco local).

## Smoke real do Espírito Santo

O smoke de CNPJ-06 usou o Ryzen 7 5700X (16 threads), Python 3.14.7 e as 21 fontes locais completas da competência RFB `2026-08-08` (10 lotes de Empresas, 10 de Estabelecimentos e Municípios). O comando de medição no Linux soma o RSS do processo principal e de todos os workers:

```bash
python3 tools/cnpj/medir_ingestao.py --intervalo 0.1 -- \
  --manifest /tmp/fontes-2026-08-es.json \
  --source-dir tools/cnpj/sources \
  --output-sql /tmp/cnpj-es.sql \
  --no-json \
  --workers 8
```

| Workers | Tempo | Pico RSS agregado | Resultado |
| ---: | ---: | ---: | --- |
| 8 | 113,21 s | 1.541,53 MiB | 78 municípios, 589.690 empresas e 608.030 estabelecimentos |
| 1 | 245,21 s | 1.011,41 MiB | mesmos registros e mesmos bytes |

O paralelismo controlado foi 2,17 vezes mais rápido e permaneceu abaixo da meta orientativa de 2 GiB. Os dois modos geraram um SQL de 168.526.384 bytes com o mesmo SHA-256: `da5caa912ecb5c3322d57a4cc9246548d0c9e942da424c0c5488b25d2aa6251a`.

## Validar

```bash
python3 tools/cnpj/test_gerar_dataset.py
```

Os 13 testes constroem ZIPs mínimos em diretório temporário e cobrem parser Latin-1, normalização, filtro de ativos/localidades, ordenação determinística, proveniência no SQL, CNPJ/endereço, metadado inseguro, checksum, município sem registros, expansão ES → 78 municípios, união retrocompatível, UF inválida/inexistente, divergência nominal, `--no-json`, limite de workers e reprodução exata do catálogo versionado.

## Saída

O JSON contém metadados da competência, fontes/checksums e municípios, seguidos por:

- `empresas`: CNPJ básico, razão social original/normalizada e data da base;
- `estabelecimentos`: CNPJ 14, nome fantasia, logradouro, número, bairro, CEP, município IBGE, UF, situação cadastral e data da base, incluindo as formas textuais normalizadas.

Empresas sem estabelecimento ativo nos municípios escolhidos não entram no artefato. A lista é ordenada por CNPJ básico e CNPJ completo, tornando a geração reproduzível.
