package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlCandidatoCanonicalizerTest {

    private final UrlCandidatoCanonicalizer canonicalizer = new UrlCandidatoCanonicalizer();

    @Test
    void deveCanonicalizarSomentePerfilDoInstagram() {
        assertThat(canonicalizer.canonicalizar(
            URI.create("https://m.instagram.com/Minha.Padaria/?igsh=abc#bio"),
            TipoPesquisaWeb.INSTAGRAM
        )).contains(URI.create("https://www.instagram.com/minha.padaria"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://www.instagram.com/p/ABC123/",
        "https://instagram.com/reel/ABC123/",
        "https://instagram.com/stories/padaria/123/",
        "https://instagram.com/explore/tags/padaria/",
        "https://instagram.com/accounts/login/",
        "https://instagram.com/share/ABC123/",
        "https://instagram.com/usuario/inbox"
    })
    void deveRejeitarConteudoEAreasInternasDoInstagram(String url) {
        assertThat(canonicalizer.canonicalizar(URI.create(url), TipoPesquisaWeb.INSTAGRAM)).isEmpty();
    }

    @Test
    void devePreservarPaginaERemoverRastreamentoEFragmentoDoSite() {
        assertThat(canonicalizer.canonicalizar(
            URI.create("https://WWW.PadariaCentral.com.br/cardapio?utm_source=google&gclid=123#paes"),
            TipoPesquisaWeb.SITE_PROPRIO
        )).contains(URI.create("https://www.padariacentral.com.br/cardapio"));
    }

    @Test
    void devePreservarEscapesDoCaminhoSemTransformarBarraCodificadaEmOutroRecurso() {
        assertThat(canonicalizer.canonicalizar(
            URI.create("https://loja.example/unidades/S%C3%A3o%2FJos%C3%A9?utm_source=brave#endereco"),
            TipoPesquisaWeb.SITE_PROPRIO
        )).contains(URI.create("https://loja.example/unidades/S%C3%A3o%2FJos%C3%A9"));
    }

    @Test
    void deveDeduplicarPaginasEquivalentesSemMisturarFiliaisDoMesmoDominio() {
        String pagina = canonicalizer.chaveDeduplicacao(URI.create("https://www.loja.example/filial-a/"),
            TipoPesquisaWeb.SITE_PROPRIO);
        assertThat(pagina).isEqualTo(canonicalizer.chaveDeduplicacao(URI.create("http://loja.example/filial-a"),
            TipoPesquisaWeb.SITE_PROPRIO));
        assertThat(pagina).isNotEqualTo(canonicalizer.chaveDeduplicacao(URI.create("https://loja.example/filial-b"),
            TipoPesquisaWeb.SITE_PROPRIO));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://linktr.ee/padaria",
        "https://www.instagram.com/padaria",
        "https://maps.google.com/place/padaria",
        "https://www.ifood.com.br/delivery/padaria",
        "https://www.tripadvisor.com.br/Restaurant_Review-x",
        "https://padaria.mercadolivre.com.br/",
        "http://localhost/padaria",
        "http://127.0.0.1/padaria",
        "https://padaria.example.com:8443/"
    })
    void deveRejeitarAgregadoresDiretoriosRedesEDestinosInseguros(String url) {
        assertThat(canonicalizer.canonicalizar(URI.create(url), TipoPesquisaWeb.SITE_PROPRIO)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "javascript:alert(1)",
        "ftp://padaria.example.com/arquivo",
        "https://usuario:senha@padaria.example.com/",
        "https://intranet/"
    })
    void deveRejeitarUrlMalformadaOuComProtocoloNaoPermitido(String url) {
        assertThat(canonicalizer.canonicalizar(URI.create(url), TipoPesquisaWeb.SITE_PROPRIO)).isEmpty();
    }
}
