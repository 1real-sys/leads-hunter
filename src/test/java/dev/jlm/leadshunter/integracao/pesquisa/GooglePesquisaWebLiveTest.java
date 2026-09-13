package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "pesquisaGoogleLive", matches = "true")
class GooglePesquisaWebLiveTest {

    @Test
    void deveObterResultadosOuClassificarOBloqueioDaFontePublica() {
        try (PlaywrightGooglePesquisaNavigator navigator = new PlaywrightGooglePesquisaNavigator(
            20_000,
            2_097_152,
            2
        )) {
            GooglePesquisaWebClient client = new GooglePesquisaWebClient(
                navigator,
                new GooglePesquisaHtmlParser(),
                10
            );

            try {
                GooglePesquisaWebResponse response = client.pesquisar(new GooglePesquisaWebRequest(
                    "smoke-test-sem-consulta-places",
                    "Padaria Real",
                    CategoriaNegocio.PADARIA,
                    null,
                    "Sorocaba",
                    "SP",
                    TipoPesquisaWeb.INSTAGRAM
                ));

                assertThat(response.resultados())
                    .as("resultados públicos renderizados pelo Google Search")
                    .isNotEmpty()
                    .allMatch(resultado -> resultado.url().getHost() != null)
                    .allMatch(resultado -> !resultado.titulo().isBlank());
            } catch (GooglePesquisaWebBloqueadaException exception) {
                assertThat(exception)
                    .hasMessage("O Google bloqueou temporariamente a pesquisa automatizada.")
                    .hasMessageNotContaining("captcha")
                    .hasMessageNotContaining("html")
                    .hasMessageNotContaining("sorry");
            }
        }
    }
}
