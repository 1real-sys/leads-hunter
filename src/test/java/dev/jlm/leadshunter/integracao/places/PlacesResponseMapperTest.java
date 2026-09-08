package dev.jlm.leadshunter.integracao.places;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.integracao.places.PlacesApiClient.DisplayName;
import dev.jlm.leadshunter.integracao.places.PlacesApiClient.Location;
import dev.jlm.leadshunter.integracao.places.PlacesApiClient.NearbySearchResponse;
import dev.jlm.leadshunter.integracao.places.PlacesApiClient.Place;
import dev.jlm.leadshunter.integracao.places.PlacesApiClient.AddressComponent;
import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlacesResponseMapperTest {

    private final PlacesResponseMapper mapper = new PlacesResponseMapper();

    @Test
    void deveMapearRespostaDaGoogleParaModeloInterno() {
        Place place = new Place(
            "place-123",
            new DisplayName("Doces da Ana", "pt-BR"),
            "Rua das Flores, 10",
            "+55 27 3333-4444",
            "(27) 3333-4444",
            new Location(-25.4284, -49.2733),
            4.7,
            82,
            "OPERATIONAL",
            List.of("bakery", "candy_store"),
            List.of(
                new AddressComponent("80.420-063", "80420-063", List.of("postal_code"), "pt-BR"),
                new AddressComponent("Rua Comendador Araújo", "R. Comendador Araújo", List.of("route"), "pt-BR"),
                new AddressComponent("731", "731", List.of("street_number"), "pt-BR"),
                new AddressComponent("Batel", "Batel", List.of("sublocality_level_1"), "pt-BR")
            )
        );

        PlacesSearchResponse response = mapper.toPlacesSearchResponse(
            new NearbySearchResponse(List.of(place))
        );

        assertThat(response.places()).hasSize(1);
        assertThat(response.places().getFirst())
            .extracting(
                PlacesSearchResponse.PlaceResult::googlePlaceId,
                PlacesSearchResponse.PlaceResult::nome,
                PlacesSearchResponse.PlaceResult::categoria,
                PlacesSearchResponse.PlaceResult::enderecoFormatado,
                PlacesSearchResponse.PlaceResult::telefone,
                PlacesSearchResponse.PlaceResult::ratingGoogle,
                PlacesSearchResponse.PlaceResult::totalReviews,
                PlacesSearchResponse.PlaceResult::businessStatus
            )
            .containsExactly(
                "place-123",
                "Doces da Ana",
                CategoriaNegocio.DOCERIA,
                "Rua das Flores, 10",
                "+55 27 3333-4444",
                new BigDecimal("4.7"),
                82,
                "OPERATIONAL"
            );
        assertThat(response.places().getFirst().latitude()).isEqualByComparingTo("-25.4284");
        assertThat(response.places().getFirst().longitude()).isEqualByComparingTo("-49.2733");
        assertThat(response.places().getFirst().enderecoEstruturado())
            .isEqualTo(new PlacesSearchResponse.EnderecoEstruturado(
                "80420063",
                "Rua Comendador Araújo",
                "731",
                "Batel"
            ));
    }

    @Test
    void deveIgnorarCepIncompletoESuportarComponentesAusentes() {
        Place place = new Place(
            "place-sem-endereco",
            new DisplayName("Local", "pt-BR"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(new AddressComponent("123", "123", List.of("postal_code"), "pt-BR"))
        );

        PlacesSearchResponse response = mapper.toPlacesSearchResponse(
            new NearbySearchResponse(List.of(place))
        );

        assertThat(response.places().getFirst().enderecoEstruturado()).isNull();
    }

    @Test
    void deveRetornarListaVaziaQuandoGoogleNaoRetornarLocais() {
        assertThat(mapper.toPlacesSearchResponse(null).places()).isEmpty();
        assertThat(mapper.toPlacesSearchResponse(new NearbySearchResponse(null)).places()).isEmpty();
    }
}
