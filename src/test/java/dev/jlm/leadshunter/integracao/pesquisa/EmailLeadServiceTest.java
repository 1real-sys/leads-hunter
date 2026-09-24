package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailLeadServiceTest {
    private static final URI SITE = URI.create("https://padaria.example.com.br/");
    private static final URI CONTATO = URI.create("https://padaria.example.com.br/contato");

    @Test
    void confirmaTelefoneDaHomeEExtraiMailtoDoDominioProprio() {
        var chamadas = new ArrayList<URI>();
        var service = service(Map.of(SITE, html("<p>(27) 3333-4444</p>"
            + "<a href='mailto:Contato@padaria.example.com.br?subject=oi'>Fale conosco</a>")), chamadas);

        var resultado = service.extrair(lead());

        assertThat(resultado.estado()).isEqualTo(EmailLeadService.Estado.ENCONTRADO);
        assertThat(resultado.email()).isEqualTo("contato@padaria.example.com.br");
        assertThat(resultado.origemHost()).isEqualTo("padaria.example.com.br");
        assertThat(chamadas).containsExactly(SITE);
    }

    @Test
    void combinaHomeEContatoMasRecusaDominioExternoEOutroHost() {
        var chamadas = new ArrayList<URI>();
        var service = service(Map.of(
            SITE, html("<p>(27) 3333-4444</p>"
                + "<a href='/contato'>Contato</a><a href='https://outro.example/contato'>Contato externo</a>"),
            CONTATO, html("<a href='mailto:loja@gmail.com'>Gmail</a>"
                + "<a href='mailto:vendas@padaria.example.com.br'>Vendas</a>")
        ), chamadas);

        var resultado = service.extrair(lead());

        assertThat(resultado.estado()).isEqualTo(EmailLeadService.Estado.ENCONTRADO);
        assertThat(resultado.email()).isEqualTo("vendas@padaria.example.com.br");
        assertThat(resultado.descartouDominioExterno()).isTrue();
        assertThat(chamadas).containsExactly(SITE, CONTATO);
    }

    @Test
    void vetaConflitoEntrePaginasMesmoComEmailProprio() {
        var service = service(Map.of(
            SITE, html("<p>(27) 3333-4444</p><a href='/contato'>Contato</a>"),
            CONTATO, html("<p>(41) 3333-4444</p><a href='mailto:contato@padaria.example.com.br'>E-mail</a>")),
            new ArrayList<>());

        assertThat(service.extrair(lead()).estado())
            .isEqualTo(EmailLeadService.Estado.SEM_EMAIL_ELEGIVEL);
    }

    @Test
    void dominioPaiFicaForaDaV1EEntraNoIndicadorDePerda() {
        var service = service(Map.of(SITE, html("<p>(27) 3333-4444</p>"
            + "<a href='mailto:contato@example.com.br'>E-mail</a>")), new ArrayList<>());

        var resultado = service.extrair(lead());
        assertThat(resultado.estado()).isEqualTo(EmailLeadService.Estado.SEM_EMAIL_ELEGIVEL);
        assertThat(resultado.descartouDominioExterno()).isTrue();
    }

    @Test
    void semEvidenciaOuComEnderecoConflitanteNaoGrava() {
        var chamadas = new ArrayList<URI>();
        var semProva = service(Map.of(SITE, html("<a href='mailto:contato@padaria.example.com.br'>Contato</a>")), chamadas);
        assertThat(semProva.extrair(lead()).estado())
            .isEqualTo(EmailLeadService.Estado.SEM_EMAIL_ELEGIVEL);

        var conflito = service(Map.of(SITE, html("<p>(27) 3333-4444</p>"
            + "<p>Rua Central, 999</p><a href='mailto:contato@padaria.example.com.br'>Contato</a>")),
            new ArrayList<>());
        assertThat(conflito.extrair(lead()).estado())
            .isEqualTo(EmailLeadService.Estado.SEM_EMAIL_ELEGIVEL);
    }

    @Test
    void conflitoNaHomeVetaSemAbrirContato() {
        var chamadas = new ArrayList<URI>();
        var service = service(Map.of(SITE, html("<p>(27) 3333-4444</p>"
            + "<p>Rua Central, 999</p><a href='/contato'>Contato</a>")), chamadas);

        assertThat(service.extrair(lead()).estado())
            .isEqualTo(EmailLeadService.Estado.SEM_EMAIL_ELEGIVEL);
        assertThat(chamadas).containsExactly(SITE);
    }

    @Test
    void falhaTecnicaNaoViraAusenciaENaoHaLeituraDeTerceiraPagina() {
        var chamadas = new ArrayList<URI>();
        var service = service(Map.of(SITE, html("<a href='/contato'>Contato</a>")), chamadas);
        assertThat(service.extrair(lead()).estado()).isEqualTo(EmailLeadService.Estado.FALHA);
        assertThat(chamadas).containsExactly(SITE, CONTATO);
    }

    @Test
    void semSiteNaoAbrePagina() {
        var chamadas = new ArrayList<URI>();
        var service = service(Map.of(), chamadas);
        var semSite = new PesquisaLeadDados("place-1", "Padaria Central", CategoriaNegocio.PADARIA,
            "Rua Central, 100", "Rua Central", "100", null, "Vitória", "ES",
            "552733334444", null, null, null);
        assertThat(service.extrair(semSite).estado()).isEqualTo(EmailLeadService.Estado.SEM_SITE);
        assertThat(chamadas).isEmpty();
    }

    private EmailLeadService service(Map<URI, LeitorPaginaCandidata.Resposta> respostas,
                                     List<URI> chamadas) {
        var canonicalizer = new UrlCandidatoCanonicalizer();
        var leitor = new LeitorPaginaCandidata((uri, timeout, max) -> {
            chamadas.add(uri);
            return respostas.get(uri);
        }, uri -> true, canonicalizer, 1_000, 65_536);
        return new EmailLeadService(leitor, canonicalizer, new ClassificadorUrlService(canonicalizer));
    }

    private LeitorPaginaCandidata.Resposta html(String conteudo) {
        return new LeitorPaginaCandidata.Resposta(200, "text/html",
            ("<html><body>" + conteudo + "</body></html>").getBytes(StandardCharsets.UTF_8));
    }

    private PesquisaLeadDados lead() {
        return new PesquisaLeadDados("place-1", "Padaria Central", CategoriaNegocio.PADARIA,
            "Rua Central, 100", "Rua Central", "100", null, "Vitória", "ES",
            "552733334444", null, null, SITE.toString());
    }
}
