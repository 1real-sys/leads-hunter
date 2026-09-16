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

    @ParameterizedTest
    @ValueSource(strings = {"028 99900-1234", "028999001234", "028.99900.1234", "028-99900-1234"})
    void deveConfirmarHandleAbreviadoComTelefoneExatoEZeroDeTroncoSemParenteses(String telefone) {
        var result = classificador.classificar(lead(), List.of(resultado(
            "https://instagram.com/supermercado.oliv", "Supermercado Oliveira",
            "Rua Maria Ortiz, 621. Telefone: " + telefone)), List.of());
        assertThat(result.instagram()).contains(URI.create("https://www.instagram.com/supermercado.oliv"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"028 99900-1235", "1028999001234", "0289990012345", "028 seguidores 99900 fotos 1234 posts"})
    void zeroDeTroncoNaoDevePermitirTelefoneDiferenteOuFabricado(String telefone) {
        assertThat(classificador.classificar(lead(), List.of(resultado(
            "https://instagram.com/supermercado.oliv", "Supermercado Oliveira",
            "Rua Maria Ortiz, 621. Telefone: " + telefone)), List.of()).instagram()).isEmpty();
    }

    @Test
    void devePreservarVetoDeDddDivergenteTambemComZeroDeTroncoSemParenteses() {
        assertThat(classificador.classificar(lead(), List.of(resultado(
            "https://instagram.com/supermercadooliveira", "Supermercado Oliveira",
            "Castelo - ES. Telefone: 041 99900-1234")), List.of()).instagram()).isEmpty();
    }

    @Test
    void deveDistinguirUsernameAbreviadoDeConflitoRealNoEnderecoDoHortifruti() {
        var lead = new PesquisaLeadDados("fixture-hortifruti", "Hortifruti Castelo", CategoriaNegocio.MERCADO,
            "Av. Nossa Sra. da Penha, 557 - São Miguel, Castelo - ES", "Avenida Nossa Senhora da Penha", "557",
            "São Miguel", "Castelo", "ES", "5528999353480", null, null);
        String perfil = "https://www.instagram.com/hortfrutcastelo";
        String titulo = "HORTIFRUTI CASTELO (@hortfrutcastelo)";

        // Nome exato, handle relacionado, mesma avenida/município e número vizinho confirmam o perfil.
        assertThat(classificador.classificar(lead, List.of(resultado(perfil, titulo,
            "028 3542-2436. Av. Nossa Senhora da Penha, 559 - Castelo | ES")), List.of()).instagram())
            .contains(URI.create(perfil));
        assertThat(classificador.classificar(lead, List.of(
            resultado(perfil, titulo, "028 3542-2436. Av. Nossa Senhora da Penha, 559 - Castelo | ES"),
            resultado("https://instagram.com/hortifrutidocastelo", "HORTIFRUTI DO CASTELO",
                "Frutas, verduras e temperos. Venha nos conhecer! SSA, Bahia"),
            resultado("https://instagram.com/hortifruti_lisboa_castelo", "Castelo Maçã",
                "O melhor da natureza pra sua mesa")
        ), List.of()).instagram()).contains(URI.create(perfil));
        // Com identidade corroborada, a grafia abreviada é aceita sem alias ou distância aproximada de nomes.
        assertThat(classificador.classificar(lead, List.of(resultado(perfil, titulo,
            "028 99935-3480. Av. Nossa Senhora da Penha, 557 - Castelo | ES")), List.of()).instagram())
            .contains(URI.create(perfil));
        // O telefone exato e a mesma avenida/município resolvem a divergência limitada ao número.
        assertThat(classificador.classificar(lead, List.of(resultado(perfil, titulo,
            "028 99935-3480. Av. Nossa Senhora da Penha, 559 - Castelo | ES")), List.of()).instagram())
            .contains(URI.create(perfil));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Av. Nossa Senhora da Penha, 560 - Castelo | ES",
        "Av. Nossa Senhora da Penha, 559 - Vitória | ES",
        "Rua Nossa Senhora da Penha, 559 - Castelo | ES"
    })
    void numeroProximoSemConjuntoCompletoDeIdentidadeNaoDeveConfirmarInstagram(String resumo) {
        var lead = new PesquisaLeadDados("fixture-hortifruti", "Hortifruti Castelo", CategoriaNegocio.MERCADO,
            "Av. Nossa Sra. da Penha, 557 - São Miguel, Castelo - ES", "Avenida Nossa Senhora da Penha", "557",
            "São Miguel", "Castelo", "ES", "5528999353480", null, null);
        assertThat(classificador.classificar(lead, List.of(resultado(
            "https://instagram.com/hortfrutcastelo", "Hortifruti Castelo", resumo)), List.of()).instagram()).isEmpty();
    }

    @Test
    void numeroProximoExigeNomeExatoEHandleFortementeRelacionado() {
        var lead = new PesquisaLeadDados("fixture-hortifruti", "Hortifruti Castelo", CategoriaNegocio.MERCADO,
            "Av. Nossa Sra. da Penha, 557 - São Miguel, Castelo - ES", "Avenida Nossa Senhora da Penha", "557",
            "São Miguel", "Castelo", "ES", "5528999353480", null, null);
        String resumo = "Av. Nossa Senhora da Penha, 559 - Castelo | ES";
        assertThat(classificador.classificar(lead, List.of(resultado(
            "https://instagram.com/castelo", "Hortifruti", resumo)), List.of()).instagram()).isEmpty();
    }

    @Test
    void deveRevalidarNumeroDivergenteComTelefoneExatoSemApagarEvidenciaAnterior() {
        var anterior = resultado("https://instagram.com/supermercado.oliv", "Supermercado Oliveira",
            "Rua Maria Ortiz, 622 - Castelo - ES. Telefone: 028 3542-0000");
        var confirmado = resultado("https://instagram.com/supermercado.oliv/?hl=pt", "Supermercado Oliveira",
            "Rua Maria Ortiz, 622 - Castelo - ES. Telefone: 028 99900-1234");
        var url = URI.create("https://www.instagram.com/supermercado.oliv");
        assertThat(classificador.classificar(lead(), List.of(anterior), List.of()).instagram()).isEmpty();
        assertThat(classificador.perfisParaConfirmar(lead(), List.of(anterior))).containsExactly(url);
        assertThat(classificador.classificar(lead(), List.of(anterior, confirmado), List.of()).instagram()).contains(url);
        assertThat(classificador.classificar(lead(), List.of(confirmado, anterior), List.of()).instagram()).contains(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Rua Maria Ortiz, 622 - Castelo - ES. Telefone: 028 99900-1235",
        "Rua Maria Ortiz, 622. Telefone: 028 99900-1234",
        "Rua Maria Ortiz, 622 - Castelo - ES.\nTelefone: 028 99900-1234",
        "Rua Maria Ortiz, 622 - Castelo - ES. ... Telefone: 028 99900-1234",
        "Rua Maria Ortiz, 622 - Castelo - ES. ***** Telefone: 028 99900-1234",
        "Rua Maria Ortiz, 622 - Castelo - ES. CNPJ 12.345.678/0001-90"
    })
    void numeroDivergenteExigeTelefoneExatoEMesmoLogradouroMunicipioNoMesmoTrecho(String resumo) {
        assertThat(classificador.classificar(lead(), List.of(resultado(
            "https://instagram.com/supermercado.oliv", "Supermercado Oliveira", resumo)), List.of()).instagram()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Rua da Lua, 622 - Castelo - ES", "Curitiba - PR", "CNPJ 98.765.432/0001-10",
        "Telefone: 041 99900-1234"})
    void confirmacaoDeNumeroNaoDeveApagarConflitosDefinitivosDoPerfil(String conflito) {
        var confirmado = resultado("https://instagram.com/supermercado.oliv", "Supermercado Oliveira",
            "Rua Maria Ortiz, 622 - Castelo - ES. Telefone: 028 99900-1234");
        var conflitante = resultado("https://instagram.com/supermercado.oliv", "Supermercado Oliveira", conflito);
        assertThat(classificador.classificar(lead(), List.of(confirmado, conflitante), List.of()).instagram()).isEmpty();
        assertThat(classificador.classificar(lead(), List.of(conflitante, confirmado), List.of()).instagram()).isEmpty();
        assertThat(classificador.perfisParaConfirmar(lead(), List.of(confirmado, conflitante))).isEmpty();
    }

    @Test
    void revalidacaoDoInstagramNaoDeveFlexibilizarNumeroDeSiteProprio() {
        assertThat(classificador.classificar(lead(), List.of(), List.of(resultado(
            "https://supermercadooliveira.example", "Supermercado Oliveira",
            "Rua Maria Ortiz, 622 - Castelo - ES. Telefone: 028 99900-1234"))).siteProprio()).isEmpty();
    }

    @Test
    void paginaComNumeroDivergenteDeveResolverEReavaliarTodosOsResultados() {
        var consultas = new java.util.ArrayList<GooglePesquisaWebRequest>();
        GooglePesquisaGateway client = request -> {
            consultas.add(request);
            return new GooglePesquisaWebResponse(request.googlePlaceId(), request.tipo(),
                BravePesquisaApiClient.montarConsulta(request), List.of(resultado(
                    "https://instagram.com/supermercado.oliv", "Supermercado Oliveira",
                    "Rua Maria Ortiz, 622 - Castelo - ES. Telefone: 028 3542-0000")));
        };
        var leitor = new LeitorPaginaCandidata(
            (uri, timeout, maxBytes) -> new LeitorPaginaCandidata.Resposta(200, "text/html",
                "Rua Maria Ortiz, 621 - Castelo - ES. Telefone: 028 99900-1234"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            uri -> true, 1_000, 65_536);
        assertThat(new PesquisaWebInternaService(client, classificador, leitor).pesquisar(lead()).instagram())
            .contains(URI.create("https://www.instagram.com/supermercado.oliv"));
        assertThat(consultas).hasSize(3);
    }

    @Test
    void candidatosComMesmoTelefoneEEnderecoParcialDevemPreservarAmbiguidade() {
        String resumo = "Rua Maria Ortiz, 622 - Castelo - ES. Telefone: 028 99900-1234";
        assertThat(classificador.classificar(lead(), List.of(
            resultado("https://instagram.com/supermercado.oliv", "Supermercado Oliveira", resumo),
            resultado("https://instagram.com/supermercado.olve", "Supermercado Oliveira", resumo)
        ), List.of()).instagram()).isEmpty();
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
