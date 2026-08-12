package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.integracao.places.PlacesApiClient;
import dev.jlm.leadshunter.integracao.places.PlacesSearchRequest;
import dev.jlm.leadshunter.integracao.places.PlacesSearchResponse;
import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuscaService {

    private final BuscaRepository buscaRepository;
    private final PlacesApiClient placesApiClient;

    public BuscaService(BuscaRepository buscaRepository, PlacesApiClient placesApiClient) {
        this.buscaRepository = buscaRepository;
        this.placesApiClient = placesApiClient;
    }

    @Transactional
    public BuscaResponse criar(BuscaRequest request) {
        PlacesSearchResponse placesResponse = placesApiClient.buscarProximos(
            new PlacesSearchRequest(
                request.latitude(),
                request.longitude(),
                request.raioKm(),
                request.categorias()
            )
        );

        Busca busca = new Busca();
        busca.setEnderecoBase(request.enderecoBase());
        busca.setLatitude(request.latitude());
        busca.setLongitude(request.longitude());
        busca.setRaioKm(request.raioKm());
        busca.setCategoriasBuscadas(serializarCategorias(request.categorias()));
        busca.setTotalEncontrados(placesResponse.places().size());

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
            toLeadEncontradoResponse(placesResponse)
        );
    }

    private String serializarCategorias(List<CategoriaNegocio> categorias) {
        return categorias.stream()
            .map(CategoriaNegocio::name)
            .collect(Collectors.joining(","));
    }

    private List<BuscaResponse.LeadEncontradoResponse> toLeadEncontradoResponse(
        PlacesSearchResponse placesResponse
    ) {
        return placesResponse.places().stream()
            .map(place -> new BuscaResponse.LeadEncontradoResponse(
                null,
                place.nome(),
                place.categoria(),
                place.enderecoFormatado(),
                null,
                null,
                null
            ))
            .toList();
    }
}
