package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Diagnóstico opt-in, somente leitura: valida a abertura real de uma página candidata. */
@EnabledIfSystemProperty(named = "pesquisaPaginaLive", matches = "true")
class LeitorPaginaCandidataLiveTest {

    @Test
    void deveLerTelefoneNaBioDoPerfil() {
        var leitor = new LeitorPaginaCandidata(10_000, 524_288);
        var texto = leitor.ler(URI.create("https://www.instagram.com/multishowcastelo/"));
        assertThat(texto).isPresent();
        assertThat(texto.orElseThrow()).contains("5528999146676");
    }
}
