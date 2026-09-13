package dev.jlm.leadshunter.integracao.pesquisa;

import java.util.List;

public record GooglePesquisaWebResponse(
    String googlePlaceId,
    TipoPesquisaWeb tipo,
    String consulta,
    List<GoogleResultadoWeb> resultados
) {

    public GooglePesquisaWebResponse {
        resultados = resultados == null ? List.of() : List.copyOf(resultados);
    }
}
