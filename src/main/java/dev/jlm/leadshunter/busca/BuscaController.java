package dev.jlm.leadshunter.busca;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
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
    private final BuscaCnpjService buscaCnpjService;
    private final BuscaInformacoesExecucaoService buscaInformacoesExecucaoService;

    @PostMapping("/{id}/informacoes")
    public ResponseEntity<PesquisaInformacoesExecucaoResponse> buscarInformacoes(
        @PathVariable @Positive Long id,
        @RequestBody(required = false) BuscaInformacoesRequest request
    ) {
        var execucao = request == null
            ? buscaInformacoesExecucaoService.iniciar(id)
            : buscaInformacoesExecucaoService.iniciar(id, request.deveUsarBrave());
        return ResponseEntity.accepted()
            .cacheControl(CacheControl.noStore())
            .location(URI.create("/api/buscas/" + id + "/informacoes"))
            .body(execucao);
    }

    @GetMapping("/{id}/informacoes")
    public ResponseEntity<PesquisaInformacoesExecucaoResponse> consultarInformacoes(
        @PathVariable @Positive Long id
    ) {
        return buscaInformacoesExecucaoService.consultar(id)
            .map(resposta -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta))
            .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }

    @PostMapping("/{id}/cnpj")
    public BuscaCnpjResponse buscarCnpj(@PathVariable Long id) {
        return buscaCnpjService.buscarCnpj(id);
    }

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
