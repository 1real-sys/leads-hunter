package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class GooglePesquisaWebClientTest {

    private final GooglePesquisaHtmlParser parser = new GooglePesquisaHtmlParser();

    @Test
    void deveMontarConsultaDeInstagramComNomeCategoriaELocalizacao() {
        NavegadorFake navegador = new NavegadorFake(paginaSemResultados());
        GooglePesquisaWebClient client = new GooglePesquisaWebClient(navegador, parser, 10);

        GooglePesquisaWebResponse response = client.pesquisar(request(TipoPesquisaWeb.INSTAGRAM));

        assertThat(response.googlePlaceId()).isEqualTo("place-123");
        assertThat(response.tipo()).isEqualTo(TipoPesquisaWeb.INSTAGRAM);
        assertThat(response.consulta())
            .isEqualTo("\"Padaria Central\" \"padaria\" \"Campinas SP\" site:instagram.com");
        assertThat(response.resultados()).isEmpty();
        assertThat(navegador.uri.getScheme()).isEqualTo("https");
        assertThat(navegador.uri.getHost()).isEqualTo("www.google.com");
        assertThat(navegador.uri.getPath()).isEqualTo("/search");
        assertThat(navegador.uri.toString()).contains("hl=pt-BR", "gl=br", "num=10", "filter=1");
    }

    @Test
    void deveMontarConsultaDeSiteProprioSemUsarApiOuGooglePlaceIdNaUrl() {
        NavegadorFake navegador = new NavegadorFake(paginaSemResultados());
        GooglePesquisaWebClient client = new GooglePesquisaWebClient(navegador, parser, 10);

        GooglePesquisaWebResponse response = client.pesquisar(request(TipoPesquisaWeb.SITE_PROPRIO));

        assertThat(response.consulta()).isEqualTo(
            "\"Padaria Central\" \"padaria\" \"Campinas SP\" site oficial "
                + "-site:instagram.com -site:facebook.com -site:ifood.com.br "
                + "-site:tripadvisor.com.br -site:linktr.ee"
        );
        assertThat(navegador.uri.toString())
            .doesNotContain("place-123", "api", "key")
            .startsWith("https://www.google.com/search?");
    }

    @Test
    void deveLimitarETratarDadosDoLeadComoTextoDaConsulta() {
        NavegadorFake navegador = new NavegadorFake(paginaSemResultados());
        GooglePesquisaWebClient client = new GooglePesquisaWebClient(navegador, parser, 10);
        GooglePesquisaWebRequest request = new GooglePesquisaWebRequest(
            "place-123",
            "Padaria \"Central\"`\\ site:evil.example",
            CategoriaNegocio.PADARIA,
            null,
            "Campinas",
            "sp",
            TipoPesquisaWeb.INSTAGRAM
        );

        GooglePesquisaWebResponse response = client.pesquisar(request);

        assertThat(response.consulta()).startsWith("\"Padaria Central site:evil.example\"");
        assertThat(navegador.uri.getHost()).isEqualTo("www.google.com");
    }

    @Test
    void devePropagarTimeoutSeguroDoNavegador() {
        GooglePesquisaWebNavigator navegador = uri -> {
            throw new GooglePesquisaWebTimeoutException();
        };
        GooglePesquisaWebClient client = new GooglePesquisaWebClient(navegador, parser, 10);

        assertThatThrownBy(() -> client.pesquisar(request(TipoPesquisaWeb.INSTAGRAM)))
            .isInstanceOf(GooglePesquisaWebTimeoutException.class)
            .hasMessage("A pesquisa externa excedeu o tempo limite.");
    }

    @Test
    void deveSuspenderNovasNavegacoesDuranteCooldownAposBloqueio() {
        AtomicLong agora = new AtomicLong(1_000L);
        NavegadorContador navegador = new NavegadorContador(new GooglePesquisaPagina(
            URI.create("https://www.google.com/sorry/index"),
            429,
            "Google",
            "<html><body><form action=\"/sorry\"><div id=\"captcha\"></div></form></body></html>"
        ));
        GooglePesquisaWebClient client = new GooglePesquisaWebClient(
            navegador,
            parser,
            10,
            3_600_000,
            agora::get
        );

        assertThatThrownBy(() -> client.pesquisar(request(TipoPesquisaWeb.INSTAGRAM)))
            .isInstanceOf(GooglePesquisaWebBloqueadaException.class);
        assertThatThrownBy(() -> client.pesquisar(request(TipoPesquisaWeb.SITE_PROPRIO)))
            .isInstanceOf(GooglePesquisaWebBloqueadaException.class);
        assertThat(navegador.chamadas).isOne();

        agora.addAndGet(3_600_000);
        assertThatThrownBy(() -> client.pesquisar(request(TipoPesquisaWeb.SITE_PROPRIO)))
            .isInstanceOf(GooglePesquisaWebBloqueadaException.class);
        assertThat(navegador.chamadas).isEqualTo(2);
    }

    private GooglePesquisaWebRequest request(TipoPesquisaWeb tipo) {
        return new GooglePesquisaWebRequest(
            "place-123",
            "Padaria Central",
            CategoriaNegocio.PADARIA,
            "Rua das Flores, 10",
            "Campinas",
            "SP",
            tipo
        );
    }

    private GooglePesquisaPagina paginaSemResultados() {
        return new GooglePesquisaPagina(
            URI.create("https://www.google.com/search?q=padaria"),
            200,
            "Google",
            "<html><body><main id=\"search\">"
                + "Sua pesquisa não encontrou nenhum documento correspondente."
                + "</main></body></html>"
        );
    }

    private static final class NavegadorFake implements GooglePesquisaWebNavigator {

        private final GooglePesquisaPagina pagina;
        private URI uri;

        private NavegadorFake(GooglePesquisaPagina pagina) {
            this.pagina = pagina;
        }

        @Override
        public GooglePesquisaPagina navegar(URI uri) {
            this.uri = uri;
            return pagina;
        }
    }

    private static final class NavegadorContador implements GooglePesquisaWebNavigator {

        private final GooglePesquisaPagina pagina;
        private int chamadas;

        private NavegadorContador(GooglePesquisaPagina pagina) {
            this.pagina = pagina;
        }

        @Override
        public GooglePesquisaPagina navegar(URI uri) {
            chamadas++;
            return pagina;
        }
    }
}
