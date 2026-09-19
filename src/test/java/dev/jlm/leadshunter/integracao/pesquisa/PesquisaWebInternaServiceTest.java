package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
    void deveSemearPesquisaPeloSiteOficialEExtrairInstagramSemConsultarBrave() {
        List<GooglePesquisaWebRequest> requests = new ArrayList<>();
        String html = "<html><body>Padaria Aurora Rua das Flores, 10, Campinas - SP "
            + "Telefone (19) 99999-9999 "
            + "<a href=\"https://instagram.com/padariaaurora\">Instagram</a></body></html>";
        var leitor = leitorQueDevolve(html);
        GooglePesquisaGateway client = request -> {
            requests.add(request);
            return resposta(request, List.of());
        };

        var resultado = new PesquisaWebInternaService(client,
            new ClassificadorUrlService(new UrlCandidatoCanonicalizer()), leitor)
            .pesquisar(leadAuroraComSite("https://padariaaurora.example/"));

        assertThat(resultado.siteProprio()).contains(URI.create("https://padariaaurora.example/"));
        assertThat(resultado.instagram()).contains(URI.create("https://www.instagram.com/padariaaurora"));
        assertThat(requests).isEmpty();
    }

    @Test
    void deveConsultarBraveQuandoSiteOficialNaoConfirmaNada() {
        List<GooglePesquisaWebRequest> requests = new ArrayList<>();
        GooglePesquisaGateway client = request -> {
            requests.add(request);
            return resposta(request, List.of());
        };
        var leitor = leitorQueDevolve("<html><body>Bem-vindo</body></html>");

        var resultado = new PesquisaWebInternaService(client,
            new ClassificadorUrlService(new UrlCandidatoCanonicalizer()), leitor)
            .pesquisar(leadAuroraComSite("https://padariaaurora.example/"));

        assertThat(resultado.instagram()).isEmpty();
        assertThat(resultado.siteProprio()).isEmpty();
        assertThat(requests).extracting(GooglePesquisaWebRequest::tipo)
            .containsExactly(TipoPesquisaWeb.INSTAGRAM, TipoPesquisaWeb.SITE_PROPRIO, TipoPesquisaWeb.INSTAGRAM);
    }

    @Test
    void deveDescartarWebsiteDeRedeSocialAntesDeAbrirOuConsultarComoSite() {
        AtomicInteger aberturas = new AtomicInteger();
        var leitor = new LeitorPaginaCandidata(
            (uri, timeout, max) -> {
                aberturas.incrementAndGet();
                return new LeitorPaginaCandidata.Resposta(200, "text/html", "x".getBytes());
            }, uri -> true, 1_000, 65_536);
        List<GooglePesquisaWebRequest> requests = new ArrayList<>();
        GooglePesquisaGateway client = request -> {
            requests.add(request);
            return resposta(request, List.of());
        };

        new PesquisaWebInternaService(client,
            new ClassificadorUrlService(new UrlCandidatoCanonicalizer()), leitor)
            .pesquisar(leadAuroraComSite("https://www.instagram.com/padariaaurora"));

        assertThat(aberturas).hasValue(0);
        assertThat(requests).hasSize(3);
    }

    @Test
    void deveContarPaginaOficialNoTetoDeTresPaginas() {
        AtomicInteger aberturas = new AtomicInteger();
        String html = "<html><body>Sem evidência "
            + "<a href=\"https://instagram.com/aurora1\">1</a>"
            + "<a href=\"https://instagram.com/aurora2\">2</a>"
            + "<a href=\"https://instagram.com/aurora3\">3</a>"
            + "<a href=\"https://instagram.com/aurora4\">4</a></body></html>";
        var leitor = new LeitorPaginaCandidata(
            (uri, timeout, max) -> {
                aberturas.incrementAndGet();
                return new LeitorPaginaCandidata.Resposta(200, "text/html", html.getBytes());
            }, uri -> true, 1_000, 65_536);
        GooglePesquisaGateway client = request -> resposta(request, List.of());

        new PesquisaWebInternaService(client,
            new ClassificadorUrlService(new UrlCandidatoCanonicalizer()), leitor)
            .pesquisar(leadAuroraComSite("https://padariaaurora.example/"));

        assertThat(aberturas).hasValue(3);
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

    private PesquisaLeadDados leadAuroraComSite(String website) {
        return new PesquisaLeadDados("place-1", "Padaria Aurora", CategoriaNegocio.PADARIA,
            "Rua das Flores, 10, Campinas - SP", "Rua das Flores", "10", null,
            "Campinas", "SP", "5519999999999", null, null, website);
    }

    private LeitorPaginaCandidata leitorQueDevolve(String html) {
        return new LeitorPaginaCandidata(
            (uri, timeout, max) -> new LeitorPaginaCandidata.Resposta(200, "text/html",
                html.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            uri -> true,
            1_000,
            65_536
        );
    }
}
