package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class PlaywrightGooglePesquisaNavigatorTest {

    @Test
    void deveRecusarDestinoExternoAntesDeInicializarONavegador() {
        try (PlaywrightGooglePesquisaNavigator navigator = new PlaywrightGooglePesquisaNavigator(
            1_000,
            16_384,
            1
        )) {
            assertThatThrownBy(() -> navigator.navegar(URI.create("https://evil.example/search?q=lead")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Destino de pesquisa não permitido");
        }
    }

    @Test
    void deveRecusarCaminhoDiferenteDaBuscaPublica() {
        try (PlaywrightGooglePesquisaNavigator navigator = new PlaywrightGooglePesquisaNavigator(
            1_000,
            16_384,
            1
        )) {
            assertThatThrownBy(() -> navigator.navegar(URI.create("https://www.google.com/url?q=https://evil.example")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Destino de pesquisa não permitido");
        }
    }
}
