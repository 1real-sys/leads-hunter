package dev.jlm.leadshunter.integracao.places;

import dev.jlm.leadshunter.integracao.places.PlacesApiClient.NearbySearchResponse;
import dev.jlm.leadshunter.integracao.places.PlacesApiClient.Place;
import dev.jlm.leadshunter.integracao.places.PlacesApiClient.AddressComponent;
import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
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
            place.types() != null ? place.types() : List.of(),
            toEnderecoEstruturado(place.addressComponents())
        );
    }

    private PlacesSearchResponse.EnderecoEstruturado toEnderecoEstruturado(
        List<AddressComponent> componentes
    ) {
        String cep = somenteDigitos(valorDoTipo(componentes, "postal_code"));
        if (cep != null && cep.length() != 8) {
            cep = null;
        }
        String logradouro = valorDoTipo(componentes, "route");
        String numero = valorDoTipo(componentes, "street_number");
        String bairro = valorDoPrimeiroTipo(
            componentes,
            "neighborhood",
            "sublocality_level_1",
            "sublocality",
            "sublocality_level_2"
        );
        if (cep == null && logradouro == null && numero == null && bairro == null) {
            return null;
        }
        return new PlacesSearchResponse.EnderecoEstruturado(
            cep,
            logradouro,
            numero,
            bairro
        );
    }

    private String valorDoPrimeiroTipo(List<AddressComponent> componentes, String... tipos) {
        for (String tipo : tipos) {
            String valor = valorDoTipo(componentes, tipo);
            if (valor != null) {
                return valor;
            }
        }
        return null;
    }

    private String valorDoTipo(List<AddressComponent> componentes, String tipo) {
        if (componentes == null) {
            return null;
        }
        return componentes.stream()
            .filter(Objects::nonNull)
            .filter(componente -> componente.types() != null
                && componente.types().contains(tipo))
            .map(AddressComponent::longText)
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(valor -> !valor.isEmpty())
            .findFirst()
            .orElse(null);
    }

    private String somenteDigitos(String valor) {
        if (valor == null) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
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
