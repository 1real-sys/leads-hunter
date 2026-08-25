package dev.jlm.leadshunter.busca;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/buscas")
@RequiredArgsConstructor
public class BuscaController {

    private final BuscaService buscaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BuscaResponse criar(@Valid @RequestBody BuscaRequest request) {
        return buscaService.criar(request);
    }

    @GetMapping
    public List<BuscaResumoResponse> listar() {
        return buscaService.listarHistorico();
    }

    @GetMapping("/{id}")
    public BuscaDetalheResponse buscarPorId(@PathVariable Long id) {
        return buscaService.buscarHistoricoPorId(id);
    }
}
