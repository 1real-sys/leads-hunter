package dev.jlm.leadshunter.busca;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.jlm.leadshunter.config.ApiExceptionHandler;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BuscaEmailControllerTest {
    @Mock private BuscaEmailExecucaoService service;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new BuscaController(
            mock(BuscaService.class), mock(BuscaCnpjService.class),
            mock(BuscaInformacoesExecucaoService.class), service))
            .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void postIniciaExecucaoAssincronaEGetDevolveProgresso() throws Exception {
        when(service.iniciar(10L)).thenReturn(resposta(PesquisaInformacoesStatus.PENDENTE));
        when(service.consultar(10L)).thenReturn(Optional.of(resposta(PesquisaInformacoesStatus.EM_ANDAMENTO)));

        mvc.perform(post("/api/buscas/10/emails"))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", "/api/buscas/10/emails"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.status").value("PENDENTE"));
        mvc.perform(get("/api/buscas/10/emails"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
            .andExpect(jsonPath("$.progresso").value(4))
            .andExpect(jsonPath("$.descartadosDominioExterno").value(1))
            .andExpect(jsonPath("$.encontrados").value(1));
    }

    @Test
    void semExecucaoRetorna204EBuscaInexistenteRetorna404() throws Exception {
        when(service.consultar(10L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/buscas/10/emails")).andExpect(status().isNoContent());
        when(service.iniciar(11L)).thenThrow(new BuscaNaoEncontradaException(11L));
        mvc.perform(post("/api/buscas/11/emails"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("BUSCA_NAO_ENCONTRADA"));
    }

    @Test
    void limiteRetorna429SemDadosInternos() throws Exception {
        when(service.iniciar(10L)).thenThrow(new BuscaEmailLimiteException());
        mvc.perform(post("/api/buscas/10/emails"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.codigo").value("EMAIL_LIMITE_EXCEDIDO"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "texto"})
    void idInvalidoRetorna400(String id) throws Exception {
        mvc.perform(post("/api/buscas/" + id + "/emails")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/buscas/" + id + "/emails")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private BuscaEmailResponse resposta(PesquisaInformacoesStatus status) {
        var agora = LocalDateTime.of(2026, 9, 23, 10, 0);
        return new BuscaEmailResponse(20L, 10L, status, agora, agora, agora, null,
            5, 4, 1, 0, 3, 1, 1, 1, 1, null, null);
    }
}
