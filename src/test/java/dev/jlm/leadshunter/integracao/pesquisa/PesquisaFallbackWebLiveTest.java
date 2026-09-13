package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Verificação manual opt-in: no máximo duas consultas por fonte, sem retry nem acesso aos candidatos. */
@EnabledIfSystemProperty(named = "pesquisaFallbackLive", matches = "true")
class PesquisaFallbackWebLiveTest {
    @Test
    void deveCapturarAoMenosUmLinkComEvidenciaPublica() {
        try (var navigator = new PlaywrightGooglePesquisaNavigator(20_000, 2_097_152, 1, 15_000)) {
            var google = new GooglePesquisaWebClient(navigator, new GooglePesquisaHtmlParser(), 10, 3_600_000);
            var fallback = new PesquisaWebFallbackClient(google, navigator, new PesquisaAlternativaHtmlParser());
            var service = new PesquisaWebInternaService(fallback,
                new ClassificadorUrlService(new UrlCandidatoCanonicalizer()));
            var lead = new PesquisaLeadDados("smoke-publico-sem-places", "Padaria Real", CategoriaNegocio.PADARIA,
                null, null, null, null, "Sorocaba", "SP", null, null, null);
            var resultado = service.pesquisar(lead);
            System.out.println("PESQUISA_FALLBACK_REAL instagram=" + resultado.instagram().map(Object::toString).orElse("ausente")
                + "; site=" + resultado.siteProprio().map(Object::toString).orElse("ausente"));
            assertThat(resultado.instagram().isPresent() || resultado.siteProprio().isPresent())
                .as("ao menos um link associado ao estabelecimento, não apenas tratamento de bloqueio")
                .isTrue();
        }
    }
}
