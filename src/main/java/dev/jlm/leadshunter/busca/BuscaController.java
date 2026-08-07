package dev.jlm.leadshunter.busca;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/buscas")
public class BuscaController {

    private final BuscaService buscaService;

    public BuscaController(BuscaService buscaService) {
        this.buscaService = buscaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BuscaResponse criar(@Valid @RequestBody BuscaRequest request) {
        return buscaService.criar(request);
    }
}
