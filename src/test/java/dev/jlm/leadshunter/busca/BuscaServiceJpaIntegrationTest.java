package dev.jlm.leadshunter.busca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.jlm.leadshunter.bloqueio.NomeBloqueadoService;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @Autowired
    private NomeBloqueadoService nomeBloqueadoService;

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
        assertThat(leadPersistido.getMunicipioCodigoIbge()).isEqualTo("4106902");
        assertThat(leadPersistido.getMunicipioNome()).isEqualTo("Curitiba");
        assertThat(leadPersistido.getUf()).isEqualTo("PR");
        assertThat(leadPersistido.getIdhm()).isEqualByComparingTo("0.823");
        assertThat(leadPersistido.getIdhmReferencia()).isEqualTo((short) 2010);
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

    @Test
    void deveBloquearNomeDepoisDaDeduplicacaoEPreservarTotalBrutoNoHistorico() {
        nomeBloqueadoService.cadastrar("Supermercados BH");
        PlacesSearchResponse.PlaceResult bloqueado = primeiroPlace("Supermercados BH Centro");
        PlacesSearchResponse.PlaceResult bloqueadoDuplicado = primeiroPlace(
            "Supermercados BH Centro duplicado"
        );
        PlacesSearchResponse.PlaceResult permitido = segundoPlace();
        when(placesApiClient.buscarProximos(any())).thenReturn(new PlacesSearchResponse(List.of(
            bloqueado,
            bloqueadoDuplicado,
            permitido
        )));

        BuscaResponse resposta = buscaService.criar(
            criarRequest("Centro com bloqueio", "-20.3155", "-40.3128")
        );
        BuscaDetalheResponse historico = buscaService.buscarHistoricoPorId(resposta.id());

        assertThat(resposta.totalEncontrados()).isEqualTo(3);
        assertThat(resposta.totalBloqueados()).isEqualTo(1);
        assertThat(resposta.leads()).singleElement()
            .extracting(BuscaResponse.LeadEncontradoResponse::nome)
            .isEqualTo("Restaurante Secundário");
        assertThat(leadRepository.findByGooglePlaceId(PRIMEIRO_PLACE_ID)).isEmpty();
        assertThat(buscaLeadRepository.findByBuscaIdOrderByScoreNaBuscaDesc(resposta.id()))
            .hasSize(1);
        assertThat(historico.totalEncontrados()).isEqualTo(3);
        assertThat(historico.leads()).hasSize(1);
    }

    @Test
    void deveAssociarCnpjsDistintosAsUnidadesDeVitoriaEVilaVelha() {
        PlacesSearchResponse.PlaceResult vitoria = cocoBambu(
            "cnpj-jpa-vitoria",
            "Coco Bambu Vitória",
            "-20.2976",
            "-40.2958",
            "29055620",
            "Rua João da Cruz",
            "10",
            "Praia do Canto"
        );
        PlacesSearchResponse.PlaceResult vilaVelha = cocoBambu(
            "cnpj-jpa-vila-velha",
            "Coco Bambu Vila Velha",
            "-20.3402",
            "-40.2884",
            "29101950",
            "Avenida Doutor Olívio Lira",
            "353",
            "Praia da Costa"
        );
        when(placesApiClient.buscarProximos(any()))
            .thenReturn(new PlacesSearchResponse(List.of(vitoria)))
            .thenReturn(new PlacesSearchResponse(List.of(vilaVelha)));

        buscaService.criar(criarRequest("Vitória", "-20.2976", "-40.2958"));
        buscaService.criar(criarRequest("Vila Velha", "-20.3402", "-40.2884"));

        Lead leadVitoria = leadRepository.findByGooglePlaceId("cnpj-jpa-vitoria").orElseThrow();
        Lead leadVilaVelha = leadRepository.findByGooglePlaceId("cnpj-jpa-vila-velha")
            .orElseThrow();
        assertThat(leadVitoria.getMunicipioCodigoIbge()).isEqualTo("3205309");
        assertThat(leadVitoria.getCnpj()).isEqualTo("43869215000156");
        assertThat(leadVitoria.getRazaoSocial())
            .isEqualTo("CB VITORIA COMERCIO DE ALIMENTOS LTDA");
        assertThat(leadVitoria.getCnpjCorrespondidoEm()).isNotNull();
        assertThat(leadVilaVelha.getMunicipioCodigoIbge()).isEqualTo("3205200");
        assertThat(leadVilaVelha.getCnpj()).isEqualTo("23681920000118");
        assertThat(leadVilaVelha.getRazaoSocial())
            .isEqualTo("CB VILA VELHA COMERCIO DE ALIMENTOS LTDA");
        assertThat(leadVilaVelha.getCnpjCorrespondidoEm()).isNotNull();
        assertThat(leadVitoria.getCnpj()).isNotEqualTo(leadVilaVelha.getCnpj());
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
            List.of("bakery"),
            new PlacesSearchResponse.EnderecoEstruturado(
                "80420063",
                "Rua Principal",
                "100",
                "Centro"
            )
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

    private PlacesSearchResponse.PlaceResult cocoBambu(
        String placeId,
        String nome,
        String latitude,
        String longitude,
        String cep,
        String logradouro,
        String numero,
        String bairro
    ) {
        return new PlacesSearchResponse.PlaceResult(
            placeId,
            nome,
            CategoriaNegocio.RESTAURANTE,
            logradouro + ", " + numero,
            null,
            new BigDecimal(latitude),
            new BigDecimal(longitude),
            new BigDecimal("4.8"),
            100,
            "OPERATIONAL",
            List.of("restaurant"),
            new PlacesSearchResponse.EnderecoEstruturado(
                cep,
                logradouro,
                numero,
                bairro
            )
        );
    }
}
