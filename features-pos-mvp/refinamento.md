# Município e IDHM dos leads

**Estado:** implementado.

O backend usa um dataset local de municípios e IDHM com referência de 2010. A partir das coordenadas do estabelecimento, `MunicipioService` resolve código IBGE, município, UF e IDHM sem consulta geográfica externa em runtime. A migration V2 acrescentou os campos ao lead. Sem coordenadas ou correspondência no dataset, os campos ficam nulos. O IDHM não participa do score.

API e exportações CSV/XLSX expõem os dados disponíveis. No frontend, o card e o detalhe do lead exibem a localidade e o IDHM; o mapa da Busca tem uma camada opcional por faixa de IDHM. O endpoint `GET /api/geografia/municipios?bbox=...` entrega apenas o recorte solicitado em GeoJSON, com limite de 1.500 municípios por resposta.

A camada carrega recortes ao navegar no mapa, com controle de chamadas e cache em memória. Em visão ampla, acima de 5° de amplitude, pede aproximação em vez de consultar uma área que excederia o limite. Portanto, o mapa não carrega o Brasil inteiro de uma vez.

O backfill de geografia para leads anteriores é opt-in por `leadhunter.backfill-municipio=true`; o startup padrão não altera esses registros. Os dados e o gerador estão documentados em [tools/idhm](../tools/idhm/README.md).

**Referências:** [API](../API.md), [fluxo interno](../fluxo.md) e [histórico de implementações](../HISTORICO_IMPLEMENTACOES.md).
