package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.*;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PesquisaWebFallbackClientTest {
    private final GooglePesquisaWebRequest request = new GooglePesquisaWebRequest("place-1", "Padaria Aurora",
        CategoriaNegocio.PADARIA, null, "Campinas", "SP", TipoPesquisaWeb.INSTAGRAM);
    private final List<String> chamadas = new ArrayList<>();
    private final AtomicLong agora = new AtomicLong(1000);
    private final PesquisaAlternativaHtmlParser parser = new PesquisaAlternativaHtmlParser();

    @Test
    void bingPrincipalIncluindoAusenciaConclusivaNaoChamaOutrasFontes() {
        var client = client(r -> { throw new AssertionError("Google não deve ser chamado"); },
            uri -> { chamadas.add(uri.getHost()); return fixture(uri, "bing-resultados.html"); });
        assertThat(client.pesquisar(request).resultados()).hasSize(2);
        assertThat(chamadas).containsExactly("www.bing.com");
    }

    @Test
    void bingBloqueadoUsaGoogleEPreservaCooldownDoBing() {
        var client = client(r -> { chamadas.add("google"); return respostaGoogle(); },
            uri -> { chamadas.add(uri.getHost()); return fixture(uri, "bing-captcha.html"); });
        assertThat(client.pesquisar(request).resultados()).singleElement().satisfies(r ->
            assertThat(r.url()).isEqualTo(URI.create("https://google.example/")));
        client.pesquisar(request);
        assertThat(chamadas).containsExactly("www.bing.com", "google", "google");
        agora.addAndGet(3_600_000);
        client.pesquisar(request);
        assertThat(chamadas.stream().filter("www.bing.com"::equals).count()).isEqualTo(2);
    }

    @Test
    void fontesBloqueadasUsamBraveSemRepetirFontesSuspensas() {
        var client = client(r -> { chamadas.add("google"); throw new GooglePesquisaWebBloqueadaException(); },
            uri -> {
                chamadas.add(uri.getHost());
                if (uri.getHost().equals("www.bing.com")) return fixture(uri, "bing-captcha.html");
                if (uri.getHost().equals("html.duckduckgo.com")) return fixture(uri, "google-captcha.html");
                return fixture(uri, "brave-resultados.html");
            });
        assertThat(client.pesquisar(request).resultados()).singleElement().satisfies(r ->
            assertThat(r.url()).isEqualTo(URI.create("https://padariaaurora.example/")));
        assertThat(chamadas).containsExactly("www.bing.com", "google", "html.duckduckgo.com", "search.brave.com");
    }

    @Test
    void todasIndisponiveisGeramFalhaENaoAusenciaOuNovosAcessos() {
        var client = client(r -> { chamadas.add("google"); throw new GooglePesquisaWebTimeoutException(); },
            uri -> { chamadas.add(uri.getHost()); throw new GooglePesquisaWebBloqueadaException(); });
        for (int i = 0; i < 2; i++) assertThatThrownBy(() -> client.pesquisar(request))
            .isInstanceOf(GooglePesquisaWebIndisponivelException.class).hasMessageContaining("As fontes públicas");
        assertThat(chamadas).containsExactly("www.bing.com", "google", "html.duckduckgo.com", "search.brave.com");
    }

    @Test
    void faltaDeCapacidadeLocalNaoDisparaFallback() {
        var client = client(r -> { throw new AssertionError("Fila ocupada não autoriza outro acesso"); },
            uri -> { throw new GooglePesquisaWebOcupadaException(); });
        assertThatThrownBy(() -> client.pesquisar(request)).isInstanceOf(GooglePesquisaWebOcupadaException.class);
    }

    @Test
    void deveDecodificarRedirecionamentoDoBing() {
        var pagina = fixture(URI.create("https://www.bing.com/search?q=x"), "bing-resultados.html");

        var resultados = parser.extrair(FontePesquisaWeb.BING, pagina, 10);

        assertThat(resultados).extracting(GoogleResultadoWeb::url).containsExactly(
            URI.create("https://www.instagram.com/padariaaurora/"),
            URI.create("https://padariaaurora.example/"));
        assertThat(resultados.get(0).resumo()).isEqualTo("Perfil no Instagram da Padaria Aurora, em Campinas.");
    }

    @Test
    void parserBingTrataAusenciaEBloqueio() {
        assertThat(parser.extrair(FontePesquisaWeb.BING,
            fixture(URI.create("https://www.bing.com/search?q=x"), "bing-sem-resultados.html"), 10)).isEmpty();
        assertThatThrownBy(() -> parser.extrair(FontePesquisaWeb.BING,
            fixture(URI.create("https://www.bing.com/search?q=x"), "bing-captcha.html"), 10))
            .isInstanceOf(GooglePesquisaWebBloqueadaException.class);
    }

    @Test
    void parsersRecusamPaginaDesconhecidaEDesafioMesmoComLinks() {
        for (FontePesquisaWeb fonte : List.of(FontePesquisaWeb.BING, FontePesquisaWeb.DUCKDUCKGO, FontePesquisaWeb.BRAVE)) {
            assertThatThrownBy(() -> parser.extrair(fonte, new GooglePesquisaPagina(fonte.endereco, 200, "", "<html>novo layout</html>"), 10))
                .isInstanceOf(GooglePesquisaWebFormatoInvalidoException.class);
            assertThatThrownBy(() -> parser.extrair(fonte, new GooglePesquisaPagina(fonte.endereco, 200, "", "<html>Verify you are human</html>"), 10))
                .isInstanceOf(GooglePesquisaWebBloqueadaException.class);
            assertThat(parser.extrair(fonte, new GooglePesquisaPagina(fonte.endereco, 200, "", "<html>No results found</html>"), 10)).isEmpty();
        }
    }

    @Test
    void destinosPermitidosNaoIncluemApisCredenciaisPortasOuDominiosDeResultados() {
        for (FontePesquisaWeb fonte : FontePesquisaWeb.values()) assertThat(FontePesquisaWeb.deDestino(fonte.endereco)).isEqualTo(fonte);
        for (String uri : List.of("https://api.search.brave.com/search", "https://evil.example/search",
            "https://user:pass@www.google.com/search", "https://www.google.com:8443/search",
            "https://html.duckduckgo.com/l/?uddg=https://evil.example")) {
            assertThatThrownBy(() -> FontePesquisaWeb.deDestino(URI.create(uri))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(FontePesquisaWeb.GOOGLE.permiteRecurso(URI.create("https://places.googleapis.com/search"))).isFalse();
        assertThat(FontePesquisaWeb.BRAVE.permiteRecurso(URI.create("https://instagram.com/perfil"))).isFalse();
        assertThat(FontePesquisaWeb.BING.permiteRecurso(URI.create("https://instagram.com/perfil"))).isFalse();
        assertThat(FontePesquisaWeb.BING.permiteRecurso(URI.create("https://r.bing.com/rp/app.js"))).isTrue();
    }

    private PesquisaWebFallbackClient client(GooglePesquisaGateway google, GooglePesquisaWebNavigator navegador) {
        return new PesquisaWebFallbackClient(google, navegador, parser, r -> "\"Padaria Aurora\" Campinas site:instagram.com", agora::get);
    }

    private GooglePesquisaWebResponse respostaGoogle() {
        return new GooglePesquisaWebResponse("place-1", TipoPesquisaWeb.INSTAGRAM, "q",
            List.of(new GoogleResultadoWeb(URI.create("https://google.example/"), "t", "d")));
    }

    private GooglePesquisaPagina fixture(URI uri, String nome) {
        try (var stream = getClass().getResourceAsStream("/pesquisa/" + nome)) {
            return new GooglePesquisaPagina(uri, 200, "simulado", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
}
