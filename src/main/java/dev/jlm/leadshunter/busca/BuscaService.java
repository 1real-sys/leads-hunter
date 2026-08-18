package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.integracao.places.PlacesApiClient;
import dev.jlm.leadshunter.integracao.places.PlacesSearchRequest;
import dev.jlm.leadshunter.integracao.places.PlacesSearchResponse;
import dev.jlm.leadshunter.lead.CategoriaNegocio;
import dev.jlm.leadshunter.lead.Lead;
import dev.jlm.leadshunter.lead.LeadRepository;
import dev.jlm.leadshunter.lead.StatusFunil;
import dev.jlm.leadshunter.lead.TelefoneNormalizer;
import dev.jlm.leadshunter.scoring.ScoringService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuscaService {

    private final BuscaRepository buscaRepository;
    private final BuscaLeadRepository buscaLeadRepository;
    private final LeadRepository leadRepository;
    private final PlacesApiClient placesApiClient;
    private final TelefoneNormalizer telefoneNormalizer;
    private final ScoringService scoringService;
    private final BuscaPlacesCache buscaPlacesCache;

    public BuscaService(
        BuscaRepository buscaRepository,
        BuscaLeadRepository buscaLeadRepository,
        LeadRepository leadRepository,
        PlacesApiClient placesApiClient,
        TelefoneNormalizer telefoneNormalizer,
        ScoringService scoringService,
        BuscaPlacesCache buscaPlacesCache
    ) {
        this.buscaRepository = buscaRepository;
        this.buscaLeadRepository = buscaLeadRepository;
        this.leadRepository = leadRepository;
        this.placesApiClient = placesApiClient;
        this.telefoneNormalizer = telefoneNormalizer;
        this.scoringService = scoringService;
        this.buscaPlacesCache = buscaPlacesCache;
    }

    @Transactional
    public BuscaResponse criar(BuscaRequest request) {
        PlacesSearchRequest placesRequest = new PlacesSearchRequest(
            request.latitude(),
            request.longitude(),
            request.raioKm(),
            request.categorias()
        );
        PlacesSearchResponse placesResponse = buscaPlacesCache.buscarOuCarregar(
            BuscaCacheKey.from(request),
            () -> placesApiClient.buscarProximos(placesRequest)
        );

        Busca busca = new Busca();
        busca.setEnderecoBase(request.enderecoBase());
        busca.setLatitude(request.latitude());
        busca.setLongitude(request.longitude());
        busca.setRaioKm(request.raioKm());
        busca.setCategoriasBuscadas(serializarCategorias(request.categorias()));
        busca.setTotalEncontrados(placesResponse.places().size());

        Busca buscaSalva = buscaRepository.saveAndFlush(busca);
        List<Lead> leads = persistirLeads(buscaSalva, placesResponse);

        return new BuscaResponse(
            buscaSalva.getId(),
            buscaSalva.getEnderecoBase(),
            buscaSalva.getLatitude(),
            buscaSalva.getLongitude(),
            buscaSalva.getRaioKm(),
            request.categorias(),
            buscaSalva.getTotalEncontrados(),
            buscaSalva.getCriadoEm(),
            toLeadEncontradoResponse(leads)
        );
    }

    private List<Lead> persistirLeads(Busca busca, PlacesSearchResponse placesResponse) {
        Map<String, PlacesSearchResponse.PlaceResult> placesUnicos = new LinkedHashMap<>();

        for (PlacesSearchResponse.PlaceResult place : placesResponse.places()) {
            if (place.googlePlaceId() == null || place.googlePlaceId().isBlank()) {
                throw new IllegalStateException("Google Places retornou um estabelecimento sem ID.");
            }
            placesUnicos.putIfAbsent(place.googlePlaceId(), place);
        }

        return placesUnicos.values().stream()
            .map(place -> persistirLead(busca, place))
            .toList();
    }

    private Lead persistirLead(Busca busca, PlacesSearchResponse.PlaceResult place) {
        Lead lead = leadRepository.findByGooglePlaceId(place.googlePlaceId())
            .orElseGet(() -> novoLead(place.googlePlaceId()));

        atualizarDadosExternos(lead, place);
        ScoringService.Resultado scoring = scoringService.calcular(
            lead.getCategoria(),
            lead.getTelefoneNormalizado(),
            lead.getTotalReviews(),
            lead.getRatingGoogle(),
            place.businessStatus()
        );
        lead.setScore(scoring.score());
        lead.setTemperatura(scoring.temperatura());
        Lead leadSalvo = leadRepository.save(lead);

        BuscaLead buscaLead = new BuscaLead();
        buscaLead.setBusca(busca);
        buscaLead.setLead(leadSalvo);
        buscaLead.setScoreNaBusca(scoring.score());
        buscaLead.setTemperaturaNaBusca(scoring.temperatura().name());
        buscaLeadRepository.save(buscaLead);

        return leadSalvo;
    }

    private Lead novoLead(String googlePlaceId) {
        Lead lead = new Lead();
        lead.setGooglePlaceId(googlePlaceId);
        lead.setStatus(StatusFunil.NOVO);
        return lead;
    }

    private void atualizarDadosExternos(Lead lead, PlacesSearchResponse.PlaceResult place) {
        atualizarSePresente(place.nome(), lead::setNome);
        atualizarSePresente(place.categoria(), lead::setCategoria);
        atualizarSePresente(place.enderecoFormatado(), lead::setEnderecoFormatado);
        atualizarSePresente(place.latitude(), lead::setLatitude);
        atualizarSePresente(place.longitude(), lead::setLongitude);
        atualizarSePresente(place.ratingGoogle(), lead::setRatingGoogle);
        atualizarSePresente(place.totalReviews(), lead::setTotalReviews);
        atualizarTelefone(lead, place.telefone());
    }

    private void atualizarTelefone(Lead lead, String telefone) {
        String telefoneNormalizado = telefoneNormalizer.normalizar(telefone);
        if (telefoneNormalizado != null) {
            lead.setTelefone(telefone);
            lead.setTelefoneNormalizado(telefoneNormalizado);
        }
    }

    private <T> void atualizarSePresente(T valor, Consumer<T> atualizador) {
        if (valor != null) {
            atualizador.accept(valor);
        }
    }

    private String serializarCategorias(List<CategoriaNegocio> categorias) {
        return categorias.stream()
            .map(CategoriaNegocio::name)
            .collect(Collectors.joining(","));
    }

    private List<BuscaResponse.LeadEncontradoResponse> toLeadEncontradoResponse(
        List<Lead> leads
    ) {
        return leads.stream()
            .map(lead -> new BuscaResponse.LeadEncontradoResponse(
                lead.getId(),
                lead.getNome(),
                lead.getCategoria(),
                lead.getEnderecoFormatado(),
                lead.getTelefone(),
                lead.getScore(),
                lead.getTemperatura() != null ? lead.getTemperatura().name() : null
            ))
            .toList();
    }
}
