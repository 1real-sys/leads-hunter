package dev.jlm.leadshunter.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.jlm.leadshunter.busca.BuscaController;
import dev.jlm.leadshunter.busca.BuscaNaoEncontradaException;
import dev.jlm.leadshunter.busca.BuscaService;
import dev.jlm.leadshunter.integracao.places.PlacesApiConfigurationException;
import dev.jlm.leadshunter.integracao.places.PlacesApiInvalidResponseException;
import dev.jlm.leadshunter.integracao.places.PlacesApiQuotaExceededException;
import dev.jlm.leadshunter.integracao.places.PlacesApiRequestRejectedException;
import dev.jlm.leadshunter.integracao.places.PlacesApiUnavailableException;
import dev.jlm.leadshunter.integracao.places.PlacesRateLimitExceededException;
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
    void deveRetornarErroPadronizadoQuandoRateLimitLocalForExcedido() throws Exception {
        when(buscaService.listarHistorico()).thenThrow(new PlacesRateLimitExceededException());

        mockMvc.perform(get("/api/buscas"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.codigo").value("GOOGLE_PLACES_RATE_LIMIT"))
            .andExpect(jsonPath("$.mensagem").value(
                "Limite temporário de consultas à Google Places atingido. Tente novamente em instantes."
            ));
    }

    @Test
    void deveRetornarErroPadronizadoQuandoConfiguracaoDaGoogleEstiverAusente() throws Exception {
        when(buscaService.listarHistorico()).thenThrow(new PlacesApiConfigurationException(
            "Configure GOOGLE_PLACES_API_KEY."
        ));

        mockMvc.perform(get("/api/buscas"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.codigo").value("GOOGLE_PLACES_CONFIGURATION"))
            .andExpect(jsonPath("$.mensagem").value("Configure GOOGLE_PLACES_API_KEY."));
    }

    @Test
    void deveRetornarBadGatewayQuandoGoogleRejeitarAConsulta() throws Exception {
        when(buscaService.listarHistorico()).thenThrow(new PlacesApiRequestRejectedException(
            new IllegalStateException("detalhe interno da resposta")
        ));

        mockMvc.perform(get("/api/buscas"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.status").value(502))
            .andExpect(jsonPath("$.codigo").value("GOOGLE_PLACES_REQUEST_REJECTED"))
            .andExpect(jsonPath("$.mensagem").value(
                "A Google Places API rejeitou a consulta enviada."
            ));
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

    @Test
    void deveOcultarDetalhesQuandoOcorrerErroNaoTratado() throws Exception {
        when(buscaService.listarHistorico()).thenThrow(new IllegalStateException("segredo interno"));

        mockMvc.perform(get("/api/buscas"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"))
            .andExpect(jsonPath("$.mensagem").value(
                "Ocorreu um erro interno. Tente novamente mais tarde."
            ))
            .andExpect(jsonPath("$.path").value("/api/buscas"))
            .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                result.getResponse().getContentAsString()
            ).doesNotContain("segredo interno", "IllegalStateException"));
    }
}
