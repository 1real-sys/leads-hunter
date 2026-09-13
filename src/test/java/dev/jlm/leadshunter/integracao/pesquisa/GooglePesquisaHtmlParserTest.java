package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class GooglePesquisaHtmlParserTest {

    private final GooglePesquisaHtmlParser parser = new GooglePesquisaHtmlParser();

    @Test
    void deveExtrairUrlTituloEResumoDaFixtureRenderizada() throws IOException {
        List<GoogleResultadoWeb> resultados = parser.extrair(pagina("google-resultados.html", 200), 10);

        assertThat(resultados).hasSize(2);
        assertThat(resultados.getFirst().url()).isEqualTo(URI.create("https://www.padariacentral.example/"));
        assertThat(resultados.getFirst().titulo()).isEqualTo("Padaria Central | Site oficial");
        assertThat(resultados.getFirst().resumo()).contains("Encomendas de pães e doces em Campinas");
        assertThat(resultados.get(1).url()).isEqualTo(URI.create("https://www.instagram.com/padariacentral/"));
        assertThat(resultados.get(1).resumo()).contains("Fotos e novidades");
    }

    @Test
    void deveRetornarListaVaziaSomenteQuandoAPaginaDeclaraAusenciaReal() throws IOException {
        assertThat(parser.extrair(pagina("google-sem-resultados.html", 200), 10)).isEmpty();
    }

    @Test
    void deveIdentificarCaptchaSemExporOHtml() throws IOException {
        assertThatThrownBy(() -> parser.extrair(pagina("google-captcha.html", 429), 10))
            .isInstanceOf(GooglePesquisaWebBloqueadaException.class)
            .hasMessage("O Google bloqueou temporariamente a pesquisa automatizada.")
            .hasMessageNotContaining("captcha");
    }

    @Test
    void deveFalharDeFormaSeguraQuandoOFormatoMudar() throws IOException {
        assertThatThrownBy(() -> parser.extrair(pagina("google-formato-alterado.html", 200), 10))
            .isInstanceOf(GooglePesquisaWebFormatoInvalidoException.class)
            .hasMessage("O formato da página de pesquisa mudou ou não pôde ser reconhecido.")
            .hasMessageNotContaining("novo-componente");
    }

    private GooglePesquisaPagina pagina(String fixture, int status) throws IOException {
        String html = new ClassPathResource("pesquisa/" + fixture)
            .getContentAsString(StandardCharsets.UTF_8);
        return new GooglePesquisaPagina(
            URI.create("https://www.google.com/search?q=padaria"),
            status,
            "Google",
            html
        );
    }
}
