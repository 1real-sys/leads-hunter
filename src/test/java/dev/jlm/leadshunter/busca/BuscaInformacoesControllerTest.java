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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BuscaInformacoesControllerTest {
    @Mock private BuscaInformacoesExecucaoService service;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new BuscaController(
            mock(BuscaService.class), mock(BuscaCnpjService.class), service))
            .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void postSemBodyRetorna202IdEstadoELocation() throws Exception {
        when(service.iniciar(10L)).thenReturn(resposta(PesquisaInformacoesStatus.PENDENTE, null));
        mvc.perform(post("/api/buscas/10/informacoes"))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", "/api/buscas/10/informacoes"))
            .andExpect(jsonPath("$.id").value(20))
            .andExpect(jsonPath("$.buscaId").value(10))
            .andExpect(jsonPath("$.status").value("PENDENTE"));
        verify(service).iniciar(10L);
        verifyNoMoreInteractions(service);
    }

    @Test
    void postDuplicadoDevolveExecucaoAtiva() throws Exception {
        when(service.iniciar(10L)).thenReturn(resposta(PesquisaInformacoesStatus.EM_ANDAMENTO, null));
        mvc.perform(post("/api/buscas/10/informacoes"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.id").value(20))
            .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
    }

    @ParameterizedTest
    @EnumSource(PesquisaInformacoesStatus.class)
    void getRetornaTodosOsEstadosComProgressoEResumo(PesquisaInformacoesStatus estado) throws Exception {
        when(service.consultar(10L)).thenReturn(Optional.of(resposta(estado, null)));
        mvc.perform(get("/api/buscas/10/informacoes"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.status").value(estado.name()))
            .andExpect(jsonPath("$.totalLeads").value(5))
            .andExpect(jsonPath("$.progresso").value(3))
            .andExpect(jsonPath("$.processados").value(1))
            .andExpect(jsonPath("$.ignoradosJaCompletos").value(1))
            .andExpect(jsonPath("$.comInstagram").value(1))
            .andExpect(jsonPath("$.comSite").value(1))
            .andExpect(jsonPath("$.comAmbos").value(1))
            .andExpect(jsonPath("$.semInformacoes").value(0))
            .andExpect(jsonPath("$.falhas").value(1));
        verify(service).consultar(10L);
    }

    @ParameterizedTest
    @EnumSource(PesquisaInformacoesErro.class)
    void getDeFalhaRetornaErroPersistidoSeguro(PesquisaInformacoesErro erro) throws Exception {
        when(service.consultar(10L)).thenReturn(Optional.of(resposta(PesquisaInformacoesStatus.FALHA, erro)));
        mvc.perform(get("/api/buscas/10/informacoes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FALHA"))
            .andExpect(jsonPath("$.erroCodigo").value(erro.name()))
            .andExpect(jsonPath("$.erroMensagem").value(erro.mensagem()));
    }

    @Test
    void getSemExecucaoRetorna204() throws Exception {
        when(service.consultar(10L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/buscas/10/informacoes"))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    @Test
    void buscaInexistenteRetorna404NoPostEGet() throws Exception {
        when(service.iniciar(10L)).thenThrow(new BuscaNaoEncontradaException(10L));
        when(service.consultar(10L)).thenThrow(new BuscaNaoEncontradaException(10L));
        mvc.perform(post("/api/buscas/10/informacoes"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("BUSCA_NAO_ENCONTRADA"));
        mvc.perform(get("/api/buscas/10/informacoes"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("BUSCA_NAO_ENCONTRADA"));
    }

    @Test
    void capacidadeEVolumeRetornam429Padronizado() throws Exception {
        when(service.iniciar(10L)).thenThrow(new PesquisaInformacoesLimiteException());
        mvc.perform(post("/api/buscas/10/informacoes"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.codigo").value("PESQUISA_LIMITE_EXCEDIDO"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.path").value("/api/buscas/10/informacoes"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "texto", "999999999999999999999999"})
    void rejeitaIdInvalidoSemDelegar(String id) throws Exception {
        mvc.perform(post("/api/buscas/" + id + "/informacoes")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/buscas/" + id + "/informacoes")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void erroInesperadoNaoExpoeDetalhesInternos() throws Exception {
        when(service.iniciar(10L)).thenThrow(new IllegalStateException("segredo SQL interno"));
        mvc.perform(post("/api/buscas/10/informacoes"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"))
            .andExpect(jsonPath("$.mensagem").value("Ocorreu um erro interno. Tente novamente mais tarde."));
    }

    private PesquisaInformacoesExecucaoResponse resposta(PesquisaInformacoesStatus status, PesquisaInformacoesErro erro) {
        var agora = LocalDateTime.of(2026, 9, 12, 12, 0);
        return new PesquisaInformacoesExecucaoResponse(20L, 10L, status, agora, agora, agora, null,
            5, 3, 1, 1, 1, 1, 1, 0, 1, erro, erro == null ? null : erro.mensagem());
    }
}
