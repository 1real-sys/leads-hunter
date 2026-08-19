package dev.jlm.leadshunter.busca;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import dev.jlm.leadshunter.lead.StatusFunil;
import dev.jlm.leadshunter.lead.Temperatura;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BuscaControllerTest {

    @Mock
    private BuscaService buscaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BuscaController(buscaService)).build();
    }

    @Test
    void deveListarHistoricoViaHttp() throws Exception {
        when(buscaService.listarHistorico()).thenReturn(List.of(
            new BuscaResumoResponse(
                22L,
                "Centro de Vitória",
                new BigDecimal("-20.3155"),
                new BigDecimal("-40.3128"),
                5,
                List.of(CategoriaNegocio.PADARIA),
                18,
                LocalDateTime.of(2026, 8, 18, 11, 0)
            )
        ));

        mockMvc.perform(get("/api/buscas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(22))
            .andExpect(jsonPath("$[0].enderecoBase").value("Centro de Vitória"))
            .andExpect(jsonPath("$[0].categorias[0]").value("PADARIA"))
            .andExpect(jsonPath("$[0].totalEncontrados").value(18));
    }

    @Test
    void deveBuscarDetalheHistoricoViaHttp() throws Exception {
        BuscaDetalheResponse resposta = new BuscaDetalheResponse(
            22L,
            "Centro de Vitória",
            new BigDecimal("-20.3155"),
            new BigDecimal("-40.3128"),
            5,
            List.of(CategoriaNegocio.PADARIA),
            1,
            LocalDateTime.of(2026, 8, 18, 11, 0),
            List.of(new BuscaDetalheResponse.LeadHistoricoResponse(
                35L,
                "Padaria Central",
                CategoriaNegocio.PADARIA,
                "Rua Sete, 100",
                "(27) 99999-0000",
                "https://wa.me/5527999990000",
                55,
                Temperatura.MORNO,
                StatusFunil.CONTATADO,
                "Retornar amanhã",
                LocalDateTime.of(2026, 8, 18, 14, 0)
            ))
        );
        when(buscaService.buscarHistoricoPorId(22L)).thenReturn(resposta);

        mockMvc.perform(get("/api/buscas/22"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(22))
            .andExpect(jsonPath("$.leads[0].id").value(35))
            .andExpect(jsonPath("$.leads[0].scoreNaBusca").value(55))
            .andExpect(jsonPath("$.leads[0].temperaturaNaBusca").value("MORNO"))
            .andExpect(jsonPath("$.leads[0].status").value("CONTATADO"))
            .andExpect(jsonPath("$.leads[0].whatsappUrl")
                .value("https://wa.me/5527999990000"));
    }

    @Test
    void deveRetornar404QuandoBuscaNaoExistir() throws Exception {
        when(buscaService.buscarHistoricoPorId(99L))
            .thenThrow(new BuscaNaoEncontradaException(99L));

        mockMvc.perform(get("/api/buscas/99"))
            .andExpect(status().isNotFound());
    }
}
