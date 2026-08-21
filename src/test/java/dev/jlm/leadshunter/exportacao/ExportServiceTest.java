package dev.jlm.leadshunter.exportacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import dev.jlm.leadshunter.lead.LeadResponse;
import dev.jlm.leadshunter.lead.LeadService;
import dev.jlm.leadshunter.lead.StatusFunil;
import dev.jlm.leadshunter.lead.Temperatura;
import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private LeadService leadService;

    @Test
    void deveExportarLeadsComCabecalhoDadosEFiltros() {
        LocalDateTime contato = LocalDateTime.of(2026, 8, 20, 10, 30);
        LeadResponse lead = new LeadResponse(
            15L,
            "place-15",
            "Padaria, \"Central\"",
            CategoriaNegocio.PADARIA,
            "Rua Um\n100",
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
            "Retornar, amanhã",
            contato,
            LocalDateTime.of(2026, 8, 19, 9, 0),
            LocalDateTime.of(2026, 8, 20, 10, 30)
        );
        when(leadService.listar(
            StatusFunil.CONTATADO,
            CategoriaNegocio.PADARIA,
            Temperatura.QUENTE
        )).thenReturn(List.of(lead));

        byte[] arquivo = criarService().exportarLeads(
            StatusFunil.CONTATADO,
            CategoriaNegocio.PADARIA,
            Temperatura.QUENTE
        );

        String csv = new String(arquivo, StandardCharsets.UTF_8);
        assertThat(csv).isEqualTo(
            "id,googlePlaceId,nome,categoria,enderecoFormatado,telefone,"
                + "telefoneNormalizado,whatsappUrl,latitude,longitude,ratingGoogle,"
                + "totalReviews,score,temperatura,status,observacoes,ultimoContatoEm,"
                + "criadoEm,atualizadoEm\r\n"
                + "15,place-15,\"Padaria, \"\"Central\"\"\",PADARIA,"
                + "\"Rua Um\n100\",(27) 99999-0000,5527999990000,"
                + "https://wa.me/5527999990000,-20.3155,-40.3128,4.8,120,95,"
                + "QUENTE,CONTATADO,\"Retornar, amanhã\",2026-08-20T10:30,"
                + "2026-08-19T09:00,2026-08-20T10:30\r\n"
        );
        verify(leadService).listar(
            StatusFunil.CONTATADO,
            CategoriaNegocio.PADARIA,
            Temperatura.QUENTE
        );
    }

    @Test
    void deveExportarApenasCabecalhoQuandoNaoHouverLeads() {
        when(leadService.listar(null, null, null)).thenReturn(List.of());

        String csv = new String(criarService().exportarLeads(null, null, null), StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo(
            "id,googlePlaceId,nome,categoria,enderecoFormatado,telefone,"
                + "telefoneNormalizado,whatsappUrl,latitude,longitude,ratingGoogle,"
                + "totalReviews,score,temperatura,status,observacoes,ultimoContatoEm,"
                + "criadoEm,atualizadoEm\r\n"
        );
    }

    @Test
    void deveGerarPlanilhaExcelComDadosTipadosERecursosDeUsabilidade() throws Exception {
        LeadResponse lead = new LeadResponse(
            15L,
            "place-15",
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
            "Retornar na sexta",
            LocalDateTime.of(2026, 8, 20, 10, 30),
            LocalDateTime.of(2026, 8, 19, 9, 0),
            LocalDateTime.of(2026, 8, 20, 10, 30)
        );
        when(leadService.listar(null, CategoriaNegocio.PADARIA, null))
            .thenReturn(List.of(lead));

        byte[] arquivo = criarService().exportarLeadsExcel(null, CategoriaNegocio.PADARIA, null);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(arquivo))) {
            var sheet = workbook.getSheet("Leads");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("id");
            assertThat(workbook.getFontAt(
                sheet.getRow(0).getCell(0).getCellStyle().getFontIndex()
            ).getBold())
                .isTrue();
            assertThat(sheet.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(15D);
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue())
                .isEqualTo("Padaria Central");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("PADARIA");
            assertThat(sheet.getRow(1).getCell(12).getNumericCellValue()).isEqualTo(95D);
            assertThat(sheet.getRow(1).getCell(14).getStringCellValue())
                .isEqualTo("CONTATADO");
            assertThat(DateUtil.isCellDateFormatted(sheet.getRow(1).getCell(16))).isTrue();
        }
        verify(leadService).listar(null, CategoriaNegocio.PADARIA, null);
    }

    private ExportService criarService() {
        return new ExportService(leadService);
    }
}
