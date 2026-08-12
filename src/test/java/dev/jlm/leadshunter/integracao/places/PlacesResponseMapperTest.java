package dev.jlm.leadshunter.integracao.places;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.integracao.places.PlacesApiClient.DisplayName;
import dev.jlm.leadshunter.integracao.places.PlacesApiClient.Location;
import dev.jlm.leadshunter.integracao.places.PlacesApiClient.NearbySearchResponse;
import dev.jlm.leadshunter.integracao.places.PlacesApiClient.Place;
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
            new Location(-25.4284, -49.2733),
            4.7,
            82,
            "OPERATIONAL",
            List.of("bakery", "candy_store")
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
                PlacesSearchResponse.PlaceResult::ratingGoogle,
                PlacesSearchResponse.PlaceResult::totalReviews,
                PlacesSearchResponse.PlaceResult::businessStatus
            )
            .containsExactly(
                "place-123",
                "Doces da Ana",
                CategoriaNegocio.DOCERIA,
                "Rua das Flores, 10",
                new BigDecimal("4.7"),
                82,
                "OPERATIONAL"
            );
        assertThat(response.places().getFirst().latitude()).isEqualByComparingTo("-25.4284");
        assertThat(response.places().getFirst().longitude()).isEqualByComparingTo("-49.2733");
    }

    @Test
    void deveRetornarListaVaziaQuandoGoogleNaoRetornarLocais() {
        assertThat(mapper.toPlacesSearchResponse(null).places()).isEmpty();
        assertThat(mapper.toPlacesSearchResponse(new NearbySearchResponse(null)).places()).isEmpty();
    }
}
