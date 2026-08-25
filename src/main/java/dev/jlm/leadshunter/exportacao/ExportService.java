package dev.jlm.leadshunter.exportacao;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import dev.jlm.leadshunter.lead.LeadResponse;
import dev.jlm.leadshunter.lead.LeadService;
import dev.jlm.leadshunter.lead.StatusFunil;
import dev.jlm.leadshunter.lead.Temperatura;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExportService {

    private static final List<String> COLUNAS = List.of(
        "id",
        "googlePlaceId",
        "nome",
        "categoria",
        "enderecoFormatado",
        "telefone",
        "telefoneNormalizado",
        "whatsappUrl",
        "latitude",
        "longitude",
        "ratingGoogle",
        "totalReviews",
        "score",
        "temperatura",
        "status",
        "observacoes",
        "ultimoContatoEm",
        "criadoEm",
        "atualizadoEm"
    );

    private static final String CABECALHO = String.join(",", COLUNAS);

    private final LeadService leadService;

    @Transactional(readOnly = true)
    public byte[] exportarLeads(
        StatusFunil status,
        CategoriaNegocio categoria,
        Temperatura temperatura
    ) {
        List<LeadResponse> leads = listarLeads(status, categoria, temperatura);
        StringBuilder csv = new StringBuilder(CABECALHO).append("\r\n");

        for (LeadResponse lead : leads) {
            csv.append(String.join(",", Arrays.stream(valores(lead))
                .map(this::valor)
                .toList())).append("\r\n");
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportarLeadsExcel(
        StatusFunil status,
        CategoriaNegocio categoria,
        Temperatura temperatura
    ) {
        List<LeadResponse> leads = listarLeads(status, categoria, temperatura);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Leads");
            CellStyle cabecalhoStyle = criarEstiloCabecalho(workbook);
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setDataFormat(workbook.getCreationHelper()
                .createDataFormat().getFormat("yyyy-mm-dd hh:mm"));

            Row cabecalho = sheet.createRow(0);
            for (int coluna = 0; coluna < COLUNAS.size(); coluna++) {
                Cell cell = cabecalho.createCell(coluna);
                cell.setCellValue(COLUNAS.get(coluna));
                cell.setCellStyle(cabecalhoStyle);
            }

            for (int linha = 0; linha < leads.size(); linha++) {
                Row row = sheet.createRow(linha + 1);
                Object[] valores = valores(leads.get(linha));
                for (int coluna = 0; coluna < valores.length; coluna++) {
                    preencherCelula(row.createCell(coluna), valores[coluna], dataStyle);
                }
            }

            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(
                0,
                leads.size(),
                0,
                COLUNAS.size() - 1
            ));
            for (int coluna = 0; coluna < COLUNAS.size(); coluna++) {
                sheet.autoSizeColumn(coluna);
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível gerar o arquivo Excel.", exception);
        }
    }

    private List<LeadResponse> listarLeads(
        StatusFunil status,
        CategoriaNegocio categoria,
        Temperatura temperatura
    ) {
        return leadService.listar(status, categoria, temperatura);
    }

    private CellStyle criarEstiloCabecalho(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        var font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void preencherCelula(Cell cell, Object valor, CellStyle dataStyle) {
        if (valor == null) {
            return;
        }
        if (valor instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (valor instanceof LocalDateTime data) {
            cell.setCellValue(java.util.Date.from(
                data.atZone(ZoneId.systemDefault()).toInstant()
            ));
            cell.setCellStyle(dataStyle);
        } else if (valor instanceof Enum<?> enumValue) {
            cell.setCellValue(enumValue.name());
        } else {
            cell.setCellValue(valor.toString());
        }
    }

    private Object[] valores(LeadResponse lead) {
        return new Object[] {
            lead.id(),
            lead.googlePlaceId(),
            lead.nome(),
            lead.categoria(),
            lead.enderecoFormatado(),
            lead.telefone(),
            lead.telefoneNormalizado(),
            lead.whatsappUrl(),
            lead.latitude(),
            lead.longitude(),
            lead.ratingGoogle(),
            lead.totalReviews(),
            lead.score(),
            lead.temperatura(),
            lead.status(),
            lead.observacoes(),
            lead.ultimoContatoEm(),
            lead.criadoEm(),
            lead.atualizadoEm()
        };
    }

    private String valor(Object valor) {
        if (valor == null) {
            return "";
        }
        String texto = valor instanceof Enum<?> enumValue
            ? enumValue.name()
            : valor.toString();
        if (texto.indexOf(',') >= 0
            || texto.indexOf('"') >= 0
            || texto.indexOf('\r') >= 0
            || texto.indexOf('\n') >= 0) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }
}
