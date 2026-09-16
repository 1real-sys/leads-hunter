package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

/**
 * A descoberta do perfil de filial não pode exigir que todo o nome comercial (com categoria no
 * plural e complemento de praça) caiba no handle. Marca + discriminante contíguos bastam.
 */
class PesquisaRamoIdentificadorTest {

    private static final URI PERFIL = URI.create("https://www.instagram.com/multishowcastelo");
    private final ClassificadorUrlService classificador = new ClassificadorUrlService(new UrlCandidatoCanonicalizer());

    @Test
    void replayRealDeveEscolherOHandleDeRamoEPreservarRedeERamasVizinhas() throws Exception {
        try (var entrada = getClass().getResourceAsStream("/pesquisa/brave-multishow-castelo.json")) {
            var amostra = new ObjectMapper().readValue(entrada, Amostra.class);
            List<GooglePesquisaWebRequest> consultas = new ArrayList<>();
            GooglePesquisaGateway replay = request -> {
                consultas.add(request);
                return amostra.respostas().stream()
                    .filter(r -> r.tipo() == request.tipo()
                        && r.consulta().equals(BravePesquisaApiClient.montarConsulta(request)))
                    .findFirst().orElseThrow();
            };

            var resultado = new PesquisaWebInternaService(replay, classificador).pesquisar(amostra.lead());

            assertThat(resultado.instagram()).contains(PERFIL);
            assertThat(resultado.siteProprio()).isEmpty();
            // O perfil é aceito na primeira análise; não precisa da consulta sem município.
            assertThat(consultas).hasSize(2);
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Multishow Supermercados Castelo - Volta Redonda | Castelo | Multishow Castelo | multishowcastelo",
        "Supermercados Aurora Castelo | Castelo | Aurora Castelo | auroracastelo",
        "Drogarias Aurora Castelo | Castelo | Aurora Castelo | auroracastelo",
        "Farmacia Sao Joao Niteroi | Niteroi | Sao Joao Niteroi | saojoaoniteroi"
    })
    void handleDeRamoComMarcaEDiscriminanteContiguosDeveSerAceito(
        String nome, String municipio, String titulo, String usuario
    ) {
        String url = "https://www.instagram.com/" + usuario;
        assertThat(classificar(lead(nome, municipio), candidato(url, titulo, "Veja as fotos e vídeos no Instagram")))
            .contains(URI.create(url));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Multishow Supermercados Castelo - Volta Redonda | Castelo | multishowsupermercados",
        "Multishow Supermercados Castelo - Volta Redonda | Castelo | multishowvitoria",
        "Multishow Supermercados Castelo - Volta Redonda | Castelo | castelosuper"
    })
    void handleDeRedeOuDeOutraPracaNaoDeveSerAceito(String nome, String municipio, String usuario) {
        String url = "https://www.instagram.com/" + usuario;
        assertThat(classificar(lead(nome, municipio), candidato(url, usuario, "Veja as fotos e vídeos no Instagram")))
            .isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Mercado Castelo | Castelo | mercadocastelo",
        "Padaria Sao Jose | Sao Jose | saojose"
    })
    void identificadorQueSoRepeteOPracaNaoDeveSerAceito(String nome, String municipio, String usuario) {
        String url = "https://www.instagram.com/" + usuario;
        assertThat(classificar(lead(nome, municipio), candidato(url, nome, "Veja as fotos e vídeos no Instagram")))
            .isEmpty();
    }

    @Test
    void ramoConfirmadoNaoDeveBurlarOVetoDeDddDivergente() {
        assertThat(classificar(lead("Multishow Supermercados Castelo", "Castelo"), candidato(
            PERFIL.toString(), "Multishow Castelo", "Veja as fotos. Telefone: (27) 99999-0000"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://multishowcastelo.com.br/",
        "https://multishowcastelo.example/loja"
    })
    void regraDeRamoNaoDeveLegitimarSiteProprio(String url) {
        assertThat(classificador.classificar(lead("Multishow Supermercados Castelo", "Castelo"),
            List.of(), List.of(candidato(url, "Multishow Castelo", "Ofertas"))).siteProprio()).isEmpty();
    }

    private java.util.Optional<URI> classificar(PesquisaLeadDados lead, GoogleResultadoWeb... resultados) {
        return classificador.classificar(lead, List.of(resultados), List.of()).instagram();
    }

    private PesquisaLeadDados lead(String nome, String municipio) {
        return new PesquisaLeadDados("fixture-ramo", nome, CategoriaNegocio.MERCADO,
            null, null, null, null, municipio, "ES", "5528999146676", null, null);
    }

    private GoogleResultadoWeb candidato(String url, String titulo, String resumo) {
        return new GoogleResultadoWeb(URI.create(url), titulo, resumo);
    }

    record Amostra(PesquisaLeadDados lead, List<GooglePesquisaWebResponse> respostas) {}
}
