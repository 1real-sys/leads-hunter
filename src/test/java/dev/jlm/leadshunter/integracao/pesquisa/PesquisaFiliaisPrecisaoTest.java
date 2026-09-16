package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

class PesquisaFiliaisPrecisaoTest {

    private static final URI SITE_PETZ = URI.create("https://www.petz.com.br/loja/petz-vila-velha");
    private final ClassificadorUrlService classificador = new ClassificadorUrlService(new UrlCandidatoCanonicalizer());

    @Test
    void deveEncontrarFilialNoReplayRealDoBraveEPreservarPaginaNasObservacoes() throws Exception {
        // Captura somente de leitura de 13/09/2026; nenhuma rede ou banco neste teste.
        try (var entrada = getClass().getResourceAsStream("/pesquisa/brave-petz-vila-velha.json")) {
            var amostra = new ObjectMapper().readValue(entrada, Amostra.class);
            List<GooglePesquisaWebRequest> consultas = new ArrayList<>();
            GooglePesquisaGateway replay = request -> {
                consultas.add(request);
                return amostra.respostas().stream()
                    .filter(r -> r.tipo() == request.tipo()
                        && r.consulta().equals(BravePesquisaApiClient.montarConsulta(request)))
                    .findFirst().orElseThrow();
            };

            var resultado = new PesquisaWebInternaService(replay, classificador).pesquisar(amostra.lead());

            assertThat(resultado.siteProprio()).contains(SITE_PETZ);
            assertThat(resultado.instagram()).isEmpty();
            assertThat(consultas).hasSize(3);
            var formatador = new FormatadorObservacoesPesquisa(new UrlCandidatoCanonicalizer());
            String manual = "Retornar na sexta-feira.\r\n";
            String anterior = formatador.atualizar(manual,
                new PesquisaInformacoesWebResultado(java.util.Optional.empty(), java.util.Optional.empty()));
            String atualizado = formatador.atualizar(anterior, resultado);
            assertThat(atualizado).startsWith(manual).contains("Site próprio:\n" + SITE_PETZ)
                .doesNotContain(FormatadorObservacoesPesquisa.SEM_INFORMACOES);
            assertThat(formatador.extrairLinks(atualizado).siteProprio()).contains(SITE_PETZ);
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Rod. do Sol | Rodovia do Sol, 256",
        "Rodovia do Sol | Rod. do Sol, 256",
        "Av. Brasil | Avenida Brasil, nº 256",
        "Avenida Brasil | Av. Brasil, 256",
        "R. São José | Rua Sao Jose, 256",
        "Tv. das Flores | Travessa das Flores, 256",
        "Estr. do Campo | Estrada do Campo, 256",
        "Al. dos Anjos | Alameda dos Anjos, 256",
        "Pç. da Sé | Praça da Se, 256"
    })
    void deveConfirmarEnderecoAbreviadoComNumeroJuntoAoLogradouro(String logradouro, String resumo) {
        var lead = lead("Empório Aurora", logradouro, "256", null);
        assertThat(classificar(lead, candidato("https://emporioaurora.example/unidade",
            "Empório Aurora", resumo)).siteProprio()).contains(URI.create("https://emporioaurora.example/unidade"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Rodovia do Sol, 257. Código promocional 256",
        "Rodovia do Sol. Temos 256 produtos",
        "Rodovia do Sol. 256 seguidores",
        "Rodovia do Sol · 256 produtos",
        "Rodovia do Sol\n256 seguidores",
        "Rodovia da Lua, 256",
        "Rua do Sol, 256",
        "Rodovia do Sol, 2560",
        "Rodovia do Sol, 256-A",
        "Rodovia do Sol, 256-R"
    })
    void naoDeveConfirmarEnderecoPorNumeroSoltoOuLogradouroDiferente(String resumo) {
        assertThat(classificar(lead("Empório Aurora", "Rod. do Sol", "256", null), candidato(
            "https://emporioaurora.example", "Empório Aurora", resumo)).siteProprio()).isEmpty();
    }

    @Test
    void deveUsarEnderecoFormatadoQuandoComponentesEstruturadosEstiveremAusentes() {
        var lead = new PesquisaLeadDados("fixture", "Petz Vila Velha", CategoriaNegocio.OUTROS,
            "Rod. do Sol, 256 - Itapuã, Vila Velha - ES", null, null, "Itapuã", "Vila Velha", "ES",
            null, null, null);
        assertThat(classificar(lead, candidato(SITE_PETZ.toString(), "Petz Vila Velha",
            "Rodovia do Sol, 256")).siteProprio()).contains(SITE_PETZ);
    }

    @Test
    void paginaDeOutrasFiliaisNaoDeveInvalidarPaginaLocalNoMesmoDominio() {
        var lead = lead("Petz Vila Velha", "Rod. do Sol", "256", "Vila Velha");
        var local = candidato(SITE_PETZ.toString(), "Petz Vila Velha", "Rodovia do Sol, 256 - Vila Velha/ES");
        var outra = candidato("https://www.petz.com.br/loja/petz-vitoria", "Petz Vitória", "Vitória - ES");
        var lista = candidato("https://www.petz.com.br/nossas-lojas", "Nossas Lojas",
            "Rodovia do Sol, 256 - Vila Velha/ES. Outras unidades em Vitória - ES e Curitiba - PR");
        assertThat(classificar(lead, local, outra, lista).siteProprio()).contains(SITE_PETZ);
        assertThat(classificar(lead, lista, outra, local).siteProprio()).contains(SITE_PETZ);
    }

    @Test
    void duplicatasDaMesmaPaginaNaoDevemOcultarConflitos() {
        var lead = lead("Petz Vila Velha", "Rod. do Sol", "256", "Vila Velha");
        assertThat(classificar(lead,
            candidato(SITE_PETZ + "?utm_source=brave", "Petz Vila Velha", "Rodovia do Sol, 256 - Vila Velha/ES"),
            candidato("https://petz.com.br/loja/petz-vila-velha/", "Petz Vila Velha", "Curitiba - PR")
        ).siteProprio()).isEmpty();
    }

    @Test
    void deveRecusarNumeroDeOutraUnidadeMesmoComNomeEMunicipioIguais() {
        assertThat(classificar(lead("Petz Vila Velha", "Rod. do Sol", "256", "Vila Velha"),
            candidato(SITE_PETZ.toString(), "Petz Vila Velha", "Rodovia do Sol, 257 - Vila Velha/ES")
        ).siteProprio()).isEmpty();
    }

    @Test
    void deveRecusarLogradouroDeOutraUnidadeMesmoComNomeEMunicipioIguais() {
        assertThat(classificar(lead("Petz Vila Velha", "Rod. do Sol", "256", "Vila Velha"),
            candidato(SITE_PETZ.toString(), "Petz Vila Velha", "Rua da Lua, 256 - Vila Velha/ES")
        ).siteProprio()).isEmpty();
    }

    @Test
    void dominioDaMarcaNaoDeveCompensarParteAusenteDoNomeDaFilial() {
        assertThat(classificar(lead("Empório Aurora Vila Velha", "Rod. do Sol", "256", "Vila Velha"),
            candidato("https://emporioaurora.example/", "Empório Aurora Vila", "Rodovia do Sol, 256")
        ).siteProprio()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://guialocal.example/petz-vila-velha",
        "https://petz.guialocal.example/loja/petz-vila-velha",
        "https://petshopem.com.br/petz-vila-velha",
        "https://areiawicat.com.br/onde-comprar/petz-vila-velha"
    })
    void nomeDaFilialNoCaminhoOuSubdominioNaoDeveLegitimarDominioAlheio(String url) {
        assertThat(classificar(lead("Petz Vila Velha", "Rod. do Sol", "256", "Vila Velha"),
            candidato(url, "Petz Vila Velha", "Rodovia do Sol, 256 - Vila Velha/ES")
        ).siteProprio()).isEmpty();
    }

    @Test
    void nomeDaFilialSemConfirmacaoIndependenteNaoBasta() {
        assertThat(classificar(lead("Petz Vila Velha", "Rod. do Sol", "256", "Vila Velha"),
            candidato(SITE_PETZ.toString(), "Petz Vila Velha", "Confira nossas ofertas")
        ).siteProprio()).isEmpty();
    }

    @Test
    void naoDeveRemoverPartesIsoladasDoMunicipioDoNomeComercial() {
        assertThat(classificar(lead("Aurora Velha", "Rod. do Sol", "256", "Vila Velha"),
            candidato("https://aurora.example/", "Aurora Velha", "Vila Velha/ES")
        ).siteProprio()).isEmpty();
    }

    @Test
    void nomeGenericoRestanteNaoDeveLegitimarDominioDeCategoria() {
        assertThat(classificar(lead("Mercado Castelo", "Rod. do Sol", "256", "Castelo"),
            candidato("https://mercado.example/", "Mercado Castelo", "Castelo/ES")
        ).siteProprio()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"(027) 3022-5308", "(27) 3022-5308", "+55 (27) 3022-5308"})
    void deveReconhecerTelefoneComZeroDeTroncoNoDdd(String telefone) {
        var lead = new PesquisaLeadDados("fixture", "Petz Vila Velha", CategoriaNegocio.OUTROS,
            null, null, null, null, "Vila Velha", "ES", "552730225308", null, null);
        assertThat(classificar(lead, candidato(SITE_PETZ.toString(), "Petz Vila Velha",
            "Telefone da loja " + telefone)).siteProprio()).contains(SITE_PETZ);
    }

    private PesquisaInformacoesWebResultado classificar(PesquisaLeadDados lead, GoogleResultadoWeb... candidatos) {
        return classificador.classificar(lead, List.of(candidatos), List.of(candidatos));
    }

    private PesquisaLeadDados lead(String nome, String logradouro, String numero, String municipio) {
        return new PesquisaLeadDados("fixture", nome, CategoriaNegocio.OUTROS, null, logradouro, numero,
            null, municipio, "ES", null, null, null);
    }

    private GoogleResultadoWeb candidato(String url, String titulo, String resumo) {
        return new GoogleResultadoWeb(URI.create(url), titulo, resumo);
    }

    record Amostra(PesquisaLeadDados lead, List<GooglePesquisaWebResponse> respostas) {}
}
