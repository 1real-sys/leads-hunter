package dev.jlm.leadshunter.bloqueio;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.jlm.leadshunter.config.ApiExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class NomeBloqueadoControllerTest {

    @Mock
    private NomeBloqueadoService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NomeBloqueadoController(service))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @Test
    void deveListarBloqueiosViaHttp() throws Exception {
        when(service.listar()).thenReturn(List.of(criarResponse()));

        mockMvc.perform(get("/api/bloqueios"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(12))
            .andExpect(jsonPath("$[0].termo").value("Supermercados BH"))
            .andExpect(jsonPath("$[0].criadoEm").value("2026-09-08T11:00:00"));
    }

    @Test
    void deveCadastrarBloqueioViaHttp() throws Exception {
        when(service.cadastrar("Supermercados BH")).thenReturn(criarResponse());

        mockMvc.perform(post("/api/bloqueios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"termo":"Supermercados BH"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(12))
            .andExpect(jsonPath("$.termo").value("Supermercados BH"));

        verify(service).cadastrar("Supermercados BH");
    }

    @Test
    void deveRejeitarPayloadInvalido() throws Exception {
        mockMvc.perform(post("/api/bloqueios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"termo":"ab"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACAO_INVALIDA"));

        verifyNoInteractions(service);
    }

    @Test
    void deveRetornarErroPadronizadoParaDuplicidade() throws Exception {
        when(service.cadastrar("Supermercados BH"))
            .thenThrow(new NomeBloqueadoDuplicadoException());

        mockMvc.perform(post("/api/bloqueios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"termo":"Supermercados BH"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("TERMO_BLOQUEADO_DUPLICADO"))
            .andExpect(jsonPath("$.mensagem")
                .value("Já existe um bloqueio cadastrado para esse termo."));
    }

    @Test
    void deveRemoverBloqueioViaHttp() throws Exception {
        doNothing().when(service).remover(12L);

        mockMvc.perform(delete("/api/bloqueios/12"))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(service).remover(eq(12L));
    }

    @Test
    void deveRetornar404AoRemoverIdInexistente() throws Exception {
        org.mockito.Mockito.doThrow(new NomeBloqueadoNaoEncontradoException(99L))
            .when(service).remover(99L);

        mockMvc.perform(delete("/api/bloqueios/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("BLOQUEIO_NAO_ENCONTRADO"))
            .andExpect(jsonPath("$.path").value("/api/bloqueios/99"));
    }

    private NomeBloqueadoResponse criarResponse() {
        return new NomeBloqueadoResponse(
            12L,
            "Supermercados BH",
            LocalDateTime.of(2026, 9, 8, 11, 0)
        );
    }
}
