package dev.jlm.leadshunter.integracao.places;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.math.BigDecimal;
import java.util.List;

public record PlacesSearchResponse(
    List<PlaceResult> places
) {

    public record PlaceResult(
        String googlePlaceId,
        String nome,
        CategoriaNegocio categoria,
        String enderecoFormatado,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal ratingGoogle,
        Integer totalReviews,
        String businessStatus,
        List<String> tipos
    ) {
    }
}
