package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PesquisaWebGatewayTest {

    private final GooglePesquisaWebRequest request = new GooglePesquisaWebRequest(
        "place-1", "Padaria Aurora", CategoriaNegocio.PADARIA, null, "Campinas", "SP",
        TipoPesquisaWeb.INSTAGRAM);

    @Test
    void braveHabilitadoTemPrioridadeENaoAcionaScraping() {
        var brave = new BravePesquisaApiClient(
            (uri, token, timeout, limite) -> new BravePesquisaApiClient.Resposta(200, json("https://brave.example/")),
            r -> "q", "chave", true, 15_000, 10, 65_536);
        var gateway = new PesquisaWebGateway(brave, scrapingQueFalha(), false);

        assertThat(gateway.pesquisar(request).resultados())
            .extracting(GoogleResultadoWeb::url)
            .containsExactly(URI.create("https://brave.example/"));
    }

    @Test
    void semBraveUsaScrapingApenasQuandoHabilitado() {
        var brave = new BravePesquisaApiClient(
            (uri, token, timeout, limite) -> { throw new AssertionError("Brave sem chave não deve ser chamado"); },
            r -> "q", "", true, 15_000, 10, 65_536);
        var scraping = new PesquisaWebFallbackClient(
            r -> { throw new AssertionError("Google não deve ser chamado antes do Bing"); },
            uri -> new GooglePesquisaPagina(uri, 200, "simulado",
                "<html><body><ol id=\"b_results\"><li class=\"b_algo\"><h2>"
                    + "<a href=\"https://scraping.example/\">t</a></h2>"
                    + "<div class=\"b_caption\"><p>d</p></div></li></ol></body></html>"),
            new PesquisaAlternativaHtmlParser(), r -> "q", System::currentTimeMillis);
        var gateway = new PesquisaWebGateway(brave, scraping, true);

        assertThat(gateway.pesquisar(request).resultados()).hasSize(1);
    }

    @Test
    void semNenhumaFonteConfiguradaRetornaIndisponivel() {
        var brave = new BravePesquisaApiClient(
            (uri, token, timeout, limite) -> { throw new AssertionError("Brave sem chave não deve ser chamado"); },
            r -> "q", "", true, 15_000, 10, 65_536);
        var gateway = new PesquisaWebGateway(brave, scrapingQueFalha(), false);

        assertThatThrownBy(() -> gateway.pesquisar(request))
            .isInstanceOf(GooglePesquisaWebIndisponivelException.class)
            .hasMessageContaining("BRAVE_SEARCH_API_KEY");
    }

    private PesquisaWebFallbackClient scrapingQueFalha() {
        return new PesquisaWebFallbackClient(
            r -> { throw new AssertionError("Scraping não deveria ser acionado"); },
            uri -> { throw new AssertionError("Scraping não deveria ser acionado"); },
            new PesquisaAlternativaHtmlParser(), r -> "q", System::currentTimeMillis);
    }

    private byte[] json(String url) {
        return ("{\"web\":{\"results\":[{\"url\":\"" + url + "\",\"title\":\"t\",\"description\":\"d\"}]}}")
            .getBytes(StandardCharsets.UTF_8);
    }
}
