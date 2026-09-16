package dev.jlm.leadshunter.integracao.pesquisa;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** Diagnóstico opt-in, somente leitura: mede a captura real do Brave em leads reais, sem persistir nada. */
@EnabledIfSystemProperty(named = "pesquisaBraveLeadsLive", matches = "true")
class PesquisaBraveLeadsReaisLiveTest {

    private static final int LIMITE_LEADS = 6;
    private static final String SQL = """
        select google_place_id, nome, categoria, endereco_formatado, logradouro, numero, bairro,
               municipio_nome, uf, telefone_normalizado, cnpj, razao_social
        from leads
        where nome is not null and municipio_nome is not null
          and (? is null or nome = ?)
        order by id desc
        limit ?
        """;

    @Test
    @Timeout(300)
    void deveMedirCapturaDoBraveEmLeadsReais() throws Exception {
        String replay = System.getProperty("pesquisaBraveReplay");
        if (replay != null) {
            medir(List.of(new ObjectMapper().readValue(Files.readString(Path.of(replay)), Amostra[].class)));
            return;
        }
        PropertySource<?> fonte = new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml")).get(0);
        var ambiente = new StandardEnvironment();
        ambiente.getPropertySources().addLast(fonte);
        String url = ambiente.getRequiredProperty("spring.datasource.url");
        String usuario = ambiente.getRequiredProperty("spring.datasource.username");
        String senha = ambiente.getProperty("spring.datasource.password", "");
        String apiKey = ambiente.getRequiredProperty("pesquisa-inteligente.brave.api-key");

        var brave = new BravePesquisaApiClient(apiKey, true, 15_000, 10, 2_097_152);
        List<Amostra> amostras = new ArrayList<>();
        String consultaDiagnostico = System.getProperty("pesquisaBraveConsulta");
        if (consultaDiagnostico != null && System.getProperty("pesquisaBraveNome") == null) {
            throw new IllegalArgumentException("Consulta diagnóstica exige o nome exato do lead");
        }
        try (Connection conexao = DriverManager.getConnection(url, usuario, senha);
             PreparedStatement comando = conexao.prepareStatement(SQL)) {
            conexao.setReadOnly(true);
            String nome = System.getProperty("pesquisaBraveNome");
            comando.setString(1, nome);
            comando.setString(2, nome);
            comando.setInt(3, LIMITE_LEADS);
            try (ResultSet rs = comando.executeQuery()) {
                while (rs.next()) {
                    var lead = new PesquisaLeadDados(
                        rs.getString("google_place_id"),
                        rs.getString("nome"),
                        CategoriaNegocio.valueOf(rs.getString("categoria")),
                        rs.getString("endereco_formatado"),
                        rs.getString("logradouro"),
                        rs.getString("numero"),
                        rs.getString("bairro"),
                        rs.getString("municipio_nome"),
                        rs.getString("uf"),
                        rs.getString("telefone_normalizado"),
                        rs.getString("cnpj"),
                        rs.getString("razao_social"));
                    List<GooglePesquisaWebResponse> respostas = new ArrayList<>();
                    // Executa o mesmo planejamento da aplicação: até cinco consultas por lead.
                    // A amostra tem no máximo seis leads; não há retry ou escrita no banco real.
                    GooglePesquisaGateway gravador = request -> {
                        try {
                            Thread.sleep(1_100);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new GooglePesquisaWebIndisponivelException(exception);
                        }
                        var resposta = brave.pesquisar(request);
                        respostas.add(resposta);
                        return resposta;
                    };
                    if (consultaDiagnostico == null) {
                        new PesquisaWebInternaService(gravador,
                            new ClassificadorUrlService(new UrlCandidatoCanonicalizer()),
                            new LeitorPaginaCandidata(10_000, 524_288)).pesquisar(lead);
                        assertThat(respostas).hasSizeBetween(2, 5);
                    } else {
                        gravador.pesquisar(new GooglePesquisaWebRequest(lead.googlePlaceId(), consultaDiagnostico,
                            lead.categoria(), null, null, null, TipoPesquisaWeb.SITE_PROPRIO));
                    }
                    amostras.add(new Amostra(lead, respostas));
                }
            }
        }
        Path arquivo = Files.createTempFile("brave-precisao-", ".json");
        Files.writeString(arquivo, new ObjectMapper().writeValueAsString(amostras));
        System.out.println("BRAVE_AMOSTRA arquivo=" + arquivo + "; chamadas="
            + amostras.stream().mapToInt(a -> a.respostas().size()).sum());
        if (consultaDiagnostico == null) medir(amostras);
    }

    private void medir(List<Amostra> amostras) {
        assertThat(amostras).isNotEmpty();
        String instagramEsperado = System.getProperty("pesquisaBraveInstagramEsperado");
        if (instagramEsperado != null) assertThat(amostras).hasSize(1);
        int comInstagram = 0;
        int comSite = 0;
        for (var amostra : amostras) {
            GooglePesquisaGateway replay = request -> amostra.respostas().stream()
                .filter(r -> r.tipo() == request.tipo()
                    && r.consulta().equals(BravePesquisaApiClient.montarConsulta(request)))
                .findFirst().orElseThrow(() -> new AssertionError("Consulta ausente na amostra; nenhuma rede no replay"));
            var service = new PesquisaWebInternaService(replay,
                new ClassificadorUrlService(new UrlCandidatoCanonicalizer()));
            var resultado = service.pesquisar(amostra.lead());
            if (instagramEsperado != null) {
                assertThat(resultado.instagram()).contains(java.net.URI.create(instagramEsperado));
            }
            if (resultado.instagram().isPresent()) comInstagram++;
            if (resultado.siteProprio().isPresent()) comSite++;
            System.out.println("BRAVE_LEAD nome=" + amostra.lead().nome() + "; municipio=" + amostra.lead().municipio()
                + "; instagram=" + resultado.instagram().map(Object::toString).orElse("ausente")
                + "; site=" + resultado.siteProprio().map(Object::toString).orElse("ausente"));
        }
        System.out.println("BRAVE_RESUMO total=" + amostras.size() + "; comInstagram=" + comInstagram
            + "; comSite=" + comSite);
    }

    record Amostra(PesquisaLeadDados lead, List<GooglePesquisaWebResponse> respostas) {}
}
