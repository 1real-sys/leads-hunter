package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassificadorUrlServiceTest {

    private final ClassificadorUrlService classificador = new ClassificadorUrlService(
        new UrlCandidatoCanonicalizer()
    );

    @Test
    void deveCapturarInstagramClaroMesmoSemSiteProprio() {
        PesquisaLeadDados lead = lead(
            "Açougue São José",
            CategoriaNegocio.ACOUGUE,
            "Campinas",
            "SP"
        );
        GoogleResultadoWeb instagram = resultado(
            "https://www.instagram.com/acouguesaojose/?igsh=teste",
            "ACOUGUE SAO JOSE (@acouguesaojose) • Instagram",
            "Casa de carnes no centro de CAMPINAS - SP"
        );

        PesquisaInformacoesWebResultado resultado = classificador.classificar(
            lead,
            List.of(instagram),
            List.of()
        );

        assertThat(resultado.instagram()).contains(URI.create("https://www.instagram.com/acouguesaojose"));
        assertThat(resultado.siteProprio()).isEmpty();
    }

    @Test
    void deveSelecionarSiteOficialERejeitarDiretorios() {
        PesquisaLeadDados lead = lead("Doce Encanto", CategoriaNegocio.DOCERIA, "Campinas", "SP");

        PesquisaInformacoesWebResultado resultado = classificador.classificar(
            lead,
            List.of(),
            List.of(
                resultado(
                    "https://www.ifood.com.br/delivery/campinas/doce-encanto",
                    "Doce Encanto no iFood",
                    "Peça doces em Campinas"
                ),
                resultado(
                    "https://doceencanto.com.br/cardapio?utm_source=google",
                    "DOCE ENCANTO | Confeitaria",
                    "Doces artesanais em Campinas, São Paulo"
                ),
                resultado(
                    "https://www.tripadvisor.com.br/doce-encanto",
                    "Avaliações de Doce Encanto",
                    "Campinas"
                )
            )
        );

        assertThat(resultado.siteProprio()).contains(URI.create("https://doceencanto.com.br/"));
    }

    @Test
    void naoDeveAceitarHomonimoGenericoDeOutraCidadeSomentePeloNome() {
        PesquisaLeadDados lead = lead("Padaria Central", CategoriaNegocio.PADARIA, "Campinas", "SP");
        GoogleResultadoWeb homonimo = resultado(
            "https://www.instagram.com/padariacentral/",
            "Padaria Central • Instagram",
            "Panificadora tradicional em Santos - SP"
        );

        PesquisaInformacoesWebResultado resultado = classificador.classificar(
            lead,
            List.of(homonimo),
            List.of()
        );

        assertThat(resultado.instagram()).isEmpty();
    }

    @Test
    void deveAceitarNomeGenericoQuandoMunicipioConfirmaAIdentidade() {
        PesquisaLeadDados lead = lead("Padaria Central", CategoriaNegocio.PADARIA, "Campinas", "SP");
        GoogleResultadoWeb candidato = resultado(
            "https://www.instagram.com/padariacentralcampinas/",
            "Padaria Central • Instagram",
            "Panificadora tradicional em Campinas - SP"
        );

        PesquisaInformacoesWebResultado resultado = classificador.classificar(
            lead,
            List.of(candidato),
            List.of()
        );

        assertThat(resultado.instagram())
            .contains(URI.create("https://www.instagram.com/padariacentralcampinas"));
    }

    @Test
    void deveRejeitarEmpateEntrePerfisPlausveis() {
        PesquisaLeadDados lead = lead("Doce Encanto", CategoriaNegocio.DOCERIA, "Campinas", "SP");

        PesquisaInformacoesWebResultado resultado = classificador.classificar(
            lead,
            List.of(
                resultado(
                    "https://instagram.com/doceencanto",
                    "Doce Encanto Confeitaria",
                    "Campinas - SP"
                ),
                resultado(
                    "https://instagram.com/doceencantooficial",
                    "Doce Encanto Confeitaria",
                    "Campinas - SP"
                )
            ),
            List.of()
        );

        assertThat(resultado.instagram()).isEmpty();
    }

    @Test
    void deveDistinguirFiliaisPeloNomeELocalizacao() {
        PesquisaLeadDados lead = lead("Coco Bambu Vitória", CategoriaNegocio.RESTAURANTE, "Vitória", "ES");

        PesquisaInformacoesWebResultado resultado = classificador.classificar(
            lead,
            List.of(
                resultado(
                    "https://instagram.com/cocobambuvilavelha",
                    "Coco Bambu Vila Velha",
                    "Restaurante em Vila Velha - ES"
                ),
                resultado(
                    "https://instagram.com/cocobambuvitoria",
                    "Coco Bambu Vitória",
                    "Restaurante em Vitória - ES"
                )
            ),
            List.of()
        );

        assertThat(resultado.instagram()).contains(URI.create("https://www.instagram.com/cocobambuvitoria"));
    }

    @Test
    void deveRejeitarCandidatoComCnpjExplicitoDeOutraUnidade() {
        PesquisaLeadDados lead = new PesquisaLeadDados(
            "place-123",
            "Mercado Avenida",
            CategoriaNegocio.MERCADO,
            "Avenida Brasil, 100, Campinas - SP",
            "Avenida Brasil",
            "100",
            "Centro",
            "Campinas",
            "SP",
            "5519999999999",
            "12345678000190",
            "Mercado Avenida Ltda"
        );
        GoogleResultadoWeb candidato = resultado(
            "https://mercadoavenida.com.br",
            "Mercado Avenida | Site oficial",
            "Campinas - SP • CNPJ 98.765.432/0001-10"
        );

        PesquisaInformacoesWebResultado resultado = classificador.classificar(
            lead,
            List.of(),
            List.of(candidato)
        );

        assertThat(resultado.siteProprio()).isEmpty();
    }

    @Test
    void deveUsarGooglePlaceIdSomenteQuandoAparecerNoResultadoPublico() {
        PesquisaLeadDados lead = lead("Mercado Central", CategoriaNegocio.MERCADO, null, "ES");
        GoogleResultadoWeb candidato = resultado(
            "https://instagram.com/mercadocentral_es",
            "Mercado Central • Instagram",
            "Perfil oficial • referência pública place-123"
        );

        PesquisaInformacoesWebResultado resultado = classificador.classificar(
            lead,
            List.of(candidato),
            List.of()
        );

        assertThat(resultado.instagram()).contains(URI.create("https://www.instagram.com/mercadocentral_es"));
    }

    @Test
    void deveDeduplicarMesmoSiteComWwwEParametrosDiferentes() {
        PesquisaLeadDados lead = lead("Doce Encanto", CategoriaNegocio.DOCERIA, "Campinas", "SP");

        PesquisaInformacoesWebResultado resultado = classificador.classificar(
            lead,
            List.of(),
            List.of(
                resultado(
                    "https://www.doceencanto.com.br/?utm_source=google",
                    "Doce Encanto Confeitaria",
                    "Campinas - SP"
                ),
                resultado(
                    "https://doceencanto.com.br/cardapio?gclid=123",
                    "Doce Encanto Confeitaria",
                    "Campinas - SP"
                )
            )
        );

        assertThat(resultado.siteProprio()).isPresent();
        assertThat(resultado.siteProprio().orElseThrow().getHost()).endsWith("doceencanto.com.br");
    }

    private PesquisaLeadDados lead(
        String nome,
        CategoriaNegocio categoria,
        String municipio,
        String uf
    ) {
        return new PesquisaLeadDados(
            "place-123",
            nome,
            categoria,
            municipio == null ? null : "Rua das Flores, 10, " + municipio + " - " + uf,
            "Rua das Flores",
            "10",
            "Centro",
            municipio,
            uf,
            null,
            null,
            null
        );
    }

    private GoogleResultadoWeb resultado(String url, String titulo, String resumo) {
        return new GoogleResultadoWeb(URI.create(url), titulo, resumo);
    }
}
