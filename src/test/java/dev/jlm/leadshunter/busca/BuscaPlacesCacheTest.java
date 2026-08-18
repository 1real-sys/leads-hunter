package dev.jlm.leadshunter.busca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jlm.leadshunter.integracao.places.PlacesSearchResponse;
import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BuscaPlacesCacheTest {

    @Test
    void deveExecutarCarregadorSomenteUmaVezParaMesmaChave() {
        BuscaPlacesCache cache = new BuscaPlacesCache(30, 100);
        BuscaCacheKey chave = criarChave(5);
        PlacesSearchResponse resposta = new PlacesSearchResponse(List.of());
        AtomicInteger chamadas = new AtomicInteger();

        PlacesSearchResponse primeira = cache.buscarOuCarregar(chave, () -> {
            chamadas.incrementAndGet();
            return resposta;
        });
        PlacesSearchResponse segunda = cache.buscarOuCarregar(chave, () -> {
            chamadas.incrementAndGet();
            return new PlacesSearchResponse(List.of());
        });

        assertThat(primeira).isSameAs(segunda);
        assertThat(chamadas).hasValue(1);
    }

    @Test
    void deveValidarConfiguracao() {
        assertThatThrownBy(() -> new BuscaPlacesCache(0, 100))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("expiração");
        assertThatThrownBy(() -> new BuscaPlacesCache(30, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tamanho máximo");
    }

    private BuscaCacheKey criarChave(int raioKm) {
        return new BuscaCacheKey(
            new BigDecimal("-25.4284"),
            new BigDecimal("-49.2733"),
            raioKm,
            List.of(CategoriaNegocio.PADARIA)
        );
    }
}
