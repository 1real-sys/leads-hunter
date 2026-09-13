package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FormatadorObservacoesPesquisaTest {

    private final FormatadorObservacoesPesquisa formatador =
        new FormatadorObservacoesPesquisa(new UrlCandidatoCanonicalizer());

    @Test
    void deveFormatarInstagramESiteNaOrdemComLinhaVazia() {
        String observacoes = formatador.atualizar(null, resultado(
            "https://www.instagram.com/padaria.central",
            "https://padariacentral.com.br/"
        ));

        assertThat(observacoes).isEqualTo("""
            --- Pesquisa inteligente ---
            Instagram:
            https://www.instagram.com/padaria.central

            Site próprio:
            https://padariacentral.com.br/
            --- Fim da pesquisa inteligente ---""");
        assertThat(formatador.possuiInstagramESiteValidos(observacoes)).isTrue();
    }

    @Test
    void deveFormatarSomenteInstagramOuSomenteSiteSemRotpleadosVazios() {
        String instagram = formatador.atualizar(null, resultado(
            "https://www.instagram.com/padaria.central", null
        ));
        String site = formatador.atualizar(null, resultado(
            null, "https://padariacentral.com.br/"
        ));

        assertThat(instagram).contains("Instagram:\nhttps://www.instagram.com/padaria.central")
            .doesNotContain("Site próprio:");
        assertThat(site).contains("Site próprio:\nhttps://padariacentral.com.br/")
            .doesNotContain("Instagram:");
    }

    @Test
    void deveRegistrarFraseExataQuandoNaoEncontrarInformacoes() {
        String observacoes = formatador.atualizar(null, resultado(null, null));

        assertThat(observacoes).isEqualTo("""
            --- Pesquisa inteligente ---
            pesquisa inteligente não encontrou mais informações
            --- Fim da pesquisa inteligente ---""");
    }

    @Test
    void devePreservarTextoManualByteAByteESubstituirSemDuplicarBloco() {
        String anterior = "Anotação antes\r\n\r\n" + bloco(
            "Instagram:\r\nhttps://www.instagram.com/antigo"
        ) + "\r\nTexto depois\r\n" + bloco(
            "pesquisa inteligente não encontrou mais informações"
        ) + "\r\nFim manual";

        String atualizado = formatador.atualizar(anterior, resultado(
            "https://www.instagram.com/novo", "https://novo.example/"
        ));

        assertThat(atualizado).startsWith("Anotação antes\r\n\r\n")
            .endsWith("\r\nTexto depois\r\n\r\nFim manual")
            .contains("https://www.instagram.com/novo", "https://novo.example/")
            .doesNotContain("https://www.instagram.com/antigo");
        assertThat(ocorrencias(atualizado, FormatadorObservacoesPesquisa.INICIO_BLOCO)).isOne();
        assertThat(ocorrencias(atualizado, FormatadorObservacoesPesquisa.FIM_BLOCO)).isOne();
    }

    @Test
    void deveAcrescentarBlocoAposLinhaVaziaSemAlterarObservacaoManual() {
        String manual = "Cliente pediu retorno sexta-feira.";

        String atualizado = formatador.atualizar(manual, resultado(null, null));

        assertThat(atualizado).startsWith(manual + "\n\n")
            .contains(FormatadorObservacoesPesquisa.SEM_INFORMACOES);
    }

    @Test
    void deveManterLinkValidoAnteriorAoCompletarResultadoParcial() {
        String anterior = bloco("Instagram:\nhttps://www.instagram.com/padaria.central");

        String atualizado = formatador.atualizar(
            anterior,
            resultado(null, "https://padariacentral.com.br/")
        );

        assertThat(atualizado)
            .contains("https://www.instagram.com/padaria.central")
            .contains("https://padariacentral.com.br/");
        assertThat(formatador.possuiInstagramESiteValidos(atualizado)).isTrue();
    }

    @Test
    void naoDeveConsiderarUrlsInvalidasComoBlocoCompleto() {
        String observacoes = bloco("""
            Instagram:
            https://www.instagram.com/reel/video

            Site próprio:
            http://127.0.0.1/admin""");

        assertThat(formatador.possuiInstagramESiteValidos(observacoes)).isFalse();
        assertThat(formatador.extrairLinks(observacoes).instagram()).isEmpty();
        assertThat(formatador.extrairLinks(observacoes).siteProprio()).isEmpty();
    }

    @Test
    void naoDevePersistirUrlsInvalidasRecebidasDoFluxoInterno() {
        String observacoes = formatador.atualizar(null, resultado(
            "https://www.instagram.com/reel/video",
            "http://127.0.0.1/admin"
        ));

        assertThat(observacoes)
            .contains(FormatadorObservacoesPesquisa.SEM_INFORMACOES)
            .doesNotContain("127.0.0.1", "instagram.com");
    }

    private PesquisaInformacoesWebResultado resultado(String instagram, String site) {
        return new PesquisaInformacoesWebResultado(uri(instagram), uri(site));
    }

    private Optional<URI> uri(String valor) {
        return valor == null ? Optional.empty() : Optional.of(URI.create(valor));
    }

    private String bloco(String conteudo) {
        return FormatadorObservacoesPesquisa.INICIO_BLOCO + "\n" + conteudo + "\n"
            + FormatadorObservacoesPesquisa.FIM_BLOCO;
    }

    private int ocorrencias(String texto, String trecho) {
        return (texto.length() - texto.replace(trecho, "").length()) / trecho.length();
    }
}
