package dev.jlm.leadshunter.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.jlm.leadshunter.config.ApiExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LeadControllerTest {

    @Mock
    private LeadService leadService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LeadController(leadService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @Test
    void deveListarLeadsComFiltrosViaHttp() throws Exception {
        when(leadService.listar(StatusFunil.CONTATADO, CategoriaNegocio.PADARIA, Temperatura.QUENTE))
            .thenReturn(List.of(criarResposta()));

        mockMvc.perform(get("/api/leads")
                .param("status", "CONTATADO")
                .param("categoria", "PADARIA")
                .param("temperatura", "QUENTE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(35))
            .andExpect(jsonPath("$[0].nome").value("Padaria Central"))
            .andExpect(jsonPath("$[0].status").value("CONTATADO"))
            .andExpect(jsonPath("$[0].whatsappUrl")
                .value("https://wa.me/5527999990000"));

        verify(leadService).listar(StatusFunil.CONTATADO, CategoriaNegocio.PADARIA, Temperatura.QUENTE);
    }

    @Test
    void deveListarPaginaComFiltrosETotaisViaHttp() throws Exception {
        when(leadService.listarPagina(
            StatusFunil.QUALIFICADO,
            CategoriaNegocio.PADARIA,
            Temperatura.QUENTE,
            1,
            25
        )).thenReturn(new PaginaLeadsResponse(
            List.of(criarResposta()),
            1,
            25,
            63,
            3
        ));

        mockMvc.perform(get("/api/leads/pagina")
                .param("status", "QUALIFICADO")
                .param("categoria", "PADARIA")
                .param("temperatura", "QUENTE")
                .param("page", "1")
                .param("size", "25"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.leads[0].id").value(35))
            .andExpect(jsonPath("$.pagina").value(1))
            .andExpect(jsonPath("$.tamanho").value(25))
            .andExpect(jsonPath("$.totalElementos").value(63))
            .andExpect(jsonPath("$.totalPaginas").value(3));
    }

    @Test
    void deveRejeitarPaginaOuTamanhoForaDosLimites() throws Exception {
        mockMvc.perform(get("/api/leads/pagina")
                .param("status", "NOVO")
                .param("page", "-1")
                .param("size", "26"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACAO_INVALIDA"));

        verifyNoInteractions(leadService);
    }

    @Test
    void deveRejeitarPaginaExcessiva() throws Exception {
        mockMvc.perform(get("/api/leads/pagina")
                .param("status", "NOVO")
                .param("page", "10001"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACAO_INVALIDA"));

        verifyNoInteractions(leadService);
    }

    @Test
    void deveExigirStatusNaConsultaPaginada() throws Exception {
        mockMvc.perform(get("/api/leads/pagina"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));

        verifyNoInteractions(leadService);
    }

    @Test
    void deveBuscarLeadPorIdViaHttp() throws Exception {
        when(leadService.buscarPorId(35L)).thenReturn(criarResposta());

        mockMvc.perform(get("/api/leads/35"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(35))
            .andExpect(jsonPath("$.googlePlaceId").value("place-35"))
            .andExpect(jsonPath("$.score").value(95))
            .andExpect(jsonPath("$.temperatura").value("QUENTE"));
    }

    @Test
    void deveAtualizarLeadViaHttp() throws Exception {
        when(leadService.atualizar(eq(35L), any(AtualizarLeadRequest.class)))
            .thenReturn(criarResposta());

        mockMvc.perform(patch("/api/leads/35")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "CONTATADO",
                      "observacoes": "Retornar amanhã",
                      "ultimoContatoEm": "2026-08-22T10:30:00"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value("CONTATADO"))
            .andExpect(jsonPath("$.observacoes").value("Retornar amanhã"));

        ArgumentCaptor<AtualizarLeadRequest> requestCaptor =
            ArgumentCaptor.forClass(AtualizarLeadRequest.class);
        verify(leadService).atualizar(eq(35L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().status()).isEqualTo(StatusFunil.CONTATADO);
        assertThat(requestCaptor.getValue().observacoes()).isEqualTo("Retornar amanhã");
        assertThat(requestCaptor.getValue().ultimoContatoEm())
            .isEqualTo(LocalDateTime.of(2026, 8, 22, 10, 30));
    }

    @Test
    void deveRetornar404QuandoLeadNaoExistir() throws Exception {
        when(leadService.buscarPorId(99L)).thenThrow(new LeadNaoEncontradoException(99L));

        mockMvc.perform(get("/api/leads/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("LEAD_NAO_ENCONTRADO"))
            .andExpect(jsonPath("$.path").value("/api/leads/99"));
    }

    @Test
    void deveRejeitarAtualizacaoSemCampos() throws Exception {
        mockMvc.perform(patch("/api/leads/35")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACAO_INVALIDA"))
            .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(leadService);
    }

    @Test
    void deveRejeitarFiltroDeStatusInvalido() throws Exception {
        mockMvc.perform(get("/api/leads").param("status", "STATUS_INEXISTENTE"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"))
            .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(leadService);
    }

    private LeadResponse criarResposta() {
        return new LeadResponse(
            35L,
            "place-35",
            "Padaria Central",
            CategoriaNegocio.PADARIA,
            "Rua Central, 100",
            "(27) 99999-0000",
            "5527999990000",
            "https://wa.me/5527999990000",
            new BigDecimal("-20.3155"),
            new BigDecimal("-40.3128"),
            new BigDecimal("4.8"),
            120,
            95,
            Temperatura.QUENTE,
            StatusFunil.CONTATADO,
            "Retornar amanhã",
            LocalDateTime.of(2026, 8, 22, 10, 30),
            LocalDateTime.of(2026, 8, 20, 9, 0),
            LocalDateTime.of(2026, 8, 22, 10, 30)
        );
    }
}
