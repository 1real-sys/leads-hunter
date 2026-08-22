package dev.jlm.leadshunter.integracao.places;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

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
            .isInstanceOf(PlacesApiConfigurationException.class)
            .hasMessageContaining("GOOGLE_PLACES_API_KEY");
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void deveTraduzirCotaExcedidaDaGooglePlaces() {
        HttpTestClient httpClient = criarHttpClient(HttpStatus.TOO_MANY_REQUESTS);

        assertThatThrownBy(() -> httpClient.client().buscarProximos(criarRequest()))
            .isInstanceOf(PlacesApiQuotaExceededException.class)
            .hasMessageContaining("cota");

        httpClient.server().verify();
    }

    @Test
    void deveTraduzirFalhaDeAutorizacaoDaGooglePlaces() {
        HttpTestClient httpClient = criarHttpClient(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> httpClient.client().buscarProximos(criarRequest()))
            .isInstanceOf(PlacesApiConfigurationException.class)
            .hasMessageContaining("permissões");

        httpClient.server().verify();
    }

    @Test
    void deveTraduzirIndisponibilidadeDaGooglePlaces() {
        HttpTestClient httpClient = criarHttpClient(HttpStatus.BAD_GATEWAY);

        assertThatThrownBy(() -> httpClient.client().buscarProximos(criarRequest()))
            .isInstanceOf(PlacesApiUnavailableException.class)
            .hasMessageContaining("indisponível");

        httpClient.server().verify();
    }

    @Test
    void deveTraduzirRespostaInvalidaDaGooglePlaces() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlacesApiClient client = new PlacesApiClient(
            builder,
            responseMapper,
            rateLimiter,
            "chave-de-teste",
            URL
        );
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{payload-invalido"));
        permitirExecucaoDoRateLimiter();

        assertThatThrownBy(() -> client.buscarProximos(criarRequest()))
            .isInstanceOf(PlacesApiInvalidResponseException.class)
            .hasMessageContaining("resposta inválida");

        server.verify();
    }

    private PlacesApiClient criarClient(String apiKey) {
        return new PlacesApiClient(
            responseMapper,
            rateLimiter,
            apiKey,
            "https://places.googleapis.com/v1/places:searchNearby"
        );
    }

    private HttpTestClient criarHttpClient(HttpStatus status) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withStatus(status));
        PlacesApiClient client = new PlacesApiClient(builder, responseMapper, rateLimiter, "chave-de-teste", URL);
        permitirExecucaoDoRateLimiter();
        return new HttpTestClient(server, client);
    }

    private void permitirExecucaoDoRateLimiter() {
        doAnswer(invocation -> {
            Supplier<?> callback = invocation.getArgument(0);
            return callback.get();
        }).when(rateLimiter).executar(org.mockito.ArgumentMatchers.<Supplier<Object>>any());
    }

    private PlacesSearchRequest criarRequest() {
        return new PlacesSearchRequest(
            new BigDecimal("-25.4284"),
            new BigDecimal("-49.2733"),
            5,
            List.of(CategoriaNegocio.PADARIA)
        );
    }

    private static final String URL = "https://places.googleapis.com/v1/places:searchNearby";

    private record HttpTestClient(MockRestServiceServer server, PlacesApiClient client) {
    }
}
