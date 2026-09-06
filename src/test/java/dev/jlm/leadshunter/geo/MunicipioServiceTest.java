package dev.jlm.leadshunter.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.ObjectMapper;

class MunicipioServiceTest {

    private static MunicipioService municipioService;

    @BeforeAll
    static void setUp() {
        MunicipioDataset dataset = new MunicipioDataset(
            new ObjectMapper(),
            new ClassPathResource("geo/municipios-idhm.json")
        );
        municipioService = new MunicipioService(dataset);
    }

    @Test
    void deveLocalizarVitoriaComIdhmCongelado() {
        assertThat(localizar("-20.3155", "-40.3128"))
            .hasValueSatisfying(municipio -> {
                assertThat(municipio.codigoIbge()).isEqualTo("3205309");
                assertThat(municipio.nome()).isEqualTo("Vitória");
                assertThat(municipio.uf()).isEqualTo("ES");
                assertThat(municipio.idhm()).isEqualByComparingTo("0.845");
                assertThat(municipio.idhmReferencia()).isEqualTo((short) 2010);
            });
    }

    @Test
    void deveLocalizarCuritibaComIdhmCongelado() {
        assertThat(localizar("-25.4284", "-49.2733"))
            .hasValueSatisfying(municipio -> {
                assertThat(municipio.codigoIbge()).isEqualTo("4106902");
                assertThat(municipio.nome()).isEqualTo("Curitiba");
                assertThat(municipio.uf()).isEqualTo("PR");
                assertThat(municipio.idhm()).isEqualByComparingTo("0.823");
                assertThat(municipio.idhmReferencia()).isEqualTo((short) 2010);
            });
    }

    @Test
    void deveRetornarVazioParaCoordenadasAusentesInvalidasOuForaDoBrasil() {
        assertThat(municipioService.localizar(null, new BigDecimal("-40.3128"))).isEmpty();
        assertThat(municipioService.localizar(new BigDecimal("-20.3155"), null)).isEmpty();
        assertThat(localizar("91", "0")).isEmpty();
        assertThat(localizar("40.7128", "-74.0060")).isEmpty();
    }

    @Test
    void deveListarSomenteEnvelopesQueIntersectamOBbox() {
        MunicipiosGeoJsonResponse resposta = municipioService.listarPorBbox(
            "-40.4,-20.4,-40.2,-20.2"
        );

        assertThat(resposta.type()).isEqualTo("FeatureCollection");
        assertThat(resposta.features())
            .isNotEmpty()
            .allSatisfy(feature -> {
                assertThat(feature.type()).isEqualTo("Feature");
                assertThat(feature.geometry().type()).isIn("Polygon", "MultiPolygon");
                if (feature.geometry() instanceof MunicipiosGeoJsonResponse.PolygonGeometry polygon) {
                    assertThat(polygon.coordinates()).isNotEmpty();
                } else {
                    assertThat(feature.geometry())
                        .isInstanceOf(MunicipiosGeoJsonResponse.MultiPolygonGeometry.class);
                    var multiPolygon = (MunicipiosGeoJsonResponse.MultiPolygonGeometry)
                        feature.geometry();
                    assertThat(multiPolygon.coordinates()).isNotEmpty();
                }
            })
            .extracting(feature -> feature.properties().codigoIbge())
            .contains("3205309")
            .doesNotContain("4106902");
    }

    @Test
    void deveRecusarDatasetComChecksumDiferente() throws Exception {
        byte[] adulterado = new ClassPathResource("geo/municipios-idhm.json")
            .getContentAsByteArray();
        adulterado[100] ^= 1;

        assertThatThrownBy(() -> new MunicipioDataset(
            new ObjectMapper(),
            new ByteArrayResource(adulterado)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Checksum inválido");
    }

    private java.util.Optional<MunicipioInfo> localizar(String latitude, String longitude) {
        return municipioService.localizar(new BigDecimal(latitude), new BigDecimal(longitude));
    }
}
