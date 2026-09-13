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
            1,
            15_000
        )) {
            GooglePesquisaWebClient client = new GooglePesquisaWebClient(
                navigator,
                new GooglePesquisaHtmlParser(),
                10,
                3_600_000
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
                System.out.println("PESQUISA_PUBLICA=RESULTADOS; quantidade=" + response.resultados().size());
            } catch (GooglePesquisaWebBloqueadaException exception) {
                assertThat(exception)
                    .hasMessage("A fonte de pesquisa bloqueou temporariamente o acesso automatizado.")
                    .hasMessageNotContaining("captcha")
                    .hasMessageNotContaining("html")
                    .hasMessageNotContaining("sorry");
                // Teste aprovado aqui comprova tratamento do bloqueio, não captura/precisão real.
                System.out.println("PESQUISA_PUBLICA=BLOQUEADA; captura_e_precisao=NAO_VALIDADAS; sem_retry=true");
            }
        }
    }
}
