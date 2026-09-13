package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ClassificadorUrlPrecisaoTest {
    private final ClassificadorUrlService classificador = new ClassificadorUrlService(new UrlCandidatoCanonicalizer());

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "https://instagram.com/supermercadooliveira | Supermercado Oliveira | Sem localização",
        "https://redesuperoliveira.com.br | Supermercado Oliveira | Confira nossas ofertas",
        "https://instagram.com/supermercadooliveira | Supermercado Oliveira | Centro - Curitiba - PR",
        "https://instagram.com/supermercadooliveira | Supermercado Oliveira | Castelo - PR",
        "https://instagram.com/supermercadooliveira | Supermercado Oliveira | Conceição de Castelo - ES",
        "https://instagram.com/supermercadooliveira | Supermercado Oliveira | Monte Castelo - ES",
        "https://instagram.com/supermercadooliveira | Supermercado Oliveira | Vitória - ES",
        "https://instagram.com/supermercadooliveira | Supermercado Oliveira | Centro",
        "https://instagram.com/supermercadooliveira | Supermercado Oliveira | Bairro Castelo",
        "https://instagram.com/supermercadooliveira | Supermercado Oliveira | Rua Castelo",
        "https://instagram.com/supermercadooliveira | Supermercado Oliveira | Castelo - ES. Telefone 41 99649-0127",
        "https://instagram.com/guialocal | Supermercado Oliveira | Castelo - ES",
        "https://instagram.com/supermercadooliveira?q=Castelo | Supermercado Oliveira | Ofertas",
        "https://supermercadooliveira.guialocal.example | Supermercado Oliveira | Castelo - ES",
        "https://guialocal.example/supermercado-oliveira-castelo | Supermercado Oliveira | Castelo - ES",
        "https://supermercado.net.br | Supermercado Oliveira | Castelo - ES",
        "https://redesuperoliveira.com.br | Supermercado Oliveira | Castelo - ES. Unidade em Curitiba - PR",
        "https://instagram.com/supermercadooliveira | Supermercado Oliveira | Seguidores: 55 28 publicações: 99900 itens: 1234",
        "https://redesuperoliveira.com.br | Supermercado Oliveira | CNPJ 98.765.432/0001-10 Castelo - ES"
    })
    void deveRecusarAssociacoesSemIdentidadeOuComConflito(String url, String titulo, String resumo) {
        var candidatos = List.of(resultado(url, titulo, resumo));
        var result = classificador.classificar(lead(), candidatos, candidatos);
        assertThat(result.instagram()).isEmpty();
        assertThat(result.siteProprio()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Castelo - ES", "Castelo, ES", "Castelo/ES", "Supermercado em Castelo - ES",
        "(28) 99900-1234", "+55 (28) 99900-1234", "5528999001234", "CNPJ 12.345.678/0001-90",
        "Castelo - ES. Ofertas, se precisar fale conosco."})
    void deveAceitarEvidenciaLocalOuIdentificadorExato(String resumo) {
        var result = classificador.classificar(lead(), List.of(resultado(
            "https://instagram.com/supermercadooliveira", "Supermercado Oliveira", resumo)), List.of());
        assertThat(result.instagram()).contains(URI.create("https://www.instagram.com/supermercadooliveira"));
    }

    @Test
    void municipioContidoNoNomeNaoDeveSerEvidenciaDeLocalizacao() {
        var lead = new PesquisaLeadDados("place-teste", "Mercado Castelo", CategoriaNegocio.MERCADO,
            null, null, null, null, "Castelo", "ES", null, null, null);
        var result = classificador.classificar(lead, List.of(resultado(
            "https://instagram.com/mercadocastelo", "Mercado Castelo", "Mercado Castelo • ofertas")), List.of());
        assertThat(result.instagram()).isEmpty();
    }

    @Test
    void duplicataConfirmadaNaoDeveOcultarConflitoDoMesmoDestino() {
        var result = classificador.classificar(lead(), List.of(
            resultado("https://instagram.com/supermercadooliveira", "Supermercado Oliveira", "Castelo - ES"),
            resultado("https://instagram.com/supermercadooliveira/?hl=pt", "Supermercado Oliveira", "Curitiba - PR")
        ), List.of());
        assertThat(result.instagram()).isEmpty();
    }

    @Test
    void deveAceitarHandleAbreviadoApenasComIdentificadorExternoForte() {
        var result = classificador.classificar(lead(), List.of(resultado(
            "https://instagram.com/supermercado.oliv", "Supermercado Oliveira", "Fone: (28) 99900-1234")), List.of());
        assertThat(result.instagram()).contains(URI.create("https://www.instagram.com/supermercado.oliv"));
    }

    @Test
    void nomeGenericoNaoDevePermitirDominioDeDiretorioDesconhecido() {
        var lead = new PesquisaLeadDados("place-teste", "Padaria Central", CategoriaNegocio.PADARIA,
            null, null, null, null, "Castelo", "ES", null, null, null);
        assertThat(classificador.classificar(lead, List.of(), List.of(resultado(
            "https://guialocal.example/padaria-central", "Padaria Central", "Castelo - ES"))).siteProprio()).isEmpty();
    }

    @Test
    void naoDeveSomarDigitosDeCnpjEspalhadosPeloTexto() {
        assertThat(classificador.classificar(lead(), List.of(resultado(
            "https://instagram.com/supermercadooliveira", "Supermercado Oliveira",
            "12 anos, 345 ofertas, 678 clientes, 0001 filial, 90 produtos")), List.of()).instagram()).isEmpty();
    }

    private PesquisaLeadDados lead() {
        return new PesquisaLeadDados("place-teste", "Supermercado Oliveira", CategoriaNegocio.MERCADO,
            "Rua Maria Ortiz, 621 - Castelo - ES", "Rua Maria Ortiz", "621", "Centro", "Castelo", "ES",
            "5528999001234", "12345678000190", null);
    }

    private GoogleResultadoWeb resultado(String url, String titulo, String resumo) {
        return new GoogleResultadoWeb(URI.create(url), titulo, resumo);
    }
}
