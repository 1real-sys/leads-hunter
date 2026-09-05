package dev.jlm.leadshunter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import dev.jlm.leadshunter.integracao.places.PlacesApiClient;
import dev.jlm.leadshunter.integracao.places.PlacesSearchResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@Transactional
@Import(MvpFlowIntegrationTest.StubPlacesConfiguration.class)
class MvpFlowIntegrationTest {

    private static final String GOOGLE_PLACE_ID = "fe17-flow-place-001";
    private static final String NOME_LEAD = "Padaria Fluxo Integrado";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StubPlacesApiClient placesApiClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void devePercorrerBuscaAtualizacaoHistoricoEExportacaoComPersistencia() throws Exception {
        MvcResult busca = mockMvc.perform(post("/api/buscas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "enderecoBase": "Centro de Vitória",
                      "latitude": -20.3155,
                      "longitude": -40.3128,
                      "raioKm": 3,
                      "categorias": ["PADARIA"]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.totalEncontrados").value(1))
            .andExpect(jsonPath("$.leads[0].nome").value(NOME_LEAD))
            .andExpect(jsonPath("$.leads[0].whatsappUrl").value("https://wa.me/5527999990000"))
            .andReturn();

        JsonNode buscaJson = objectMapper.readTree(busca.getResponse().getContentAsString());
        long buscaId = buscaJson.get("id").asLong();
        long leadId = buscaJson.get("leads").get(0).get("id").asLong();

        mockMvc.perform(get("/api/leads").param("status", "NOVO"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(NOME_LEAD)));

        mockMvc.perform(patch("/api/leads/{id}", leadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "QUALIFICADO",
                      "observacoes": "Retornar na próxima semana.",
                      "ultimoContatoEm": "2026-09-05T11:00:00"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("QUALIFICADO"))
            .andExpect(jsonPath("$.observacoes").value("Retornar na próxima semana."))
            .andExpect(jsonPath("$.ultimoContatoEm").value("2026-09-05T11:00:00"));

        mockMvc.perform(get("/api/leads/{id}", leadId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("QUALIFICADO"))
            .andExpect(jsonPath("$.observacoes").value("Retornar na próxima semana."))
            .andExpect(jsonPath("$.ultimoContatoEm").value("2026-09-05T11:00:00"));

        mockMvc.perform(get("/api/buscas/{id}", buscaId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.leads[0].nome").value(NOME_LEAD))
            .andExpect(jsonPath("$.leads[0].scoreNaBusca").isNumber())
            .andExpect(jsonPath("$.leads[0].status").value("QUALIFICADO"))
            .andExpect(jsonPath("$.leads[0].observacoes")
                .value("Retornar na próxima semana."))
            .andExpect(jsonPath("$.leads[0].ultimoContatoEm")
                .value("2026-09-05T11:00:00"));

        mockMvc.perform(get("/api/exportacao/leads.csv").param("status", "QUALIFICADO"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("leads.csv")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(NOME_LEAD)))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Retornar na próxima semana.")));

        MvcResult excel = mockMvc.perform(get("/api/exportacao/leads.xlsx")
                .param("status", "QUALIFICADO"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("leads.xlsx")))
            .andReturn();

        byte[] excelBytes = excel.getResponse().getContentAsByteArray();
        assertThat(excelBytes).isNotEmpty();
        assertThat(excelBytes[0]).isEqualTo((byte) 'P');
        assertThat(excelBytes[1]).isEqualTo((byte) 'K');
        assertThat(placesApiClient.chamadas()).isEqualTo(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubPlacesConfiguration {

        @Bean(name = "stubPlacesApiClient")
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
        public PlacesSearchResponse buscarProximos(
            dev.jlm.leadshunter.integracao.places.PlacesSearchRequest request
        ) {
            chamadas.incrementAndGet();
            return new PlacesSearchResponse(List.of(placeResultStatic()));
        }

        int chamadas() {
            return chamadas.get();
        }

        private static PlacesSearchResponse.PlaceResult placeResultStatic() {
            return new PlacesSearchResponse.PlaceResult(
                GOOGLE_PLACE_ID,
                NOME_LEAD,
                CategoriaNegocio.PADARIA,
                "Rua do Fluxo, 17",
                "+55 27 99999-0000",
                new BigDecimal("-20.3155"),
                new BigDecimal("-40.3128"),
                new BigDecimal("4.8"),
                80,
                "OPERATIONAL",
                List.of("bakery")
            );
        }
    }
}
