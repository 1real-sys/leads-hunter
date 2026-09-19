package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Validação opt-in, somente leitura, da extração em uma página pública real. */
@EnabledIfSystemProperty(named = "siteOficialLive", matches = "true")
class SiteOficialLiveTest {

    @Test
    @Timeout(60)
    void deveExtrairPerfilDaPaginaRealUsadaNaAmostraDeSupermercadoMichel() {
        var leitor = new LeitorPaginaCandidata(10_000, 524_288);

        var pagina = leitor.lerPagina(URI.create("https://centraldecompras.com.br/lojas"));

        Assumptions.assumeTrue(pagina.isPresent(),
            "Página real indisponível no ambiente; diagnóstico não comprova extração positiva");
        var conteudo = pagina.orElseThrow();
        assertThat(conteudo.texto()).contains("Castelo");
        assertThat(conteudo.links())
            .contains(URI.create("https://www.instagram.com/centraldecomprasmichel"));
    }
}
