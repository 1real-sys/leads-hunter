package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BravePesquisaApiClientTest {

    private static final String URL_RESULTADOS = """
        {"web":{"results":[
          {"url":"https://www.instagram.com/padariaaurora","title":"Padaria <strong>Aurora</strong>","description":"Perfil no <strong>Instagram</strong>"},
          {"url":"https://padariaaurora.example/","title":"Padaria Aurora","description":"Site oficial"}
        ]}}
        """;

    private final Function<GooglePesquisaWebRequest, String> consulta = request -> "\"Padaria Aurora\" site:instagram.com";

    @Test
    void confirmacaoDevePesquisarPerfilETelefoneNoProvedorFixoSemUsarNomeComoRestricao() {
        var uriCapturada = new AtomicReference<URI>();
        var cliente = new BravePesquisaApiClient((uri, token, timeout, limite) -> {
            uriCapturada.set(uri);
            return new BravePesquisaApiClient.Resposta(200, URL_RESULTADOS.getBytes(StandardCharsets.UTF_8));
        }, BravePesquisaApiClient::montarConsulta, "chave", true, 15_000, 10, 65_536);
        var request = new GooglePesquisaWebRequest("place-1", "Drogaria Aurora", CategoriaNegocio.FARMACIA,
            null, null, null, TipoPesquisaWeb.INSTAGRAM, new ConfirmacaoPerfilInstagram("farmaaurora", "19999999999"));

        assertThat(cliente.pesquisar(request).consulta()).isEqualTo("farmaaurora 19 99999-9999");
        assertThat(uriCapturada.get().getHost()).isEqualTo("api.search.brave.com");
        assertThat(uriCapturada.get().getQuery()).contains("q=farmaaurora 19 99999-9999", "operators=false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example", "aurora site:evil", "aurora\nsite:evil", "aurora/contato"})
    void confirmacaoNaoDeveAceitarUrlOuOperadoresNoUsuario(String usuario) {
        assertThatThrownBy(() -> new ConfirmacaoPerfilInstagram(usuario, "19999999999"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveMontarConsultaPropriaSemOperadoresDoGoogle() {
        assertThat(BravePesquisaApiClient.montarConsulta(request()))
            .isEqualTo("Padaria Aurora Campinas SP instagram");
        var site = new GooglePesquisaWebRequest("place-1", "Padaria Aurora", CategoriaNegocio.PADARIA,
            null, "Campinas", "SP", TipoPesquisaWeb.SITE_PROPRIO);

        assertThat(BravePesquisaApiClient.montarConsulta(site)).isEqualTo("Padaria Aurora Campinas SP");
    }

    @Test
    void deveExtrairResultadosELimparHtmlDoTituloEDescricao() {
        var cliente = cliente(transporte(200, URL_RESULTADOS), true, "chave");

        var resposta = cliente.pesquisar(request());

        assertThat(resposta.googlePlaceId()).isEqualTo("place-1");
        assertThat(resposta.consulta()).isEqualTo("\"Padaria Aurora\" site:instagram.com");
        assertThat(resposta.resultados()).hasSize(2);
        assertThat(resposta.resultados().get(0).url()).isEqualTo(URI.create("https://www.instagram.com/padariaaurora"));
        assertThat(resposta.resultados().get(0).titulo()).isEqualTo("Padaria Aurora");
        assertThat(resposta.resultados().get(0).resumo()).isEqualTo("Perfil no Instagram");
        assertThat(resposta.resultados().get(1).url()).isEqualTo(URI.create("https://padariaaurora.example/"));
    }

    @Test
    void deveMontarUrlDaApiEEnviarToken() {
        var uriCapturada = new AtomicReference<URI>();
        var tokenCapturado = new AtomicReference<String>();
        var cliente = cliente((uri, token, timeout, limite) -> {
            uriCapturada.set(uri);
            tokenCapturado.set(token);
            return new BravePesquisaApiClient.Resposta(200, URL_RESULTADOS.getBytes(StandardCharsets.UTF_8));
        }, true, "segredo-brave");

        cliente.pesquisar(request());

        assertThat(uriCapturada.get().getHost()).isEqualTo("api.search.brave.com");
        assertThat(uriCapturada.get().getPath()).isEqualTo("/res/v1/web/search");
        assertThat(uriCapturada.get().getQuery()).contains("count=10", "country=BR", "search_lang=pt-br",
            "spellcheck=false", "operators=false", "extra_snippets=true", "text_decorations=false", "result_filter=web");
        assertThat(tokenCapturado.get()).isEqualTo("segredo-brave");
    }

    @Test
    void ausenciaDeResultadosEConclusivaENaoGeraErro() {
        var cliente = cliente(transporte(200, "{\"web\":{}}"), true, "chave");

        assertThat(cliente.pesquisar(request()).resultados()).isEmpty();
    }

    @Test
    void deveUsarTrechosAdicionaisDaMesmaUrlSemDuplicarEComLimites() {
        String json = """
            {"web":{"results":[{"url":"https://instagram.com/padariaaurora",
              "title":"Padaria Aurora", "description":"Padaria Aurora",
              "extra_snippets":["Padaria Aurora","<b>Campinas - SP</b>","(19) 99999-9999",42,"%s","IGNORADO"]}]}}
            """.formatted("a".repeat(700));
        var resposta = cliente(transporte(200, json), true, "chave").pesquisar(request());
        assertThat(resposta.resultados().getFirst().resumo())
            .isEqualTo("Padaria Aurora\nCampinas - SP\n(19) 99999-9999\n" + "a".repeat(500));
        var lead = new PesquisaLeadDados("place-1", "Padaria Aurora", CategoriaNegocio.PADARIA,
            null, null, null, null, "Campinas", "SP", null, null, null);
        assertThat(new ClassificadorUrlService(new UrlCandidatoCanonicalizer())
            .classificar(lead, resposta.resultados(), java.util.List.of()).instagram()).isPresent();
    }

    @Test
    void deveMapearCotaExcedidaParaBloqueio() {
        var cliente = cliente(transporte(429, "{}"), true, "chave");

        assertThatThrownBy(() -> cliente.pesquisar(request()))
            .isInstanceOf(GooglePesquisaWebBloqueadaException.class);
    }

    @Test
    void deveMapearCredencialRejeitadaEIndisponibilidade() {
        assertThatThrownBy(() -> cliente(transporte(401, "{}"), true, "chave").pesquisar(request()))
            .isInstanceOf(GooglePesquisaWebIndisponivelException.class);
        assertThatThrownBy(() -> cliente(transporte(403, "{}"), true, "chave").pesquisar(request()))
            .isInstanceOf(GooglePesquisaWebIndisponivelException.class);
        assertThatThrownBy(() -> cliente(transporte(503, "{}"), true, "chave").pesquisar(request()))
            .isInstanceOf(GooglePesquisaWebIndisponivelException.class);
    }

    @Test
    void deveMapearJsonInvalidoParaFormatoInvalido() {
        var cliente = cliente(transporte(200, "{payload-invalido"), true, "chave");

        assertThatThrownBy(() -> cliente.pesquisar(request()))
            .isInstanceOf(GooglePesquisaWebFormatoInvalidoException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{}", "{\"web\":null}", "{\"web\":{\"results\":{}}}"})
    void respostaForaDoContratoNaoPodeVirarAusenciaConclusiva(String json) {
        assertThatThrownBy(() -> cliente(transporte(200, json), true, "chave").pesquisar(request()))
            .isInstanceOf(GooglePesquisaWebFormatoInvalidoException.class);
    }

    @Test
    void respostaDeBuscaSemBlocoWebPodeRepresentarAusenciaValida() {
        assertThat(cliente(transporte(200, "{\"type\":\"search\",\"query\":{\"original\":\"q\"}}"),
            true, "chave").pesquisar(request()).resultados()).isEmpty();
    }

    @Test
    void devePropagarTimeoutDoTransporte() {
        var cliente = cliente((uri, token, timeout, limite) -> {
            throw new GooglePesquisaWebTimeoutException();
        }, true, "chave");

        assertThatThrownBy(() -> cliente.pesquisar(request()))
            .isInstanceOf(GooglePesquisaWebTimeoutException.class);
    }

    @Test
    void naoDevePesquisarSemChaveConfigurada() {
        var cliente = cliente(transporte(200, URL_RESULTADOS), true, " ");

        assertThat(cliente.habilitado()).isFalse();
        assertThatThrownBy(() -> cliente.pesquisar(request()))
            .isInstanceOf(GooglePesquisaWebIndisponivelException.class)
            .hasMessageContaining("Brave");
    }

    @Test
    void deveRejeitarUrlsSemHttpOuComCredenciais() {
        var json = """
            {"web":{"results":[
              {"url":"ftp://arquivo.example/","title":"x","description":"x"},
              {"url":"https://user:senha@site.example/","title":"x","description":"x"},
              {"url":"https://valido.example/","title":"x","description":"x"}
            ]}}
            """;
        var cliente = cliente(transporte(200, json), true, "chave");

        assertThat(cliente.pesquisar(request()).resultados())
            .extracting(GoogleResultadoWeb::url)
            .containsExactly(URI.create("https://valido.example/"));
    }

    private GooglePesquisaWebRequest request() {
        return new GooglePesquisaWebRequest("place-1", "Padaria Aurora", CategoriaNegocio.PADARIA,
            null, "Campinas", "SP", TipoPesquisaWeb.INSTAGRAM);
    }

    private BravePesquisaApiClient.Transporte transporte(int status, String corpo) {
        return (uri, token, timeout, limite) ->
            new BravePesquisaApiClient.Resposta(status, corpo.getBytes(StandardCharsets.UTF_8));
    }

    private BravePesquisaApiClient cliente(BravePesquisaApiClient.Transporte transporte,
                                           boolean habilitado, String chave) {
        return new BravePesquisaApiClient(transporte, consulta, chave, habilitado,
            15_000, 10, 65_536);
    }
}
