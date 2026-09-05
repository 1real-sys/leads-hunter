package dev.jlm.leadshunter.lead;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @GetMapping
    public List<LeadResponse> listar(
        @RequestParam(required = false) StatusFunil status,
        @RequestParam(required = false) CategoriaNegocio categoria,
        @RequestParam(required = false) Temperatura temperatura
    ) {
        return leadService.listar(status, categoria, temperatura);
    }

    @GetMapping("/pagina")
    public PaginaLeadsResponse listarPagina(
        @RequestParam StatusFunil status,
        @RequestParam(required = false) CategoriaNegocio categoria,
        @RequestParam(required = false) Temperatura temperatura,
        @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
        @RequestParam(defaultValue = "25") @Min(1) @Max(25) int size
    ) {
        return leadService.listarPagina(status, categoria, temperatura, page, size);
    }

    @GetMapping("/{id}")
    public LeadResponse buscarPorId(@PathVariable Long id) {
        return leadService.buscarPorId(id);
    }

    @PatchMapping("/{id}")
    public LeadResponse atualizar(
        @PathVariable Long id,
        @Valid @RequestBody AtualizarLeadRequest request
    ) {
        return leadService.atualizar(id, request);
    }
}
