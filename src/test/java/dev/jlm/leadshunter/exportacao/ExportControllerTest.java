package dev.jlm.leadshunter.exportacao;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import dev.jlm.leadshunter.lead.StatusFunil;
import dev.jlm.leadshunter.lead.Temperatura;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ExportControllerTest {

    @Mock
    private ExportService exportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExportController(exportService)).build();
    }

    @Test
    void deveRetornarArquivoCsvComFiltrosEHeadersDeDownload() throws Exception {
        byte[] csv = "id,nome\r\n1,Padaria Central\r\n".getBytes(StandardCharsets.UTF_8);
        when(exportService.exportarLeads(
            StatusFunil.CONTATADO,
            CategoriaNegocio.PADARIA,
            Temperatura.QUENTE
        )).thenReturn(csv);

        mockMvc.perform(get("/api/exportacao/leads.csv")
                .param("status", "CONTATADO")
                .param("categoria", "PADARIA")
                .param("temperatura", "QUENTE"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/csv;charset=UTF-8"))
            .andExpect(header().string(
                "Content-Disposition",
                containsString("attachment; filename=\"leads.csv\"")
            ))
            .andExpect(content().bytes(csv));
    }

    @Test
    void deveAceitarExportacaoSemFiltros() throws Exception {
        byte[] csv = "id,nome\r\n".getBytes(StandardCharsets.UTF_8);
        when(exportService.exportarLeads(null, null, null)).thenReturn(csv);

        mockMvc.perform(get("/api/exportacao/leads.csv"))
            .andExpect(status().isOk())
            .andExpect(content().bytes(csv));
    }

    @Test
    void deveRetornarArquivoExcelComHeaderDeDownload() throws Exception {
        byte[] excel = new byte[] { 0x50, 0x4b, 0x03, 0x04 };
        when(exportService.exportarLeadsExcel(null, null, null)).thenReturn(excel);

        mockMvc.perform(get("/api/exportacao/leads.xlsx"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ))
            .andExpect(header().string(
                "Content-Disposition",
                containsString("attachment; filename=\"leads.xlsx\"")
            ))
            .andExpect(content().bytes(excel));
    }
}
