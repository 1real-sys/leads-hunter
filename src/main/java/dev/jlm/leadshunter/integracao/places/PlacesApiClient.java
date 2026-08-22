package dev.jlm.leadshunter.integracao.places;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PlacesApiClient {

    private static final String FIELD_MASK = String.join(",",
        "places.id",
        "places.displayName",
        "places.formattedAddress",
        "places.internationalPhoneNumber",
        "places.nationalPhoneNumber",
        "places.location",
        "places.rating",
        "places.userRatingCount",
        "places.businessStatus",
        "places.types"
    );

    private final RestClient restClient;
    private final PlacesResponseMapper responseMapper;
    private final PlacesRateLimiter rateLimiter;
    private final String apiKey;
    private final String nearbySearchUrl;

    @Autowired
    public PlacesApiClient(
        PlacesResponseMapper responseMapper,
        PlacesRateLimiter rateLimiter,
        @Value("${google.places.api-key:}") String apiKey,
        @Value("${google.places.nearby-search-url}") String nearbySearchUrl
    ) {
        this(RestClient.builder(), responseMapper, rateLimiter, apiKey, nearbySearchUrl);
    }

    PlacesApiClient(
        RestClient.Builder restClientBuilder,
        PlacesResponseMapper responseMapper,
        PlacesRateLimiter rateLimiter,
        String apiKey,
        String nearbySearchUrl
    ) {
        this.restClient = restClientBuilder.build();
        this.responseMapper = responseMapper;
        this.rateLimiter = rateLimiter;
        this.apiKey = apiKey;
        this.nearbySearchUrl = nearbySearchUrl;
    }

    public PlacesSearchResponse buscarProximos(PlacesSearchRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new PlacesApiConfigurationException(
                "Google Places API key ausente. Configure GOOGLE_PLACES_API_KEY."
            );
        }

        return rateLimiter.executar(() -> executarBusca(request));
    }

    private PlacesSearchResponse executarBusca(PlacesSearchRequest request) {
        NearbySearchRequest body = new NearbySearchRequest(
            tiposGoogle(request.categorias()),
            20,
            new LocationRestriction(
                new Circle(
                    new Center(
                        request.latitude().doubleValue(),
                        request.longitude().doubleValue()
                    ),
                    request.raioKm() * 1000.0
                )
            ),
            "POPULARITY",
            "pt-BR",
            "BR"
        );

        try {
            NearbySearchResponse response = restClient.post()
                .uri(nearbySearchUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", FIELD_MASK)
                .body(body)
                .retrieve()
                .body(NearbySearchResponse.class);

            try {
                return responseMapper.toPlacesSearchResponse(response);
            } catch (RuntimeException exception) {
                throw new PlacesApiInvalidResponseException(exception);
            }
        } catch (PlacesApiInvalidResponseException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw traduzirRespostaHttp(exception);
        } catch (ResourceAccessException exception) {
            throw new PlacesApiUnavailableException(exception);
        } catch (RestClientException exception) {
            throw new PlacesApiInvalidResponseException(exception);
        }
    }

    private RuntimeException traduzirRespostaHttp(RestClientResponseException exception) {
        HttpStatusCode status = exception.getStatusCode();

        if (status.value() == 429) {
            return new PlacesApiQuotaExceededException();
        }
        if (status.value() == 401 || status.value() == 403) {
            return new PlacesApiConfigurationException(
                "A configuração da Google Places API foi rejeitada. Verifique a chave e as permissões.",
                exception
            );
        }
        if (status.is4xxClientError()) {
            return new PlacesApiRequestRejectedException(exception);
        }
        return new PlacesApiUnavailableException(exception);
    }

    private List<String> tiposGoogle(List<CategoriaNegocio> categorias) {
        Set<String> tipos = new LinkedHashSet<>();

        for (CategoriaNegocio categoria : categorias) {
            switch (categoria) {
                case MERCADO -> tipos.add("supermarket");
                case PADARIA -> tipos.add("bakery");
                case DOCERIA -> {
                    tipos.add("bakery");
                    tipos.add("candy_store");
                }
                case RESTAURANTE -> tipos.add("restaurant");
                case DISTRIBUIDORA -> tipos.add("store");
                case ACOUGUE -> tipos.add("butcher_shop");
                case FARMACIA -> tipos.add("pharmacy");
                case OUTROS -> tipos.add("store");
            }
        }

        return List.copyOf(tipos);
    }

    record NearbySearchRequest(
        List<String> includedTypes,
        Integer maxResultCount,
        LocationRestriction locationRestriction,
        String rankPreference,
        String languageCode,
        String regionCode
    ) {
    }

    record LocationRestriction(Circle circle) {
    }

    record Circle(Center center, Double radius) {
    }

    record Center(Double latitude, Double longitude) {
    }

    record NearbySearchResponse(List<Place> places) {
    }

    record Place(
        String id,
        DisplayName displayName,
        String formattedAddress,
        String internationalPhoneNumber,
        String nationalPhoneNumber,
        Location location,
        Double rating,
        Integer userRatingCount,
        String businessStatus,
        List<String> types
    ) {
    }

    record DisplayName(String text, String languageCode) {
    }

    record Location(Double latitude, Double longitude) {
    }
}
