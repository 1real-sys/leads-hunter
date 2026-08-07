package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuscaService {

    private final BuscaRepository buscaRepository;

    public BuscaService(BuscaRepository buscaRepository) {
        this.buscaRepository = buscaRepository;
    }

    @Transactional
    public BuscaResponse criar(BuscaRequest request) {
        Busca busca = new Busca();
        busca.setEnderecoBase(request.enderecoBase());
        busca.setLatitude(request.latitude());
        busca.setLongitude(request.longitude());
        busca.setRaioKm(request.raioKm());
        busca.setCategoriasBuscadas(serializarCategorias(request.categorias()));
        busca.setTotalEncontrados(0);

        Busca buscaSalva = buscaRepository.saveAndFlush(busca);

        return new BuscaResponse(
            buscaSalva.getId(),
            buscaSalva.getEnderecoBase(),
            buscaSalva.getLatitude(),
            buscaSalva.getLongitude(),
            buscaSalva.getRaioKm(),
            request.categorias(),
            buscaSalva.getTotalEncontrados(),
            buscaSalva.getCriadoEm(),
            List.of()
        );
    }

    private String serializarCategorias(List<CategoriaNegocio> categorias) {
        return categorias.stream()
            .map(CategoriaNegocio::name)
            .collect(Collectors.joining(","));
    }
}
