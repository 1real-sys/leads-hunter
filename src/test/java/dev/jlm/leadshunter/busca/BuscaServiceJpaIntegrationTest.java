package dev.jlm.leadshunter.busca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.jlm.leadshunter.integracao.places.PlacesApiClient;
import dev.jlm.leadshunter.integracao.places.PlacesSearchResponse;
import dev.jlm.leadshunter.lead.CategoriaNegocio;
import dev.jlm.leadshunter.lead.Lead;
import dev.jlm.leadshunter.lead.LeadRepository;
import dev.jlm.leadshunter.lead.StatusFunil;
import dev.jlm.leadshunter.lead.Temperatura;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BuscaServiceJpaIntegrationTest {

    private static final String PRIMEIRO_PLACE_ID = "jpa-integration-place-001";
    private static final String SEGUNDO_PLACE_ID = "jpa-integration-place-002";

    @Autowired
    private BuscaService buscaService;

    @Autowired
    private BuscaRepository buscaRepository;

    @Autowired
    private BuscaLeadRepository buscaLeadRepository;

    @Autowired
    private LeadRepository leadRepository;

    @MockitoBean
    private PlacesApiClient placesApiClient;

    @Test
    void devePersistirRelacionamentoNNPreservarDadosComerciaisESalvarSnapshotHistorico() {
        when(placesApiClient.buscarProximos(any()))
            .thenReturn(new PlacesSearchResponse(List.of(primeiroPlace("Nome inicial"))))
            .thenReturn(new PlacesSearchResponse(List.of(
                primeiroPlace("Nome atualizado"),
                segundoPlace()
            )));

        BuscaResponse primeiraResposta = buscaService.criar(criarRequest("Centro 1", "-25.4284", "-49.2733"));
        Lead leadExistente = leadRepository.findByGooglePlaceId(PRIMEIRO_PLACE_ID).orElseThrow();
        leadExistente.setStatus(StatusFunil.CONTATADO);
        leadExistente.setObservacoes("Retornar na sexta");
        leadExistente.setUltimoContatoEm(LocalDateTime.of(2026, 8, 20, 14, 30));
        leadRepository.saveAndFlush(leadExistente);

        BuscaResponse segundaResposta = buscaService.criar(
            criarRequest("Centro 2", "-25.4384", "-49.2833")
        );

        Lead leadPersistido = leadRepository.findByGooglePlaceId(PRIMEIRO_PLACE_ID).orElseThrow();
        List<BuscaLead> vinculosDoLead = buscaLeadRepository.findByLeadId(leadPersistido.getId());
        List<BuscaLead> vinculosDaSegundaBusca = buscaLeadRepository
            .findByBuscaIdOrderByScoreNaBuscaDesc(segundaResposta.id());
        BuscaDetalheResponse historico = buscaService.buscarHistoricoPorId(segundaResposta.id());

        assertThat(primeiraResposta.leads()).hasSize(1);
        assertThat(segundaResposta.leads()).hasSize(2);
        assertThat(leadRepository.findByGooglePlaceId(SEGUNDO_PLACE_ID)).isPresent();
        assertThat(vinculosDoLead).hasSize(2);
        assertThat(vinculosDaSegundaBusca).hasSize(2);
        assertThat(vinculosDaSegundaBusca)
            .extracting(BuscaLead::getScoreNaBusca)
            .isSortedAccordingTo(java.util.Comparator.reverseOrder());

        assertThat(leadPersistido.getNome()).isEqualTo("Nome atualizado");
        assertThat(leadPersistido.getStatus()).isEqualTo(StatusFunil.CONTATADO);
        assertThat(leadPersistido.getObservacoes()).isEqualTo("Retornar na sexta");
        assertThat(leadPersistido.getUltimoContatoEm())
            .isEqualTo(LocalDateTime.of(2026, 8, 20, 14, 30));
        assertThat(historico.leads()).hasSize(2);
        assertThat(historico.leads().getFirst().scoreNaBusca())
            .isGreaterThanOrEqualTo(historico.leads().get(1).scoreNaBusca());
        assertThat(buscaRepository.findById(primeiraResposta.id())).isPresent();
    }

    @Test
    void deveImpedirDuplicacaoDeGooglePlaceIdNoBanco() {
        Lead primeiro = new Lead();
        primeiro.setGooglePlaceId("jpa-integration-unique-place");
        primeiro.setStatus(StatusFunil.NOVO);
        leadRepository.saveAndFlush(primeiro);

        Lead duplicado = new Lead();
        duplicado.setGooglePlaceId("jpa-integration-unique-place");
        duplicado.setStatus(StatusFunil.NOVO);

        assertThatThrownBy(() -> leadRepository.saveAndFlush(duplicado))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private BuscaRequest criarRequest(String endereco, String latitude, String longitude) {
        return new BuscaRequest(
            endereco,
            new BigDecimal(latitude),
            new BigDecimal(longitude),
            5,
            List.of(CategoriaNegocio.PADARIA)
        );
    }

    private PlacesSearchResponse.PlaceResult primeiroPlace(String nome) {
        return new PlacesSearchResponse.PlaceResult(
            PRIMEIRO_PLACE_ID,
            nome,
            CategoriaNegocio.PADARIA,
            "Rua Principal, 100",
            "(41) 99999-0000",
            new BigDecimal("-25.4284"),
            new BigDecimal("-49.2733"),
            new BigDecimal("4.8"),
            120,
            "OPERATIONAL",
            List.of("bakery")
        );
    }

    private PlacesSearchResponse.PlaceResult segundoPlace() {
        return new PlacesSearchResponse.PlaceResult(
            SEGUNDO_PLACE_ID,
            "Restaurante Secundário",
            CategoriaNegocio.RESTAURANTE,
            "Rua Secundária, 200",
            null,
            new BigDecimal("-25.4384"),
            new BigDecimal("-49.2833"),
            new BigDecimal("3.0"),
            5,
            "CLOSED",
            List.of("restaurant")
        );
    }
}
