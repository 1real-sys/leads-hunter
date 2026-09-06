package dev.jlm.leadshunter.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.jlm.leadshunter.config.ApiExceptionHandler;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class GeografiaControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUp() {
        MunicipioDataset dataset = new MunicipioDataset(
            OBJECT_MAPPER,
            new ClassPathResource("geo/municipios-idhm.json")
        );
        mockMvc = MockMvcBuilders.standaloneSetup(
                new GeografiaController(new MunicipioService(dataset))
            )
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @Test
    void deveRetornarFeatureCollectionApenasDaRegiaoPedida() throws Exception {
        String resposta = mockMvc.perform(get("/api/geografia/municipios")
                .param("bbox", "-40.4,-20.4,-40.2,-20.2"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.parseMediaType("application/geo+json")
            ))
            .andExpect(header().string("Cache-Control", "max-age=86400, public"))
            .andExpect(jsonPath("$.type").value("FeatureCollection"))
            .andExpect(jsonPath("$.features").isArray())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode features = OBJECT_MAPPER.readTree(resposta).get("features");
        assertThat(features.size()).isBetween(1, 99);
        assertThat(StreamSupport.stream(features.spliterator(), false)
            .map(feature -> feature.get("properties").get("codigoIbge").stringValue()))
            .contains("3205309")
            .doesNotContain("4106902");
        assertThat(StreamSupport.stream(features.spliterator(), false))
            .allSatisfy(feature -> {
                assertThat(feature.get("properties").get("nome").isString()).isTrue();
                assertThat(feature.get("properties").get("uf").isString()).isTrue();
                assertThat(feature.get("properties").get("idhmReferencia").intValue())
                    .isEqualTo(2010);
                assertThat(feature.get("geometry").get("coordinates").isArray()).isTrue();
            });
    }

    @Test
    void devePreservarEstruturaMultiPolygonNaResposta() throws Exception {
        String resposta = mockMvc.perform(get("/api/geografia/municipios")
                .param("bbox", "-46.7,-15.1,-45.9,-14.4"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode feature = StreamSupport.stream(
                OBJECT_MAPPER.readTree(resposta).get("features").spliterator(),
                false
            )
            .filter(item -> "5220702".equals(
                item.get("properties").get("codigoIbge").stringValue()
            ))
            .findFirst()
            .orElseThrow();
        JsonNode geometry = feature.get("geometry");
        JsonNode coordinates = geometry.get("coordinates");

        assertThat(geometry.get("type").stringValue()).isEqualTo("MultiPolygon");
        assertThat(coordinates.size()).isEqualTo(2);
        assertThat(coordinates.get(0).isArray()).isTrue();
        assertThat(coordinates.get(0).get(0).isArray()).isTrue();
        assertThat(coordinates.get(0).get(0).get(0).isArray()).isTrue();
        assertThat(coordinates.get(0).get(0).get(0).get(0).isNumber()).isTrue();
    }

    @Test
    void deveRetornarColecaoVaziaQuandoNaoHouverMunicipios() throws Exception {
        mockMvc.perform(get("/api/geografia/municipios")
                .param("bbox", "-30,-30,-29,-29"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("FeatureCollection"))
            .andExpect(jsonPath("$.features").isEmpty());
    }

    @Test
    void deveRejeitarBboxQueExcedaOTetoDeMunicipios() throws Exception {
        mockMvc.perform(get("/api/geografia/municipios")
                .param("bbox", "-75,-34,-28,6"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"))
            .andExpect(jsonPath("$.mensagem").value(
                "O bbox informado abrange municípios demais. Aproxime o mapa e tente novamente."
            ));
    }

    @Test
    void deveRejeitarBboxInvalidoComContratoPadronizado() throws Exception {
        for (String bbox : new String[] {
            "-40,-20,-39",
            "NaN,-20,-39,-19",
            "Infinity,-20,-39,-19",
            "-39,-20,-40,-19",
            "-181,-20,-39,-19"
        }) {
            mockMvc.perform(get("/api/geografia/municipios").param("bbox", bbox))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"))
                .andExpect(jsonPath("$.path").value("/api/geografia/municipios"))
                .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    @Test
    void deveExigirOBbox() throws Exception {
        mockMvc.perform(get("/api/geografia/municipios"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));
    }
}
