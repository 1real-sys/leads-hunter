package dev.jlm.leadshunter.exportacao;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import dev.jlm.leadshunter.lead.StatusFunil;
import dev.jlm.leadshunter.lead.Temperatura;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/exportacao")
@RequiredArgsConstructor
public class ExportController {

    private static final MediaType CSV_MEDIA_TYPE = new MediaType(
        "text",
        "csv",
        StandardCharsets.UTF_8
    );
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final ExportService exportService;

    @GetMapping(value = "/leads.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportarLeads(
        @RequestParam(required = false) StatusFunil status,
        @RequestParam(required = false) CategoriaNegocio categoria,
        @RequestParam(required = false) Temperatura temperatura
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(CSV_MEDIA_TYPE);
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename("leads.csv", StandardCharsets.UTF_8)
            .build());
        return ResponseEntity.ok()
            .headers(headers)
            .body(exportService.exportarLeads(status, categoria, temperatura));
    }

    @GetMapping(
        value = "/leads.xlsx",
        produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    public ResponseEntity<byte[]> exportarLeadsExcel(
        @RequestParam(required = false) StatusFunil status,
        @RequestParam(required = false) CategoriaNegocio categoria,
        @RequestParam(required = false) Temperatura temperatura
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX_MEDIA_TYPE);
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename("leads.xlsx", StandardCharsets.UTF_8)
            .build());
        return ResponseEntity.ok()
            .headers(headers)
            .body(exportService.exportarLeadsExcel(status, categoria, temperatura));
    }
}
