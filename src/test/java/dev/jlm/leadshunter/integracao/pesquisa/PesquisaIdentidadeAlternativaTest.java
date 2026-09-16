package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

class PesquisaIdentidadeAlternativaTest {

    private static final String PERFIL = "https://www.instagram.com/centraldecomprasmichel";
    private final ClassificadorUrlService classificador = new ClassificadorUrlService(new UrlCandidatoCanonicalizer());

    @Test
    void replayRealDeveConfirmarMichelAposConsultaAdicionalEPreservarObservacoes() throws Exception {
        try (var entrada = getClass().getResourceAsStream("/pesquisa/brave-michel-confirmacao.json")) {
            var amostra = new ObjectMapper().readValue(entrada, Amostra.class);
            var iniciais = amostra.respostas().subList(0, 3).stream().flatMap(r -> r.resultados().stream()).toList();
            assertThat(classificador.classificar(amostra.lead(), iniciais, iniciais).instagram()).isEmpty();
            List<GooglePesquisaWebRequest> consultas = new ArrayList<>();
            GooglePesquisaGateway replay = request -> {
                consultas.add(request);
                return amostra.respostas().stream().filter(r -> r.tipo() == request.tipo()
                    && r.consulta().equals(BravePesquisaApiClient.montarConsulta(request))).findFirst().orElseThrow();
            };
            var leitor = new LeitorPaginaCandidata(
                (uri, timeout, maxBytes) -> {
                    String html = uri.toString().contains("centraldecomprasmichel")
                        ? "<meta name=\"description\" content=\"Telefone: (28) 3542-1440\">"
                        : "sem dados";
                    return new LeitorPaginaCandidata.Resposta(200, "text/html",
                        html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                },
                uri -> true, 1_000, 65_536);
            var resultado = new PesquisaWebInternaService(replay, classificador, leitor).pesquisar(amostra.lead());
            assertThat(resultado.instagram()).contains(URI.create(PERFIL));
            assertThat(resultado.siteProprio()).isEmpty();
            assertThat(consultas).hasSize(3);
            var formatador = new FormatadorObservacoesPesquisa(new UrlCandidatoCanonicalizer());
            String manual = "Contato comercial combinado.\r\n";
            String anterior = manual + FormatadorObservacoesPesquisa.INICIO_BLOCO + "\n"
                + FormatadorObservacoesPesquisa.SEM_INFORMACOES + "\n" + FormatadorObservacoesPesquisa.FIM_BLOCO;
            String atualizado = formatador.atualizar(anterior, resultado);
            assertThat(atualizado).startsWith(manual).contains("Instagram:\n" + PERFIL)
                .doesNotContain(FormatadorObservacoesPesquisa.SEM_INFORMACOES);
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Supermercado Michel | Central de Compras Michel | centraldecomprasmichel",
        "Drogaria Aurora | Farma Aurora | farmaaurora",
        "Padaria Girassol | Empório Girassol | emporiogirassol"
    })
    void deveAceitarNomeComercialAlternativoComMarcaCompartilhadaETelefoneExato(String nome, String titulo, String usuario) {
        var lead = lead(nome, "552835421440");
        String url = "https://www.instagram.com/" + usuario;
        assertThat(classificar(lead, candidato(url, titulo, "Fone: (28) 3542-1440")))
            .contains(URI.create(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Castelo - ES", "Fone: (28) 3542-1441", "Fone: (41) 3542-1440",
        "3542-1440", "28 seguidores 3542 fotos 1440 posts", "Ofertas da semana"
    })
    void nomeAlternativoNaoDeveSerAceitoSemIdentificadorExato(String resumo) {
        assertThat(classificar(lead(), perfil(resumo))).isEmpty();
    }

    @Test
    void telefoneNaoDeveLegitimarMarcaSemRelacaoComOLead() {
        assertThat(classificar(lead(), candidato("https://instagram.com/farmaaurora", "Farma Aurora",
            "Fone: (28) 3542-1440"))).isEmpty();
    }

    @Test
    void nomeAlternativoComTelefoneExatoNaoDeveOcultarConflitos() {
        assertThat(classificar(lead(), perfil("Fone: (28) 3542-1440. Curitiba - PR"))).isEmpty();
        assertThat(classificar(lead(), perfil("Fone: (28) 3542-1440"), perfil("Curitiba - PR"))).isEmpty();
    }

    @Test
    void deveConfirmarPerfilReferenciadoComEnderecoETelefoneNoMesmoTrecho() {
        String local = "Avenida Ministro Araripe, 288, Centro - Castelo - ES. (28) 3542-1440. Instagram: @centraldecomprasmichel";
        assertThat(classificar(lead(), perfil("Fotos e vídeos"), fonte(local))).contains(URI.create(PERFIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Instagram: centraldecomprasmichel", "Instagram: https://instagram.com/centraldecomprasmichel/",
        "https://www.instagram.com/centraldecomprasmichel/?hl=pt"})
    void deveReconhecerVariantesDeReferenciaExplicitaSemFabricarUrl(String referencia) {
        assertThat(classificar(lead(), perfil("Fotos e vídeos"), fonte(
            "Avenida Ministro Araripe, 288. (28) 3542-1440. " + referencia))).contains(URI.create(PERFIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1/lojas", "https://www.facebook.com/rede", "https://guiaja.net/michel"})
    void fonteBloqueadaNaoDeveConfirmarReferencia(String url) {
        assertThat(classificar(lead(), perfil("Fotos"), candidato(url, "Rede",
            "Avenida Ministro Araripe, 288. (28) 3542-1440. Instagram: @centraldecomprasmichel"))).isEmpty();
    }

    @Test
    void deveSepararUnidadesDaPaginaPreservandoAReferenciaDoBlocoCorreto() {
        String local = "Avenida Ministro Araripe, 288, Centro - Castelo - ES. (28) 3542-1440. Instagram: @centraldecomprasmichel";
        String outra = "Rua das Flores, 10, Curitiba - PR. (41) 3333-4444. Instagram: @outro";
        assertThat(classificar(lead(), perfil("Fotos e vídeos"), fonte(outra + "\n" + local + " ***** " + outra)))
            .contains(URI.create(PERFIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Avenida Ministro Araripe, 288. (28) 3542-1440. ***** Instagram: @centraldecomprasmichel",
        "Avenida Ministro Araripe, 288. (28) 3542-1440.\nInstagram: @centraldecomprasmichel",
        "Avenida Ministro Araripe, 288. (28) 3542-1440. ... Instagram: @centraldecomprasmichel",
        "Avenida Ministro Araripe, 288. (28) 3542-1440. … Instagram: @centraldecomprasmichel",
        "Avenida Ministro Araripe, 288. (28) 3542-1441. Instagram: @centraldecomprasmichel",
        "Avenida Ministro Araripe, 289. (28) 3542-1440. Instagram: @centraldecomprasmichel",
        "Avenida Ministro Araripe, 288. (28) 3542-1440. Instagram: @outro",
        "Avenida Ministro Araripe, 288. (28) 3542-1440. @centraldecomprasmichel",
        "Avenida Ministro Araripe, 288. (28) 3542-1440. Instagram: @centraldecomprasmichel Instagram: @outro",
        "Avenida Ministro Araripe, 288. (28) 3542-1440. Rua da Lua, 20 - Curitiba - PR. Instagram: @centraldecomprasmichel"
    })
    void naoDeveMisturarUnidadesOuConfirmarReferenciaSemVinculoCompleto(String resumo) {
        assertThat(classificar(lead(), perfil("Fotos e vídeos"), fonte(resumo))).isEmpty();
    }

    @Test
    void referenciaExternaNaoDeveApagarConflitoPublicoDoPerfil() {
        assertThat(classificar(lead(), perfil("Fone: (41) 99649-0127"), fonte(
            "Avenida Ministro Araripe, 288. (28) 3542-1440. Instagram: @centraldecomprasmichel"))).isEmpty();
    }

    @Test
    void telefoneConsultadoNaoDeveSerUsadoComoProva() {
        assertThat(classificar(lead(), perfil("Fotos e vídeos"), candidato(
            "https://rede.example/?telefone=2835421440", "Busca 2835421440", "Instagram: @centraldecomprasmichel")))
            .isEmpty();
    }

    @Test
    void deveConfirmarAliasComConsultaDirecionadaSemAlterarOLeadDaPesquisa() {
        List<GooglePesquisaWebRequest> consultas = new ArrayList<>();
        GooglePesquisaGateway client = request -> {
            consultas.add(request);
            return resposta(request, request.confirmacao() == null ? List.of(perfil("Fotos e vídeos"))
                : List.of(perfil("Fone: (28) 3542-1440")));
        };
        var resultado = new PesquisaWebInternaService(client, classificador).pesquisar(leadSemMunicipio());
        assertThat(resultado.instagram()).contains(URI.create(PERFIL));
        assertThat(consultas).hasSize(3).allSatisfy(r -> assertThat(r.nome()).isEqualTo("Supermercado Michel"));
        assertThat(BravePesquisaApiClient.montarConsulta(consultas.getLast()))
            .isEqualTo("centraldecomprasmichel 28 3542-1440");
    }

    @Test
    void deveLimitarConfirmacoesEDeduplicarPerfisSemEncadearNovasDescobertas() {
        List<GooglePesquisaWebRequest> consultas = new ArrayList<>();
        GooglePesquisaGateway client = request -> {
            consultas.add(request);
            return resposta(request, request.confirmacao() == null
                ? List.of(perfil("Fotos e vídeos"), candidato(PERFIL + "/?hl=pt", "Central de Compras Michel", "Fotos"),
                    candidato("https://instagram.com/centralmichel", "Central Michel", "Fotos"),
                    candidato("https://instagram.com/michelcompras", "Michel Compras", "Fotos"))
                : List.of(candidato("https://instagram.com/novomichel", "Novo Michel", "Fotos")));
        };
        assertThat(new PesquisaWebInternaService(client, classificador).pesquisar(leadSemMunicipio()).instagram()).isEmpty();
        assertThat(consultas).hasSize(3);
        assertThat(consultas.stream().filter(r -> r.confirmacao() != null).map(r -> r.confirmacao().usuario()))
            .doesNotHaveDuplicates().doesNotContain("novomichel");
    }

    @Test
    void duasConfirmacoesPlausiveisNaoDevemSerResolvidasPelaOrdemDasConsultas() {
        GooglePesquisaGateway client = request -> resposta(request, request.confirmacao() == null
            ? List.of(perfil("Fotos"), candidato("https://instagram.com/michelcompras", "Michel Compras", "Fotos"))
            : List.of(candidato("https://instagram.com/" + request.confirmacao().usuario(),
                "Central de Compras Michel", "(28) 3542-1440")));
        assertThat(new PesquisaWebInternaService(client, classificador).pesquisar(lead()).instagram()).isEmpty();
    }

    @Test
    void naoDeveConsultarPerfisSemRelacaoOuJaConflitantes() {
        List<GooglePesquisaWebRequest> consultas = new ArrayList<>();
        GooglePesquisaGateway client = request -> {
            consultas.add(request);
            return resposta(request, List.of(perfil("Curitiba - PR"),
                candidato("https://instagram.com/outro", "Outra marca", "Fotos")));
        };
        new PesquisaWebInternaService(client, classificador).pesquisar(lead());
        assertThat(consultas).hasSize(3);
    }

    @Test
    void falhaTecnicaDaConfirmacaoNaoDeveVirarAusenciaConclusiva() {
        GooglePesquisaGateway client = request -> {
            if (request.confirmacao() != null) throw new GooglePesquisaWebTimeoutException();
            return resposta(request, List.of(perfil("Fotos")));
        };
        assertThatThrownBy(() -> new PesquisaWebInternaService(client, classificador).pesquisar(leadSemMunicipio()))
            .isInstanceOf(GooglePesquisaWebTimeoutException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234", "283542144", "telefone ausente"})
    void naoDeveConfirmarPorTelefoneInvalido(String telefone) {
        List<GooglePesquisaWebRequest> consultas = new ArrayList<>();
        GooglePesquisaGateway client = request -> {
            consultas.add(request);
            return resposta(request, List.of(perfil("Fotos")));
        };
        new PesquisaWebInternaService(client, classificador).pesquisar(lead("Supermercado Michel", telefone));
        assertThat(consultas).hasSize(3);
    }

    private java.util.Optional<URI> classificar(PesquisaLeadDados lead, GoogleResultadoWeb... resultados) {
        return classificador.classificar(lead, List.of(resultados), List.of(resultados)).instagram();
    }

    private PesquisaLeadDados lead() { return lead("Supermercado Michel", "552835421440"); }

    private PesquisaLeadDados leadSemMunicipio() {
        return new PesquisaLeadDados("fixture-michel", "Supermercado Michel", CategoriaNegocio.MERCADO,
            "Av. Min. Araripe, 288 - Castelo - ES", "Avenida Ministro Araripe", "288", "Centro",
            null, "ES", "552835421440", null, null);
    }

    private PesquisaLeadDados lead(String nome, String telefone) {
        return new PesquisaLeadDados("fixture-michel", nome, CategoriaNegocio.MERCADO,
            "Av. Min. Araripe, 288 - Castelo - ES", "Avenida Ministro Araripe", "288", "Centro",
            "Castelo", "ES", telefone, null, null);
    }

    private GoogleResultadoWeb perfil(String resumo) {
        return candidato(PERFIL, "Central de Compras Michel", resumo);
    }

    private GoogleResultadoWeb fonte(String resumo) {
        return candidato("https://rede.example/lojas", "Lojas da rede", resumo);
    }

    private GoogleResultadoWeb candidato(String url, String titulo, String resumo) {
        return new GoogleResultadoWeb(URI.create(url), titulo, resumo);
    }

    private GooglePesquisaWebResponse resposta(GooglePesquisaWebRequest request, List<GoogleResultadoWeb> resultados) {
        return new GooglePesquisaWebResponse(request.googlePlaceId(), request.tipo(),
            BravePesquisaApiClient.montarConsulta(request), resultados);
    }

    record Amostra(PesquisaLeadDados lead, List<GooglePesquisaWebResponse> respostas) {}
}
