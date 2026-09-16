package dev.jlm.leadshunter.integracao.places;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Diagnóstico opt-in de uma única chamada real à Google Places, sem persistir nada.
 * O rate limiter de capacidade 1 garante que nenhuma requisição extra seja disparada.
 */
@EnabledIfSystemProperty(named = "placesLive", matches = "true")
class PlacesApiClientLiveTest {

    private static final BigDecimal LATITUDE = new BigDecimal("-23.5614");
    private static final BigDecimal LONGITUDE = new BigDecimal("-46.6559");
    private static final int RAIO_KM = 3;

    @Test
    @Timeout(60)
    void deveClassificarLojasReaisNasNovasCategoriasComUmaUnicaChamada() throws Exception {
        PropertySource<?> fonte = new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml")).get(0);
        var ambiente = new StandardEnvironment();
        ambiente.getPropertySources().addLast(fonte);
        String apiKey = ambiente.getRequiredProperty("google.places.api-key");

        var rateLimiter = new PlacesRateLimiter(1, 60);
        var client = new PlacesApiClient(
            new PlacesResponseMapper(),
            rateLimiter,
            apiKey,
            "https://places.googleapis.com/v1/places:searchNearby"
        );
        var request = new PlacesSearchRequest(
            LATITUDE,
            LONGITUDE,
            RAIO_KM,
            List.of(CategoriaNegocio.INFORMATICA, CategoriaNegocio.VESTUARIO, CategoriaNegocio.PETSHOP)
        );

        var resposta = client.buscarProximos(request);

        Map<CategoriaNegocio, Integer> contagem = new EnumMap<>(CategoriaNegocio.class);
        for (var place : resposta.places()) {
            contagem.merge(place.categoria(), 1, Integer::sum);
            System.out.println("PLACES_LIVE " + place.categoria() + " | " + place.nome()
                + " | " + place.enderecoFormatado());
        }
        System.out.println("PLACES_LIVE_RESUMO " + contagem + " total=" + resposta.places().size());

        assertThat(resposta.places()).isNotEmpty();
        assertThat(contagem.keySet()).isSubsetOf(List.of(
            CategoriaNegocio.INFORMATICA,
            CategoriaNegocio.VESTUARIO,
            CategoriaNegocio.PETSHOP
        ));
        assertThat(contagem).doesNotContainKey(CategoriaNegocio.OUTROS);
    }
}
