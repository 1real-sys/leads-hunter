# Categorias de prospecção

**Estado:** Informática, Vestuário e Pet Shop implementadas.

O enum `CategoriaNegocio` inclui as três categorias junto das anteriores. O cliente Google Places consulta tipos compatíveis: `electronics_store` e `cell_phone_store` para Informática; `clothing_store`, `womens_clothing_store`, `shoe_store` e `sportswear_store` para Vestuário; `pet_store` para Pet Shop. O mapper infere a categoria dos resultados, e a interface oferece as opções na Busca e nos filtros.

A mudança não exigiu migration. Leads já persistidos não são reclassificados retroativamente; a categoria de um lead pode ser atualizada quando ele reaparece em uma busca. Tipos amplos do Google podem gerar classificação menos específica, então os resultados ainda dependem da qualidade da resposta do Places.

**Referências:** [enum](../src/main/java/dev/jlm/leadshunter/lead/CategoriaNegocio.java), [cliente Places](../src/main/java/dev/jlm/leadshunter/integracao/places/PlacesApiClient.java), [fluxo interno](../fluxo.md).
