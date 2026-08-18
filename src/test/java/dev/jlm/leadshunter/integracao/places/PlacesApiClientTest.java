package dev.jlm.leadshunter.integracao.places;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlacesApiClientTest {

    @Mock
    private PlacesResponseMapper responseMapper;

    @Mock
    private PlacesRateLimiter rateLimiter;

    @Test
    void devePassarPeloRateLimiterAntesDaChamadaExterna() {
        PlacesSearchResponse resposta = new PlacesSearchResponse(List.of());
        when(rateLimiter.executar(any())).thenReturn(resposta);
        PlacesApiClient client = criarClient("chave-de-teste");

        PlacesSearchResponse resultado = client.buscarProximos(criarRequest());

        assertThat(resultado).isSameAs(resposta);
        verify(rateLimiter).executar(any());
    }

    @Test
    void naoDeveConsumirPermissaoQuandoChaveEstiverAusente() {
        PlacesApiClient client = criarClient(" ");

        assertThatThrownBy(() -> client.buscarProximos(criarRequest()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("GOOGLE_PLACES_API_KEY");
        verifyNoInteractions(rateLimiter);
    }

    private PlacesApiClient criarClient(String apiKey) {
        return new PlacesApiClient(
            responseMapper,
            rateLimiter,
            apiKey,
            "https://places.googleapis.com/v1/places:searchNearby"
        );
    }

    private PlacesSearchRequest criarRequest() {
        return new PlacesSearchRequest(
            new BigDecimal("-25.4284"),
            new BigDecimal("-49.2733"),
            5,
            List.of(CategoriaNegocio.PADARIA)
        );
    }
}
