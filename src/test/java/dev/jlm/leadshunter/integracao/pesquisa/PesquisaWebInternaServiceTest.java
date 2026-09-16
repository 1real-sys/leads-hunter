package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PesquisaWebInternaServiceTest {

    @Test
    void deveExecutarUmaConsultaPorTipoEClassificarOsResultados() {
        List<GooglePesquisaWebRequest> requests = new ArrayList<>();
        List<GoogleResultadoWeb> resultadosInstagram = List.of(
            new GoogleResultadoWeb(
                URI.create("https://instagram.com/padariacentral"),
                "Padaria Central Instagram",
                "Campinas"
            )
        );
        List<GoogleResultadoWeb> resultadosSite = List.of(
            new GoogleResultadoWeb(
                URI.create("https://padariacentral.example"),
                "Padaria Central Site",
                "Campinas"
            )
        );
        GooglePesquisaGateway client = request -> {
            requests.add(request);
            List<GoogleResultadoWeb> resultados = request.tipo() == TipoPesquisaWeb.INSTAGRAM
                ? resultadosInstagram
                : resultadosSite;
            return new GooglePesquisaWebResponse(
                request.googlePlaceId(),
                request.tipo(),
                "consulta",
                resultados
            );
        };
        ClassificadorUrlService classificador = new ClassificadorUrlService(new UrlCandidatoCanonicalizer());
        PesquisaWebInternaService service = new PesquisaWebInternaService(client, classificador);
        PesquisaLeadDados lead = new PesquisaLeadDados(
            "place-123",
            "Padaria Central",
            CategoriaNegocio.PADARIA,
            "Rua das Flores, 10, Campinas - SP",
            "Rua das Flores",
            "10",
            "Centro",
            "Campinas",
            "SP",
            "5519999999999",
            "12345678000190",
            "Padaria Central Ltda"
        );
        PesquisaInformacoesWebResultado resultado = service.pesquisar(lead);

        assertThat(resultado.instagram()).contains(URI.create("https://www.instagram.com/padariacentral"));
        assertThat(resultado.siteProprio()).contains(URI.create("https://padariacentral.example/"));
        assertThat(requests).extracting(GooglePesquisaWebRequest::tipo)
            .containsExactly(TipoPesquisaWeb.INSTAGRAM, TipoPesquisaWeb.SITE_PROPRIO);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.googlePlaceId()).isEqualTo("place-123");
            assertThat(request.nome()).isEqualTo("Padaria Central");
            assertThat(request.categoria()).isEqualTo(CategoriaNegocio.PADARIA);
            assertThat(request.municipio()).isEqualTo("Campinas");
        });
    }

    @Test
    void deveTentarConsultaSemMunicipioQuandoNaoAcharInstagramComLocalizacao() {
        List<GooglePesquisaWebRequest> requests = new ArrayList<>();
        GooglePesquisaGateway client = request -> {
            requests.add(request);
            if (request.tipo() == TipoPesquisaWeb.INSTAGRAM && request.municipio() != null) {
                return new GooglePesquisaWebResponse(request.googlePlaceId(), request.tipo(), "q", List.of(
                    new GoogleResultadoWeb(URI.create("https://www.instagram.com/outraloja/"),
                        "Outra Loja", "Outra cidade")));
            }
            if (request.tipo() == TipoPesquisaWeb.INSTAGRAM) {
                return new GooglePesquisaWebResponse(request.googlePlaceId(), request.tipo(), "q", List.of(
                    new GoogleResultadoWeb(URI.create("https://www.instagram.com/supermercadomichel/"),
                        "Supermercado Michel (@supermercadomichel) • Instagram", "Castelo - ES")));
            }
            return new GooglePesquisaWebResponse(request.googlePlaceId(), request.tipo(), "q", List.of());
        };
        PesquisaWebInternaService service = new PesquisaWebInternaService(client,
            new ClassificadorUrlService(new UrlCandidatoCanonicalizer()));
        PesquisaLeadDados lead = new PesquisaLeadDados("place-1", "Supermercado Michel", CategoriaNegocio.MERCADO,
            "Rua das Flores, 10, Castelo - ES", "Rua das Flores", "10", "Centro", "Castelo", "ES",
            null, null, null);

        PesquisaInformacoesWebResultado resultado = service.pesquisar(lead);

        assertThat(resultado.instagram()).contains(URI.create("https://www.instagram.com/supermercadomichel"));
        assertThat(requests).extracting(GooglePesquisaWebRequest::tipo)
            .containsExactly(TipoPesquisaWeb.INSTAGRAM, TipoPesquisaWeb.SITE_PROPRIO, TipoPesquisaWeb.INSTAGRAM);
        assertThat(requests.get(2).municipio()).isNull();
        assertThat(requests.get(2).uf()).isNull();
    }

    @Test
    void deveAproveitarInstagramNaConsultaDeSiteSemTerceiraChamada() {
        List<GooglePesquisaWebRequest> requests = new ArrayList<>();
        GooglePesquisaGateway client = request -> {
            requests.add(request);
            return resposta(request, request.tipo() == TipoPesquisaWeb.SITE_PROPRIO ? List.of(
                candidato("https://instagram.com/padariaaurora", "Campinas - SP"),
                candidato("https://padariaaurora.example", "Campinas - SP")) : List.of());
        };
        var resultado = new PesquisaWebInternaService(client,
            new ClassificadorUrlService(new UrlCandidatoCanonicalizer())).pesquisar(leadAurora());
        assertThat(resultado.instagram()).isPresent();
        assertThat(resultado.siteProprio()).isPresent();
        assertThat(requests).hasSize(2);
    }

    @Test
    void terceiraConsultaNaoDeveApagarAmbiguidadeAnterior() {
        GooglePesquisaGateway client = request -> resposta(request,
            request.tipo() == TipoPesquisaWeb.SITE_PROPRIO ? List.of() : request.municipio() == null
                ? List.of(candidato("https://instagram.com/padariaaurora", "Campinas - SP"))
                : List.of(candidato("https://instagram.com/padariaaurora", "Campinas - SP"),
                    candidato("https://instagram.com/padariaauroraoficial", "Campinas - SP")));
        var resultado = new PesquisaWebInternaService(client,
            new ClassificadorUrlService(new UrlCandidatoCanonicalizer())).pesquisar(leadAurora());
        assertThat(resultado.instagram()).isEmpty();
    }

    @Test
    void terceiraConsultaNaoDeveOcultarLocalizacaoDivergenteDoMesmoPerfil() {
        GooglePesquisaGateway client = request -> resposta(request,
            request.tipo() == TipoPesquisaWeb.SITE_PROPRIO ? List.of() : List.of(candidato(
                "https://instagram.com/padariaaurora", request.municipio() == null ? "Campinas - SP" : "Curitiba - PR")));
        var resultado = new PesquisaWebInternaService(client,
            new ClassificadorUrlService(new UrlCandidatoCanonicalizer())).pesquisar(leadAurora());
        assertThat(resultado.instagram()).isEmpty();
    }

    @Test
    void deveLimitarAberturasDePaginaPorLead() {
        int[] aberturas = {0};
        List<GoogleResultadoWeb> candidatos = new ArrayList<>();
        for (String usuario : List.of("michela", "michelb", "michelc", "micheld", "michele")) {
            candidatos.add(new GoogleResultadoWeb(URI.create("https://instagram.com/" + usuario),
                "Supermercado Michel " + usuario, "Fotos e vídeos"));
        }
        GooglePesquisaGateway client = request -> new GooglePesquisaWebResponse(request.googlePlaceId(),
            request.tipo(), "q", request.tipo() == TipoPesquisaWeb.INSTAGRAM ? candidatos : List.of());
        var leitor = new LeitorPaginaCandidata(
            (uri, timeout, max) -> {
                aberturas[0]++;
                return new LeitorPaginaCandidata.Resposta(200, "text/html",
                    "sem dados".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }, uri -> true, 1_000, 65_536);
        var lead = new PesquisaLeadDados("place-1", "Supermercado Michel", CategoriaNegocio.MERCADO,
            null, null, null, null, null, "ES", null, null, null);

        var resultado = new PesquisaWebInternaService(client,
            new ClassificadorUrlService(new UrlCandidatoCanonicalizer()), leitor).pesquisar(lead);

        assertThat(resultado.instagram()).isEmpty();
        assertThat(aberturas[0]).isEqualTo(3);
    }

    private GooglePesquisaWebResponse resposta(GooglePesquisaWebRequest request, List<GoogleResultadoWeb> candidatos) {
        return new GooglePesquisaWebResponse(request.googlePlaceId(), request.tipo(), "q", candidatos);
    }

    private GoogleResultadoWeb candidato(String url, String resumo) {
        return new GoogleResultadoWeb(URI.create(url), "Padaria Aurora", resumo);
    }

    private PesquisaLeadDados leadAurora() {
        return new PesquisaLeadDados("place-1", "Padaria Aurora", CategoriaNegocio.PADARIA,
            null, null, null, null, "Campinas", "SP", null, null, null);
    }
}
