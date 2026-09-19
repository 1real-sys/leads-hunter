package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LeitorPaginaCandidataTest {

    private static final long TIMEOUT = 1_000;
    private static final int MAX = 65_536;

    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1/x", "http://localhost/x", "http://[::1]/x", "http://10.0.0.1/x",
        "http://192.168.0.1/x", "http://172.16.0.1/x", "http://169.254.169.254/latest/meta-data",
        "http://0.0.0.0/x", "file:///etc/passwd", "ftp://exemplo.com/x",
        "http://usuario:senha@exemplo.com/x", "http://exemplo.com:8080/x"
    })
    void deveBloquearDestinoForaDoEscopoPublico(String url) {
        assertThat(LeitorPaginaCandidata.destinoPublico(URI.create(url))).isFalse();
    }

    @Test
    void devePermitirSomenteDestinoHttpPublico() {
        assertThat(LeitorPaginaCandidata.destinoPublico(URI.create("http://8.8.8.8/x"))).isTrue();
        assertThat(LeitorPaginaCandidata.destinoPublico(null)).isFalse();
    }

    @Test
    void deveExtrairMetaDescricaoECorpo() {
        var leitor = leitorQueDevolve(new LeitorPaginaCandidata.Resposta(200, "text/html; charset=utf-8",
            ("<html><head><meta name=\"description\" content=\"Fone: (28) 3542-1440\">"
                + "<meta property=\"og:description\" content=\"Endereco da loja\"></head>"
                + "<body>Avenida Ministro Araripe, 288</body></html>").getBytes(StandardCharsets.UTF_8)));
        assertThat(leitor.ler(URI.create("https://exemplo.com/perfil")))
            .get().asString()
            .contains("Fone: (28) 3542-1440")
            .contains("Endereco da loja")
            .contains("Avenida Ministro Araripe, 288");
    }

    @Test
    void deveExtrairJsonEstruturadoComTelefone() {
        var leitor = leitorQueDevolve(new LeitorPaginaCandidata.Resposta(200, "text/html",
            ("<html><head></head><body><script type=\"application/json\">"
                + "{\"bio_links\":[{\"url\":\"http://wa.me/5528999146676\"}]}</script><p>Multishow Castelo</p>"
                + "</body></html>").getBytes(StandardCharsets.UTF_8)));
        assertThat(leitor.ler(URI.create("https://www.instagram.com/multishowcastelo"))).get().asString()
            .contains("wa.me/5528999146676").contains("Multishow Castelo");
    }

    @Test
    void deveExtrairLinksDeInstagramAbsolutosEProtocolRelative() {
        var leitor = leitorQueDevolve(new LeitorPaginaCandidata.Resposta(200, "text/html",
            ("<html><body><a href=\"https://www.instagram.com/Perfil/?utm_source=site\">Instagram</a>"
                + "<a href=\"//instagram.com/outro.perfil\">Outro</a>"
                + "<a href=\"https://instagram.com/perfil/p/123\">Publicação</a>"
                + "<a href=\"https://instagram.com/perfil/reel/123\">Reel</a>"
                + "<link rel=\"me\" href=\"https://instagram.com/perfil\"></body></html>")
                .getBytes(StandardCharsets.UTF_8)));

        assertThat(leitor.lerPagina(URI.create("https://exemplo.com/contato"))).get()
            .extracting(PaginaLida::links)
            .isEqualTo(List.of(
                URI.create("https://www.instagram.com/perfil"),
                URI.create("https://www.instagram.com/outro.perfil")
            ));
    }

    @Test
    void deveExtrairLinksDeAtributosDinamicosEJsonEmbutido() {
        var leitor = leitorQueDevolve(new LeitorPaginaCandidata.Resposta(200, "text/html",
            ("<html><body>"
                + "<button data-href=\"https://www.instagram.com/petz/\">Instagram</button>"
                + "<script type=\"application/json\">"
                + "{\"instagram\":\"https:\\/\\/instagram.com\\/petz_\"}"
                + "</script>"
                + "</body></html>").getBytes(StandardCharsets.UTF_8)));

        assertThat(leitor.lerPagina(URI.create("https://exemplo.com"))).get()
            .extracting(PaginaLida::links)
            .isEqualTo(List.of(
                URI.create("https://www.instagram.com/petz"),
                URI.create("https://www.instagram.com/petz_")
            ));
    }

    @Test
    void deveDescartarLinksDeInstagramParaDestinoPrivadoOuNaoPerfil() {
        var leitor = leitorQueDevolve(new LeitorPaginaCandidata.Resposta(200, "text/html",
            ("<html><body><a href=\"http://127.0.0.1/perfil\">Privado</a>"
                + "<a href=\"https://instagram.com/perfil/p/123\">Publicação</a>"
                + "<a href=\"https://instagram.com/perfilvalido\">Perfil</a></body></html>")
                .getBytes(StandardCharsets.UTF_8)));

        assertThat(leitor.lerPagina(URI.create("https://exemplo.com"))).get()
            .extracting(PaginaLida::links)
            .isEqualTo(List.of(URI.create("https://www.instagram.com/perfilvalido")));
    }

    @Test
    void deveManterTextoEIndicarQuandoNaoHaLinks() {
        var leitor = leitorQueDevolve(new LeitorPaginaCandidata.Resposta(200, "text/html",
            "<html><body>Telefone (28) 3542-1440</body></html>".getBytes(StandardCharsets.UTF_8)));

        assertThat(leitor.lerPagina(URI.create("https://exemplo.com"))).get()
            .satisfies(pagina -> {
                assertThat(pagina.texto()).contains("Telefone (28) 3542-1440");
                assertThat(pagina.links()).isEmpty();
            });
    }

    @Test
    void deveRetornarPaginaComLinksMesmoSemTexto() {
        var leitor = leitorQueDevolve(new LeitorPaginaCandidata.Resposta(200, "text/html",
            "<html><body><a href=\"https://instagram.com/perfil\"></a></body></html>"
                .getBytes(StandardCharsets.UTF_8)));

        assertThat(leitor.lerPagina(URI.create("https://exemplo.com"))).get()
            .extracting(PaginaLida::links)
            .isEqualTo(List.of(URI.create("https://www.instagram.com/perfil")));
        assertThat(leitor.ler(URI.create("https://exemplo.com"))).isEmpty();
    }

    @Test
    void deveIgnorarRespostaDeErroTipoInvalidoOuVazia() {
        assertThat(leitorQueDevolve(new LeitorPaginaCandidata.Resposta(302, "text/html",
            "<b>x</b>".getBytes(StandardCharsets.UTF_8))).ler(URI.create("https://exemplo.com"))).isEmpty();
        assertThat(leitorQueDevolve(new LeitorPaginaCandidata.Resposta(200, "application/pdf",
            "binario".getBytes(StandardCharsets.UTF_8))).ler(URI.create("https://exemplo.com"))).isEmpty();
        assertThat(leitorQueDevolve(new LeitorPaginaCandidata.Resposta(200, "text/html",
            new byte[0])).ler(URI.create("https://exemplo.com"))).isEmpty();
        assertThat(leitorQueDevolve(new LeitorPaginaCandidata.Resposta(200, null,
            "sem tipo".getBytes(StandardCharsets.UTF_8))).ler(URI.create("https://exemplo.com"))).isEmpty();
    }

    @Test
    void falhaTecnicaNaoDevePropagar() {
        var leitor = new LeitorPaginaCandidata((uri, timeout, max) -> {
            throw new IOException("timeout");
        }, uri -> true, TIMEOUT, MAX);
        assertThat(leitor.ler(URI.create("https://exemplo.com"))).isEmpty();
    }

    @Test
    void destinoRecusadoNaoDeveAcionarTransporte() {
        var chamou = new AtomicBoolean(false);
        var leitor = new LeitorPaginaCandidata((uri, timeout, max) -> {
            chamou.set(true);
            return new LeitorPaginaCandidata.Resposta(200, "text/html", "x".getBytes(StandardCharsets.UTF_8));
        }, uri -> false, TIMEOUT, MAX);
        assertThat(leitor.ler(URI.create("https://exemplo.com"))).isEmpty();
        assertThat(chamou).isFalse();
    }

    @Test
    void leitorNenhumNaoDeveLerNada() {
        assertThat(LeitorPaginaCandidata.nenhum().ler(URI.create("https://exemplo.com"))).isEmpty();
    }

    private LeitorPaginaCandidata leitorQueDevolve(LeitorPaginaCandidata.Resposta resposta) {
        return new LeitorPaginaCandidata((uri, timeout, max) -> resposta, uri -> true, TIMEOUT, MAX);
    }
}
