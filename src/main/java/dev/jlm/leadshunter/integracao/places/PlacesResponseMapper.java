package dev.jlm.leadshunter.integracao.places;

import dev.jlm.leadshunter.integracao.places.PlacesApiClient.NearbySearchResponse;
import dev.jlm.leadshunter.integracao.places.PlacesApiClient.Place;
import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlacesResponseMapper {

    public PlacesSearchResponse toPlacesSearchResponse(NearbySearchResponse response) {
        if (response == null || response.places() == null) {
            return new PlacesSearchResponse(List.of());
        }

        List<PlacesSearchResponse.PlaceResult> places = response.places().stream()
            .map(this::toPlaceResult)
            .toList();

        return new PlacesSearchResponse(places);
    }

    private PlacesSearchResponse.PlaceResult toPlaceResult(Place place) {
        return new PlacesSearchResponse.PlaceResult(
            place.id(),
            place.displayName() != null ? place.displayName().text() : null,
            inferirCategoria(place.types()),
            place.formattedAddress(),
            selecionarTelefone(place),
            toBigDecimal(place.location() != null ? place.location().latitude() : null),
            toBigDecimal(place.location() != null ? place.location().longitude() : null),
            place.rating() != null ? BigDecimal.valueOf(place.rating()) : null,
            place.userRatingCount(),
            place.businessStatus(),
            place.types() != null ? place.types() : List.of()
        );
    }

    private String selecionarTelefone(Place place) {
        if (place.internationalPhoneNumber() != null
            && !place.internationalPhoneNumber().isBlank()) {
            return place.internationalPhoneNumber();
        }
        if (place.nationalPhoneNumber() != null && !place.nationalPhoneNumber().isBlank()) {
            return place.nationalPhoneNumber();
        }
        return null;
    }

    private CategoriaNegocio inferirCategoria(List<String> types) {
        if (types == null || types.isEmpty()) {
            return CategoriaNegocio.OUTROS;
        }

        if (types.contains("supermarket")) {
            return CategoriaNegocio.MERCADO;
        }
        if (types.contains("candy_store")) {
            return CategoriaNegocio.DOCERIA;
        }
        if (types.contains("restaurant")) {
            return CategoriaNegocio.RESTAURANTE;
        }
        if (types.contains("butcher_shop")) {
            return CategoriaNegocio.ACOUGUE;
        }
        if (types.contains("pharmacy")) {
            return CategoriaNegocio.FARMACIA;
        }
        if (types.contains("bakery")) {
            return CategoriaNegocio.PADARIA;
        }

        return CategoriaNegocio.OUTROS;
    }

    private BigDecimal toBigDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }
}
