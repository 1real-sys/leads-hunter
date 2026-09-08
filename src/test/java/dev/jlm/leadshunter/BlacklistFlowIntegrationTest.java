package dev.jlm.leadshunter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.jlm.leadshunter.busca.BuscaLeadRepository;
import dev.jlm.leadshunter.integracao.places.PlacesApiClient;
import dev.jlm.leadshunter.integracao.places.PlacesSearchRequest;
import dev.jlm.leadshunter.integracao.places.PlacesSearchResponse;
import dev.jlm.leadshunter.lead.CategoriaNegocio;
import dev.jlm.leadshunter.lead.LeadRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Transactional
@Import(BlacklistFlowIntegrationTest.StubPlacesConfiguration.class)
class BlacklistFlowIntegrationTest {

    private static final String GOOGLE_PLACE_ID_BLOQUEADO = "bl04-supermercados-bh";
    private static final String GOOGLE_PLACE_ID_PERMITIDO = "bl04-padaria-permitida";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private BuscaLeadRepository buscaLeadRepository;

    @Autowired
    private StubPlacesApiClient placesApiClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void deveCadastrarAplicarEExcluirBloqueioPeloFluxoHttp() throws Exception {
        MvcResult cadastro = mockMvc.perform(post("/api/bloqueios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"termo": "Supermercados BH"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.termo").value("Supermercados BH"))
            .andReturn();

        long bloqueioId = objectMapper
            .readTree(cadastro.getResponse().getContentAsString())
            .get("id")
            .asLong();

        mockMvc.perform(get("/api/bloqueios"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(bloqueioId))
            .andExpect(jsonPath("$[0].termo").value("Supermercados BH"));

        MvcResult busca = mockMvc.perform(post("/api/buscas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "enderecoBase": "Centro de Belo Horizonte",
                      "latitude": -19.9191,
                      "longitude": -43.9386,
                      "raioKm": 3,
                      "categorias": ["PADARIA"]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.totalEncontrados").value(3))
            .andExpect(jsonPath("$.totalBloqueados").value(1))
            .andExpect(jsonPath("$.leads.length()").value(1))
            .andExpect(jsonPath("$.leads[0].nome").value("Padaria Permitida"))
            .andReturn();

        JsonNode buscaJson = objectMapper.readTree(busca.getResponse().getContentAsString());
        long buscaId = buscaJson.get("id").asLong();

        assertThat(leadRepository.existsByGooglePlaceId(GOOGLE_PLACE_ID_BLOQUEADO)).isFalse();
        assertThat(leadRepository.existsByGooglePlaceId(GOOGLE_PLACE_ID_PERMITIDO)).isTrue();
        assertThat(buscaLeadRepository.findByBuscaIdOrderByScoreNaBuscaDesc(buscaId)).hasSize(1);
        assertThat(placesApiClient.chamadas()).isEqualTo(1);

        mockMvc.perform(get("/api/buscas/{id}", buscaId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalEncontrados").value(3))
            .andExpect(jsonPath("$.leads.length()").value(1))
            .andExpect(jsonPath("$.leads[0].nome").value("Padaria Permitida"));

        mockMvc.perform(delete("/api/bloqueios/{id}", bloqueioId))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/bloqueios"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubPlacesConfiguration {

        @Bean(name = "blacklistFlowPlacesApiClient")
        @Primary
        StubPlacesApiClient placesApiClient() {
            return new StubPlacesApiClient();
        }
    }

    static class StubPlacesApiClient extends PlacesApiClient {

        private final AtomicInteger chamadas = new AtomicInteger();

        StubPlacesApiClient() {
            super(null, null, "", "");
        }

        @Override
        public PlacesSearchResponse buscarProximos(PlacesSearchRequest request) {
            chamadas.incrementAndGet();
            PlacesSearchResponse.PlaceResult bloqueado = place(
                GOOGLE_PLACE_ID_BLOQUEADO,
                "Supermercados BH Centro"
            );
            return new PlacesSearchResponse(List.of(
                bloqueado,
                bloqueado,
                place(GOOGLE_PLACE_ID_PERMITIDO, "Padaria Permitida")
            ));
        }

        int chamadas() {
            return chamadas.get();
        }

        private static PlacesSearchResponse.PlaceResult place(String id, String nome) {
            return new PlacesSearchResponse.PlaceResult(
                id,
                nome,
                CategoriaNegocio.PADARIA,
                "Rua do Teste, 10",
                "+55 31 99999-0000",
                new BigDecimal("-19.9191"),
                new BigDecimal("-43.9386"),
                new BigDecimal("4.5"),
                40,
                "OPERATIONAL",
                List.of("bakery")
            );
        }
    }
}
