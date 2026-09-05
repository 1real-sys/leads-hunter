# Dataset municipal de IDHM

Este diretório contém o gerador one-off da IDHM-00. Nenhum download é feito durante a inicialização ou durante o uso do Leads Hunter: o backend consumirá somente o artefato congelado em `src/main/resources/geo/municipios-idhm.json` nas sprints seguintes.

## Fontes e licenças

### IDHM e identificação municipal

- Fonte consumida: [Atlas Cidade — Dados abertos dos municípios do Brasil](https://www.atlascidade.com.br/dados/municipios-brasil.json).
- Catálogo e licença: [Dados abertos do Atlas Cidade](https://www.atlascidade.com.br/dados/).
- Licença declarada pelo distribuidor: [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/deed.pt-br).
- Atribuição: **Atlas Cidade; IDHM 2010: PNUD, Ipea e FJP**.
- Conteúdo usado: código IBGE, município, UF e IDHM municipal de 2010.
- SHA-256 congelado: `a19ddcab8150d68248fe14c002f58096bdba63af64b7cb00df4220e9e22e007e`.

O Atlas Cidade compila 5.571 registros municipais. Boa Esperança do Norte/MT (`5101837`), instalada depois do Censo 2010, não possui IDHM 2010 e não aparece na malha mínima retornada pela API do IBGE usada nesta versão.

### Geometria municipal

- Fonte consumida: [API de Malhas Geográficas v3 do IBGE](https://servicodados.ibge.gov.br/api/docs/malhas?versao=3), endpoint nacional com `qualidade=minima` e `intrarregiao=municipio`.
- Produto e informações legais: [Malhas Municipais do IBGE](https://www.ibge.gov.br/geociencias/organizacao-do-territorio/malhas-territoriais/15774-malhas.html).
- Condições informadas pelo IBGE: dados públicos, com atribuição ao IBGE e condições compatíveis com CC BY 4.0.
- Atribuição: **Instituto Brasileiro de Geografia e Estatística — IBGE**.
- Sistema de referência informado pelo produto: SIRGAS 2000, coordenadas geográficas.
- SHA-256 congelado: `5efffedf8772dc8654322affafac4e3e8b001839e7c2af5cab4eb1094dcd6240`.

A escolha da fonte oficial elimina a dependência de uma malha republicada por terceiros. A qualidade mínima da API já reduz o detalhamento; o gerador aplica adicionalmente Ramer–Douglas–Peucker com tolerância padrão de `0.001` grau por anel.

## Requisitos

- Python 3.10 ou superior.
- Biblioteca padrão apenas.
- Internet somente ao regenerar sem `--source-dir`.

## Gerar

Na raiz do repositório:

```bash
python3 tools/idhm/gerar_dataset.py
```

Para reproduzir usando fontes já baixadas e verificadas:

```bash
python3 tools/idhm/gerar_dataset.py --source-dir /caminho/das/fontes
```

O diretório deve conter:

- `municipios-brasil.json`;
- `ibge-municipios-min.geojson`.

O script aceita somente as URLs HTTPS fixas quando faz download, limita cada resposta — inclusive após descompactação gzip — a 12 MiB e exige os SHA-256 registrados. Se uma fonte mudar, a geração para; o novo conteúdo, licença e impacto devem ser revisados antes de atualizar o checksum.

## Validar

```bash
python3 tools/idhm/test_gerar_dataset.py
```

A validação confirma:

- 5.570 municípios com códigos únicos;
- campos obrigatórios, IDHM nulo ou entre 0 e 1, bbox e geometria Polygon/MultiPolygon;
- Vitória/ES em `-20.3155, -40.3128`, com IDHM `0.845`;
- Curitiba/PR em `-25.4284, -49.2733`, com IDHM `0.823`;
- a diferença conhecida de Boa Esperança do Norte/MT entre a tabela de 2026 e a malha municipal consumida.

## Artefato

O JSON final é ordenado por código IBGE e escrito de forma compacta e determinística. O artefato congelado nesta sprint possui 3.709.696 bytes e SHA-256 `8c9ce54dff5eec54e7401ba2392e4305145edc4acb02c21388425393c6b56286`. Cada município contém:

- `codigoIbge`;
- `nome`;
- `uf`;
- `idhm`;
- `idhmReferencia`;
- `bbox` no formato `[minLng, minLat, maxLng, maxLat]`;
- `geometry` GeoJSON simplificada.

O arquivo gerado não é um endpoint público nem uma entrada de runtime. Sua exposição filtrada por bbox será implementada somente na IDHM-02.
