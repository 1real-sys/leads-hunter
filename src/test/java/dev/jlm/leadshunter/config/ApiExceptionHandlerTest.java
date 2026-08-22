package dev.jlm.leadshunter.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.jlm.leadshunter.busca.BuscaController;
import dev.jlm.leadshunter.busca.BuscaNaoEncontradaException;
import dev.jlm.leadshunter.busca.BuscaService;
import dev.jlm.leadshunter.integracao.places.PlacesApiInvalidResponseException;
import dev.jlm.leadshunter.integracao.places.PlacesApiQuotaExceededException;
import dev.jlm.leadshunter.integracao.places.PlacesApiUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ApiExceptionHandlerTest {

    @Mock
    private BuscaService buscaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BuscaController(buscaService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @Test
    void deveRetornarErroPadronizadoQuandoCotaDaGoogleForExcedida() throws Exception {
        when(buscaService.listarHistorico()).thenThrow(new PlacesApiQuotaExceededException());

        mockMvc.perform(get("/api/buscas"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.codigo").value("GOOGLE_PLACES_QUOTA_EXCEEDED"))
            .andExpect(jsonPath("$.path").value("/api/buscas"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void deveRetornarErroPadronizadoQuandoGoogleEstiverIndisponivel() throws Exception {
        when(buscaService.listarHistorico()).thenThrow(new PlacesApiUnavailableException());

        mockMvc.perform(get("/api/buscas"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.codigo").value("GOOGLE_PLACES_UNAVAILABLE"))
            .andExpect(jsonPath("$.mensagem").value(
                "A Google Places API está indisponível no momento. Tente novamente mais tarde."
            ))
            .andExpect(jsonPath("$.path").value("/api/buscas"));
    }

    @Test
    void deveRetornarBadGatewayParaRespostaInvalidaSemExporDetalhesInternos() throws Exception {
        when(buscaService.listarHistorico()).thenThrow(new PlacesApiInvalidResponseException(
            new IllegalStateException("resposta interna com segredo")
        ));

        mockMvc.perform(get("/api/buscas"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.status").value(502))
            .andExpect(jsonPath("$.codigo").value("GOOGLE_PLACES_INVALID_RESPONSE"))
            .andExpect(jsonPath("$.mensagem").value(
                "A Google Places API retornou uma resposta inválida."
            ));
    }

    @Test
    void deveManterContratoPadronizadoParaRecursoNaoEncontrado() throws Exception {
        when(buscaService.buscarHistoricoPorId(99L)).thenThrow(new BuscaNaoEncontradaException(99L));

        mockMvc.perform(get("/api/buscas/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.codigo").value("BUSCA_NAO_ENCONTRADA"))
            .andExpect(jsonPath("$.path").value("/api/buscas/99"));
    }
}
