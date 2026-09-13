package dev.jlm.leadshunter.integracao.pesquisa;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** Diagnóstico opt-in, somente leitura: mede a cobertura do Bing em leads reais, sem persistir nada. */
@EnabledIfSystemProperty(named = "pesquisaBingLeadsLive", matches = "true")
class PesquisaBingLeadsReaisLiveTest {

    private static final int LIMITE_LEADS = 6;
    private static final String SQL = """
        select google_place_id, nome, categoria, endereco_formatado, logradouro, numero, bairro,
               municipio_nome, uf, telefone_normalizado, cnpj, razao_social
        from leads
        where nome is not null and municipio_nome is not null
        order by id desc
        limit ?
        """;

    @Test
    @Timeout(600)
    void deveMedirCoberturaDoBingEmLeadsReais() throws Exception {
        PropertySource<?> fonte = new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml")).get(0);
        String url = (String) fonte.getProperty("spring.datasource.url");
        String usuario = (String) fonte.getProperty("spring.datasource.username");
        String senha = (String) fonte.getProperty("spring.datasource.password");

        try (var navigator = new PlaywrightGooglePesquisaNavigator(20_000, 2_097_152, 1, 4_000)) {
            var google = new GooglePesquisaWebClient(navigator, new GooglePesquisaHtmlParser(), 10, 3_600_000);
            var fallback = new PesquisaWebFallbackClient(google, navigator, new PesquisaAlternativaHtmlParser());
            var service = new PesquisaWebInternaService(fallback,
                new ClassificadorUrlService(new UrlCandidatoCanonicalizer()));

            int total = 0;
            int comInstagram = 0;
            int comSite = 0;
            try (Connection conexao = DriverManager.getConnection(url, usuario, senha);
                 PreparedStatement comando = conexao.prepareStatement(SQL)) {
                comando.setInt(1, LIMITE_LEADS);
                try (ResultSet rs = comando.executeQuery()) {
                    while (rs.next()) {
                        total++;
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
                        try {
                            var resultado = service.pesquisar(lead);
                            if (resultado.instagram().isPresent()) comInstagram++;
                            if (resultado.siteProprio().isPresent()) comSite++;
                            System.out.println("BING_LEAD nome=" + lead.nome() + "; municipio=" + lead.municipio()
                                + "; instagram=" + resultado.instagram().map(Object::toString).orElse("ausente")
                                + "; site=" + resultado.siteProprio().map(Object::toString).orElse("ausente"));
                        } catch (RuntimeException exception) {
                            System.out.println("BING_LEAD nome=" + lead.nome() + "; erro="
                                + exception.getClass().getSimpleName());
                        }
                    }
                }
            }
            System.out.println("BING_RESUMO total=" + total + "; comInstagram=" + comInstagram
                + "; comSite=" + comSite);
        }
    }
}
