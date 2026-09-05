package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.geo.MunicipioInfo;
import dev.jlm.leadshunter.geo.MunicipioService;
import dev.jlm.leadshunter.integracao.places.PlacesApiClient;
import dev.jlm.leadshunter.integracao.places.PlacesSearchRequest;
import dev.jlm.leadshunter.integracao.places.PlacesSearchResponse;
import dev.jlm.leadshunter.lead.CategoriaNegocio;
import dev.jlm.leadshunter.lead.Lead;
import dev.jlm.leadshunter.lead.LeadRepository;
import dev.jlm.leadshunter.lead.StatusFunil;
import dev.jlm.leadshunter.lead.TelefoneNormalizer;
import dev.jlm.leadshunter.lead.Temperatura;
import dev.jlm.leadshunter.lead.WhatsAppLinkGenerator;
import dev.jlm.leadshunter.scoring.ScoringService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuscaService {

    private final BuscaRepository buscaRepository;
    private final BuscaLeadRepository buscaLeadRepository;
    private final LeadRepository leadRepository;
    private final MunicipioService municipioService;
    private final PlacesApiClient placesApiClient;
    private final TelefoneNormalizer telefoneNormalizer;
    private final ScoringService scoringService;
    private final BuscaPlacesCache buscaPlacesCache;
    private final WhatsAppLinkGenerator whatsAppLinkGenerator;

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

    @Transactional(readOnly = true)
    public List<BuscaResumoResponse> listarHistorico() {
        return buscaRepository.findAllByOrderByCriadoEmDesc().stream()
            .map(this::toResumoResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public BuscaDetalheResponse buscarHistoricoPorId(Long id) {
        Busca busca = buscaRepository.findById(id)
            .orElseThrow(() -> new BuscaNaoEncontradaException(id));
        List<BuscaDetalheResponse.LeadHistoricoResponse> leads = buscaLeadRepository
            .findByBuscaIdOrderByScoreNaBuscaDesc(id).stream()
            .map(this::toLeadHistoricoResponse)
            .toList();

        return new BuscaDetalheResponse(
            busca.getId(),
            busca.getEnderecoBase(),
            busca.getLatitude(),
            busca.getLongitude(),
            busca.getRaioKm(),
            deserializarCategorias(busca.getCategoriasBuscadas()),
            busca.getTotalEncontrados(),
            busca.getCriadoEm(),
            leads
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
        atualizarGeografia(lead);
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

    private void atualizarGeografia(Lead lead) {
        if (lead.getLatitude() == null || lead.getLongitude() == null) {
            limparGeografia(lead);
            return;
        }

        Optional<MunicipioInfo> municipio = municipioService.localizar(
            lead.getLatitude(),
            lead.getLongitude()
        );
        if (municipio.isEmpty()) {
            limparGeografia(lead);
            return;
        }

        MunicipioInfo encontrado = municipio.get();
        lead.setMunicipioCodigoIbge(encontrado.codigoIbge());
        lead.setMunicipioNome(encontrado.nome());
        lead.setUf(encontrado.uf());
        lead.setIdhm(encontrado.idhm());
        lead.setIdhmReferencia(encontrado.idhmReferencia());
    }

    private void limparGeografia(Lead lead) {
        lead.setMunicipioCodigoIbge(null);
        lead.setMunicipioNome(null);
        lead.setUf(null);
        lead.setIdhm(null);
        lead.setIdhmReferencia(null);
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

    private List<CategoriaNegocio> deserializarCategorias(String categorias) {
        if (categorias == null || categorias.isBlank()) {
            return List.of();
        }
        return List.of(categorias.split(",")).stream()
            .map(CategoriaNegocio::valueOf)
            .toList();
    }

    private BuscaResumoResponse toResumoResponse(Busca busca) {
        return new BuscaResumoResponse(
            busca.getId(),
            busca.getEnderecoBase(),
            busca.getLatitude(),
            busca.getLongitude(),
            busca.getRaioKm(),
            deserializarCategorias(busca.getCategoriasBuscadas()),
            busca.getTotalEncontrados(),
            busca.getCriadoEm()
        );
    }

    private BuscaDetalheResponse.LeadHistoricoResponse toLeadHistoricoResponse(
        BuscaLead buscaLead
    ) {
        Lead lead = buscaLead.getLead();
        return new BuscaDetalheResponse.LeadHistoricoResponse(
            lead.getId(),
            lead.getNome(),
            lead.getCategoria(),
            lead.getEnderecoFormatado(),
            lead.getTelefone(),
            whatsAppLinkGenerator.gerar(lead.getTelefoneNormalizado()),
            buscaLead.getScoreNaBusca(),
            toTemperatura(buscaLead.getTemperaturaNaBusca()),
            lead.getStatus(),
            lead.getObservacoes(),
            lead.getUltimoContatoEm()
        );
    }

    private Temperatura toTemperatura(String valor) {
        return valor == null ? null : Temperatura.valueOf(valor);
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
                whatsAppLinkGenerator.gerar(lead.getTelefoneNormalizado()),
                lead.getScore(),
                lead.getTemperatura() != null ? lead.getTemperatura().name() : null
            ))
            .toList();
    }
}
