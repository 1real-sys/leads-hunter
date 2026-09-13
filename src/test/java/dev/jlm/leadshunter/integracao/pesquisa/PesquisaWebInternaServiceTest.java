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
}
