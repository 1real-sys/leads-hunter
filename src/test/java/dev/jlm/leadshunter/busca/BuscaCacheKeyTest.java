package dev.jlm.leadshunter.busca;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuscaCacheKeyTest {

    @Test
    void deveConsiderarEquivalentesCoordenadasProximasECategoriasEmOutraOrdem() {
        BuscaCacheKey primeira = BuscaCacheKey.from(new BuscaRequest(
            "Centro",
            new BigDecimal("-25.42841"),
            new BigDecimal("-49.27331"),
            5,
            List.of(CategoriaNegocio.PADARIA, CategoriaNegocio.MERCADO)
        ));
        BuscaCacheKey segunda = BuscaCacheKey.from(new BuscaRequest(
            "Outro endereço textual",
            new BigDecimal("-25.42844"),
            new BigDecimal("-49.27334"),
            5,
            List.of(
                CategoriaNegocio.MERCADO,
                CategoriaNegocio.PADARIA,
                CategoriaNegocio.PADARIA
            )
        ));

        assertThat(primeira).isEqualTo(segunda);
        assertThat(primeira.categorias())
            .containsExactly(CategoriaNegocio.MERCADO, CategoriaNegocio.PADARIA);
    }

    @Test
    void deveDiferenciarRaiosDistintos() {
        BuscaRequest request = new BuscaRequest(
            "Centro",
            new BigDecimal("-25.4284"),
            new BigDecimal("-49.2733"),
            5,
            List.of(CategoriaNegocio.PADARIA)
        );
        BuscaRequest outroRaio = new BuscaRequest(
            "Centro",
            request.latitude(),
            request.longitude(),
            6,
            request.categorias()
        );

        assertThat(BuscaCacheKey.from(request)).isNotEqualTo(BuscaCacheKey.from(outroRaio));
    }
}
