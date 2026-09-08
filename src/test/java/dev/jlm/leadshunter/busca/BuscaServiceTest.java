package dev.jlm.leadshunter.busca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import dev.jlm.leadshunter.bloqueio.NomeBloqueadoService;
import dev.jlm.leadshunter.cnpj.CnpjService;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BuscaServiceTest {

    @Mock
    private BuscaRepository buscaRepository;

    @Mock
    private BuscaLeadRepository buscaLeadRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private MunicipioService municipioService;

    @Mock
    private PlacesApiClient placesApiClient;

    @Mock
    private NomeBloqueadoService nomeBloqueadoService;

    @Mock
    private CnpjService cnpjService;

    @Test
    void deveBuscarLocaisPersistirResumoERetornarResultados() {
        BuscaRequest request = new BuscaRequest(
            "Centro, Curitiba - PR",
            new BigDecimal("-25.4284"),
            new BigDecimal("-49.2733"),
            5,
            List.of(CategoriaNegocio.PADARIA, CategoriaNegocio.MERCADO)
        );
        PlacesSearchResponse placesResponse = new PlacesSearchResponse(List.of(
            new PlacesSearchResponse.PlaceResult(
                "place-1",
                "Padaria Central",
                CategoriaNegocio.PADARIA,
                "Rua Central, 100",
                "(41) 3333-4444",
                new BigDecimal("-25.4300"),
                new BigDecimal("-49.2700"),
                new BigDecimal("4.5"),
                120,
                "OPERATIONAL",
                List.of("bakery"),
                new PlacesSearchResponse.EnderecoEstruturado(
                    "80420063",
                    "Rua Central",
                    "100",
                    "Centro"
                )
            )
        ));
        when(placesApiClient.buscarProximos(any(PlacesSearchRequest.class)))
            .thenReturn(placesResponse);
        when(leadRepository.findByGooglePlaceId("place-1")).thenReturn(Optional.empty());
        when(municipioService.localizar(
            new BigDecimal("-25.4300"),
            new BigDecimal("-49.2700")
        )).thenReturn(Optional.of(new MunicipioInfo(
            "4106902",
            "Curitiba",
            "PR",
            new BigDecimal("0.823"),
            (short) 2010
        )));
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> {
            Lead lead = invocation.getArgument(0);
            lead.setId(20L);
            return lead;
        });
        when(buscaRepository.saveAndFlush(any(Busca.class))).thenAnswer(invocation -> {
            Busca busca = invocation.getArgument(0);
            busca.setId(10L);
            busca.setCriadoEm(LocalDateTime.of(2026, 8, 11, 10, 0));
            return busca;
        });

        BuscaResponse response = criarService().criar(request);

        ArgumentCaptor<PlacesSearchRequest> placesRequestCaptor =
            ArgumentCaptor.forClass(PlacesSearchRequest.class);
        verify(placesApiClient).buscarProximos(placesRequestCaptor.capture());
        assertThat(placesRequestCaptor.getValue())
            .usingRecursiveComparison()
            .isEqualTo(new PlacesSearchRequest(
                request.latitude(),
                request.longitude(),
                request.raioKm(),
                request.categorias()
            ));

        ArgumentCaptor<Busca> buscaCaptor = ArgumentCaptor.forClass(Busca.class);
        verify(buscaRepository).saveAndFlush(buscaCaptor.capture());
        assertThat(buscaCaptor.getValue().getCategoriasBuscadas()).isEqualTo("PADARIA,MERCADO");
        assertThat(buscaCaptor.getValue().getTotalEncontrados()).isEqualTo(1);

        ArgumentCaptor<Lead> leadCaptor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(leadCaptor.capture());
        assertThat(leadCaptor.getValue().getGooglePlaceId()).isEqualTo("place-1");
        assertThat(leadCaptor.getValue().getStatus()).isEqualTo(StatusFunil.NOVO);
        assertThat(leadCaptor.getValue().getRatingGoogle()).isEqualByComparingTo("4.5");
        assertThat(leadCaptor.getValue().getTelefone()).isEqualTo("(41) 3333-4444");
        assertThat(leadCaptor.getValue().getTelefoneNormalizado()).isEqualTo("554133334444");
        assertThat(leadCaptor.getValue().getCep()).isEqualTo("80420063");
        assertThat(leadCaptor.getValue().getLogradouro()).isEqualTo("Rua Central");
        assertThat(leadCaptor.getValue().getNumero()).isEqualTo("100");
        assertThat(leadCaptor.getValue().getBairro()).isEqualTo("Centro");
        assertThat(leadCaptor.getValue().getMunicipioCodigoIbge()).isEqualTo("4106902");
        assertThat(leadCaptor.getValue().getMunicipioNome()).isEqualTo("Curitiba");
        assertThat(leadCaptor.getValue().getUf()).isEqualTo("PR");
        assertThat(leadCaptor.getValue().getIdhm()).isEqualByComparingTo("0.823");
        assertThat(leadCaptor.getValue().getIdhmReferencia()).isEqualTo((short) 2010);
        assertThat(leadCaptor.getValue().getScore()).isEqualTo(95);
        assertThat(leadCaptor.getValue().getTemperatura()).isEqualTo(Temperatura.QUENTE);

        ArgumentCaptor<BuscaLead> buscaLeadCaptor = ArgumentCaptor.forClass(BuscaLead.class);
        verify(buscaLeadRepository).save(buscaLeadCaptor.capture());
        assertThat(buscaLeadCaptor.getValue().getBusca().getId()).isEqualTo(10L);
        assertThat(buscaLeadCaptor.getValue().getLead().getId()).isEqualTo(20L);
        assertThat(buscaLeadCaptor.getValue().getScoreNaBusca()).isEqualTo(95);
        assertThat(buscaLeadCaptor.getValue().getTemperaturaNaBusca()).isEqualTo("QUENTE");

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.totalEncontrados()).isEqualTo(1);
        assertThat(response.totalBloqueados()).isZero();
        assertThat(response.leads()).hasSize(1);
        assertThat(response.leads().getFirst().id()).isEqualTo(20L);
        assertThat(response.leads().getFirst().nome()).isEqualTo("Padaria Central");
        assertThat(response.leads().getFirst().categoria()).isEqualTo(CategoriaNegocio.PADARIA);
        assertThat(response.leads().getFirst().whatsappUrl())
            .isEqualTo("https://wa.me/554133334444");
        assertThat(response.leads().getFirst().score()).isEqualTo(95);
        assertThat(response.leads().getFirst().temperatura()).isEqualTo("QUENTE");
    }

    @Test
    void deveBloquearResultadoUnicoPorNomeSemCriarLeadOuVinculo() {
        PlacesSearchResponse.PlaceResult bloqueado = criarPlace(
            "place-bloqueado",
            "Supermercados BH Centro"
        );
        PlacesSearchResponse.PlaceResult bloqueadoDuplicado = criarPlace(
            "place-bloqueado",
            "Supermercados BH Centro duplicado"
        );
        PlacesSearchResponse.PlaceResult permitido = criarPlace("place-permitido", "Padaria Central");
        when(placesApiClient.buscarProximos(any(PlacesSearchRequest.class)))
            .thenReturn(new PlacesSearchResponse(List.of(
                bloqueado,
                bloqueadoDuplicado,
                permitido
            )));
        when(nomeBloqueadoService.listarTermosNormalizados())
            .thenReturn(List.of("supermercados bh"));
        when(nomeBloqueadoService.estaBloqueado(
            "Supermercados BH Centro",
            List.of("supermercados bh")
        )).thenReturn(true);
        when(nomeBloqueadoService.estaBloqueado(
            "Padaria Central",
            List.of("supermercados bh")
        )).thenReturn(false);
        when(buscaRepository.saveAndFlush(any(Busca.class))).thenAnswer(invocation -> {
            Busca busca = invocation.getArgument(0);
            busca.setId(15L);
            busca.setCriadoEm(LocalDateTime.of(2026, 9, 8, 12, 0));
            return busca;
        });
        when(leadRepository.findByGooglePlaceId("place-permitido")).thenReturn(Optional.empty());
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> {
            Lead lead = invocation.getArgument(0);
            lead.setId(25L);
            return lead;
        });

        BuscaResponse response = criarService().criar(criarRequestPadaria());

        assertThat(response.totalEncontrados()).isEqualTo(3);
        assertThat(response.totalBloqueados()).isEqualTo(1);
        assertThat(response.leads()).singleElement()
            .extracting(BuscaResponse.LeadEncontradoResponse::nome)
            .isEqualTo("Padaria Central");
        verify(leadRepository, never()).findByGooglePlaceId("place-bloqueado");
        verify(leadRepository, times(1)).save(any(Lead.class));
        verify(buscaLeadRepository, times(1)).save(any(BuscaLead.class));
    }

    @Test
    void deveAtualizarDadosExternosSemSobrescreverDadosComerciaisDoLeadExistente() {
        BuscaRequest request = criarRequestPadaria();
        PlacesSearchResponse responseGoogle = new PlacesSearchResponse(List.of(
            criarPlace("place-existente", "Nome atualizado"),
            criarPlace("place-existente", "Resultado duplicado")
        ));
        Lead leadExistente = new Lead();
        leadExistente.setId(30L);
        leadExistente.setGooglePlaceId("place-existente");
        leadExistente.setNome("Nome antigo");
        leadExistente.setStatus(StatusFunil.QUALIFICADO);
        leadExistente.setObservacoes("Cliente pediu retorno na sexta");
        leadExistente.setUltimoContatoEm(LocalDateTime.of(2026, 8, 10, 15, 30));
        leadExistente.setTelefone("(27) 99999-0000");
        leadExistente.setTelefoneNormalizado("5527999990000");
        leadExistente.setScore(72);
        leadExistente.setTemperatura(Temperatura.QUENTE);
        leadExistente.setMunicipioCodigoIbge("3205309");
        leadExistente.setMunicipioNome("Vitória");
        leadExistente.setUf("ES");
        leadExistente.setIdhm(new BigDecimal("0.845"));
        leadExistente.setIdhmReferencia((short) 2010);
        leadExistente.setCnpj("43869215000156");
        leadExistente.setRazaoSocial("CB VITORIA COMERCIO DE ALIMENTOS LTDA");
        leadExistente.setCnpjCorrespondidoEm(LocalDateTime.of(2026, 8, 12, 8, 0));

        when(placesApiClient.buscarProximos(any(PlacesSearchRequest.class)))
            .thenReturn(responseGoogle);
        when(buscaRepository.saveAndFlush(any(Busca.class))).thenAnswer(invocation -> {
            Busca busca = invocation.getArgument(0);
            busca.setId(11L);
            busca.setCriadoEm(LocalDateTime.of(2026, 8, 12, 9, 0));
            return busca;
        });
        when(leadRepository.findByGooglePlaceId("place-existente"))
            .thenReturn(Optional.of(leadExistente));
        when(leadRepository.save(leadExistente)).thenReturn(leadExistente);

        BuscaResponse response = criarService().criar(request);

        verify(leadRepository, times(1)).findByGooglePlaceId("place-existente");
        verify(leadRepository, times(1)).save(leadExistente);
        verify(buscaLeadRepository, times(1)).save(any(BuscaLead.class));
        assertThat(leadExistente.getNome()).isEqualTo("Nome atualizado");
        assertThat(leadExistente.getRatingGoogle()).isEqualByComparingTo("4.5");
        assertThat(leadExistente.getStatus()).isEqualTo(StatusFunil.QUALIFICADO);
        assertThat(leadExistente.getObservacoes()).isEqualTo("Cliente pediu retorno na sexta");
        assertThat(leadExistente.getUltimoContatoEm())
            .isEqualTo(LocalDateTime.of(2026, 8, 10, 15, 30));
        assertThat(leadExistente.getTelefone()).isEqualTo("(27) 99999-0000");
        assertThat(leadExistente.getTelefoneNormalizado()).isEqualTo("5527999990000");
        assertThat(leadExistente.getScore()).isEqualTo(95);
        assertThat(leadExistente.getTemperatura()).isEqualTo(Temperatura.QUENTE);
        assertThat(leadExistente.getMunicipioCodigoIbge()).isNull();
        assertThat(leadExistente.getMunicipioNome()).isNull();
        assertThat(leadExistente.getUf()).isNull();
        assertThat(leadExistente.getIdhm()).isNull();
        assertThat(leadExistente.getIdhmReferencia()).isNull();
        assertThat(leadExistente.getCnpj()).isEqualTo("43869215000156");
        assertThat(leadExistente.getRazaoSocial())
            .isEqualTo("CB VITORIA COMERCIO DE ALIMENTOS LTDA");
        assertThat(leadExistente.getCnpjCorrespondidoEm())
            .isEqualTo(LocalDateTime.of(2026, 8, 12, 8, 0));
        verifyNoInteractions(cnpjService);
        assertThat(response.totalEncontrados()).isEqualTo(2);
        assertThat(response.leads()).hasSize(1);
        assertThat(response.leads().getFirst().score()).isEqualTo(95);
        assertThat(response.leads().getFirst().temperatura()).isEqualTo("QUENTE");
    }

    @Test
    void deveRevalidarCnpjQuandoCompetenciaMunicipalMudar() {
        Lead lead = leadComCnpjDaCompetenciaAnterior("place-cnpj-revalidado");
        prepararNovaCapturaDoLead(lead);
        when(cnpjService.buscarDataBaseAtual("3205309"))
            .thenReturn(Optional.of(LocalDate.of(2026, 9, 8)));
        when(cnpjService.corresponder(lead)).thenReturn(Optional.of(
            new CnpjService.Correspondencia(
                "43869215000156",
                "CB VITORIA COMERCIO DE ALIMENTOS LTDA",
                LocalDate.of(2026, 9, 8),
                new BigDecimal("0.9876")
            )
        ));

        criarService().criar(criarRequestPadaria());

        assertThat(lead.getCnpj()).isEqualTo("43869215000156");
        assertThat(lead.getCnpjDataBase()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(lead.getCnpjConfianca()).isEqualByComparingTo("0.9876");
        assertThat(lead.getCnpjCorrespondidoEm())
            .isAfter(LocalDateTime.of(2026, 8, 8, 10, 0));
        verify(cnpjService).buscarDataBaseAtual("3205309");
        verify(cnpjService).corresponder(lead);
    }

    @Test
    void deveLimparCnpjQuandoNovaCompetenciaNaoConfirmarCorrespondencia() {
        Lead lead = leadComCnpjDaCompetenciaAnterior("place-cnpj-nao-confirmado");
        prepararNovaCapturaDoLead(lead);
        when(cnpjService.buscarDataBaseAtual("3205309"))
            .thenReturn(Optional.of(LocalDate.of(2026, 9, 8)));
        when(cnpjService.corresponder(lead)).thenReturn(Optional.empty());

        criarService().criar(criarRequestPadaria());

        assertThat(lead.getCnpj()).isNull();
        assertThat(lead.getRazaoSocial()).isNull();
        assertThat(lead.getCnpjCorrespondidoEm()).isNull();
        assertThat(lead.getCnpjDataBase()).isNull();
        assertThat(lead.getCnpjConfianca()).isNull();
    }

    @Test
    void deveReutilizarCacheSemDeixarDePersistirCadaBusca() {
        BuscaRequest primeiraRequest = new BuscaRequest(
            "Centro, Curitiba - PR",
            new BigDecimal("-25.42841"),
            new BigDecimal("-49.27331"),
            5,
            List.of(CategoriaNegocio.PADARIA, CategoriaNegocio.MERCADO)
        );
        BuscaRequest requestEquivalente = new BuscaRequest(
            "Outro texto para o mesmo ponto",
            new BigDecimal("-25.42844"),
            new BigDecimal("-49.27334"),
            5,
            List.of(CategoriaNegocio.MERCADO, CategoriaNegocio.PADARIA)
        );
        BuscaRequest requestComRaioDiferente = new BuscaRequest(
            "Centro, Curitiba - PR",
            new BigDecimal("-25.42841"),
            new BigDecimal("-49.27331"),
            6,
            List.of(CategoriaNegocio.PADARIA, CategoriaNegocio.MERCADO)
        );
        PlacesSearchResponse respostaVazia = new PlacesSearchResponse(List.of());
        AtomicLong sequenciaIds = new AtomicLong(100);

        when(placesApiClient.buscarProximos(any(PlacesSearchRequest.class)))
            .thenReturn(respostaVazia);
        when(buscaRepository.saveAndFlush(any(Busca.class))).thenAnswer(invocation -> {
            Busca busca = invocation.getArgument(0);
            busca.setId(sequenciaIds.getAndIncrement());
            busca.setCriadoEm(LocalDateTime.of(2026, 8, 17, 10, 0));
            return busca;
        });

        BuscaService service = criarService();
        BuscaResponse primeiraResposta = service.criar(primeiraRequest);
        BuscaResponse respostaEquivalente = service.criar(requestEquivalente);
        service.criar(requestComRaioDiferente);

        verify(placesApiClient, times(2)).buscarProximos(any(PlacesSearchRequest.class));
        verify(buscaRepository, times(3)).saveAndFlush(any(Busca.class));
        assertThat(primeiraResposta.id()).isNotEqualTo(respostaEquivalente.id());
    }

    @Test
    void devePersistirLeadSemGeografiaQuandoPlacesNaoRetornarCoordenadas() {
        PlacesSearchResponse.PlaceResult placeSemCoordenadas =
            new PlacesSearchResponse.PlaceResult(
                "place-sem-coordenadas",
                "Lead sem coordenadas",
                CategoriaNegocio.PADARIA,
                "Endereço sem posição",
                null,
                null,
                null,
                null,
                null,
                "OPERATIONAL",
                List.of("bakery")
            );
        when(placesApiClient.buscarProximos(any(PlacesSearchRequest.class)))
            .thenReturn(new PlacesSearchResponse(List.of(placeSemCoordenadas)));
        when(buscaRepository.saveAndFlush(any(Busca.class))).thenAnswer(invocation -> {
            Busca busca = invocation.getArgument(0);
            busca.setId(12L);
            busca.setCriadoEm(LocalDateTime.of(2026, 9, 5, 19, 0));
            return busca;
        });
        when(leadRepository.findByGooglePlaceId("place-sem-coordenadas"))
            .thenReturn(Optional.empty());
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        criarService().criar(criarRequestPadaria());

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        assertThat(captor.getValue().getMunicipioCodigoIbge()).isNull();
        assertThat(captor.getValue().getIdhm()).isNull();
        verifyNoInteractions(municipioService);
    }

    @Test
    void deveListarHistoricoDaBuscaMaisRecenteParaAMaisAntiga() {
        Busca buscaRecente = criarBuscaHistorica(
            22L,
            "Centro de Vitória",
            "PADARIA,MERCADO",
            LocalDateTime.of(2026, 8, 18, 11, 0)
        );
        Busca buscaAntiga = criarBuscaHistorica(
            21L,
            "Praia do Canto",
            "RESTAURANTE",
            LocalDateTime.of(2026, 8, 17, 9, 30)
        );
        when(buscaRepository.findAllByOrderByCriadoEmDesc())
            .thenReturn(List.of(buscaRecente, buscaAntiga));

        List<BuscaResumoResponse> resposta = criarService().listarHistorico();

        assertThat(resposta).extracting(BuscaResumoResponse::id)
            .containsExactly(22L, 21L);
        assertThat(resposta.getFirst().categorias())
            .containsExactly(CategoriaNegocio.PADARIA, CategoriaNegocio.MERCADO);
        assertThat(resposta.get(1).categorias())
            .containsExactly(CategoriaNegocio.RESTAURANTE);
        verify(buscaRepository).findAllByOrderByCriadoEmDesc();
    }

    @Test
    void deveBuscarHistoricoComScoreDaBuscaEDadosComerciaisAtuais() {
        Busca busca = criarBuscaHistorica(
            22L,
            "Centro de Vitória",
            "PADARIA",
            LocalDateTime.of(2026, 8, 18, 11, 0)
        );
        Lead lead = new Lead();
        lead.setId(35L);
        lead.setNome("Padaria Central");
        lead.setCategoria(CategoriaNegocio.PADARIA);
        lead.setEnderecoFormatado("Rua Sete, 100");
        lead.setTelefone("(27) 99999-0000");
        lead.setTelefoneNormalizado("5527999990000");
        lead.setScore(95);
        lead.setTemperatura(Temperatura.QUENTE);
        lead.setStatus(StatusFunil.CONTATADO);
        lead.setObservacoes("Retornar amanhã");
        lead.setUltimoContatoEm(LocalDateTime.of(2026, 8, 18, 14, 0));

        BuscaLead vinculo = new BuscaLead();
        vinculo.setBusca(busca);
        vinculo.setLead(lead);
        vinculo.setScoreNaBusca(55);
        vinculo.setTemperaturaNaBusca("MORNO");

        when(buscaRepository.findById(22L)).thenReturn(Optional.of(busca));
        when(buscaLeadRepository.findByBuscaIdOrderByScoreNaBuscaDesc(22L))
            .thenReturn(List.of(vinculo));

        BuscaDetalheResponse resposta = criarService().buscarHistoricoPorId(22L);

        assertThat(resposta.id()).isEqualTo(22L);
        assertThat(resposta.leads()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(35L);
            assertThat(item.scoreNaBusca()).isEqualTo(55);
            assertThat(item.temperaturaNaBusca()).isEqualTo(Temperatura.MORNO);
            assertThat(item.status()).isEqualTo(StatusFunil.CONTATADO);
            assertThat(item.observacoes()).isEqualTo("Retornar amanhã");
            assertThat(item.whatsappUrl()).isEqualTo("https://wa.me/5527999990000");
        });
    }

    @Test
    void deveRetornarErroQuandoBuscaHistoricaNaoExistir() {
        when(buscaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> criarService().buscarHistoricoPorId(99L))
            .isInstanceOf(BuscaNaoEncontradaException.class)
            .hasMessageContaining("99");
    }

    private BuscaService criarService() {
        return new BuscaService(
            buscaRepository,
            buscaLeadRepository,
            leadRepository,
            municipioService,
            placesApiClient,
            new TelefoneNormalizer(),
            new ScoringService(),
            new BuscaPlacesCache(30, 100),
            new WhatsAppLinkGenerator(),
            nomeBloqueadoService,
            cnpjService
        );
    }

    private Lead leadComCnpjDaCompetenciaAnterior(String googlePlaceId) {
        Lead lead = new Lead();
        lead.setId(80L);
        lead.setGooglePlaceId(googlePlaceId);
        lead.setNome("Coco Bambu Vitória");
        lead.setStatus(StatusFunil.NOVO);
        lead.setCep("29055620");
        lead.setLogradouro("Rua João da Cruz");
        lead.setNumero("10");
        lead.setBairro("Praia do Canto");
        lead.setCnpj("43869215000156");
        lead.setRazaoSocial("CB VITORIA COMERCIO DE ALIMENTOS LTDA");
        lead.setCnpjCorrespondidoEm(LocalDateTime.of(2026, 8, 8, 10, 0));
        lead.setCnpjDataBase(LocalDate.of(2026, 8, 8));
        lead.setCnpjConfianca(new BigDecimal("0.9500"));
        return lead;
    }

    private void prepararNovaCapturaDoLead(Lead lead) {
        when(placesApiClient.buscarProximos(any(PlacesSearchRequest.class)))
            .thenReturn(new PlacesSearchResponse(List.of(
                criarPlace(lead.getGooglePlaceId(), "Coco Bambu Vitória")
            )));
        when(buscaRepository.saveAndFlush(any(Busca.class))).thenAnswer(invocation -> {
            Busca busca = invocation.getArgument(0);
            busca.setId(81L);
            busca.setCriadoEm(LocalDateTime.of(2026, 9, 8, 10, 0));
            return busca;
        });
        when(leadRepository.findByGooglePlaceId(lead.getGooglePlaceId()))
            .thenReturn(Optional.of(lead));
        when(leadRepository.save(lead)).thenReturn(lead);
        when(municipioService.localizar(any(BigDecimal.class), any(BigDecimal.class)))
            .thenReturn(Optional.of(new MunicipioInfo(
                "3205309",
                "Vitória",
                "ES",
                new BigDecimal("0.845"),
                (short) 2010
            )));
    }

    private BuscaRequest criarRequestPadaria() {
        return new BuscaRequest(
            "Centro, Curitiba - PR",
            new BigDecimal("-25.4284"),
            new BigDecimal("-49.2733"),
            5,
            List.of(CategoriaNegocio.PADARIA)
        );
    }

    private PlacesSearchResponse.PlaceResult criarPlace(String googlePlaceId, String nome) {
        return new PlacesSearchResponse.PlaceResult(
            googlePlaceId,
            nome,
            CategoriaNegocio.PADARIA,
            "Rua Central, 100",
            null,
            new BigDecimal("-25.4300"),
            new BigDecimal("-49.2700"),
            new BigDecimal("4.5"),
            120,
            "OPERATIONAL",
            List.of("bakery")
        );
    }

    private Busca criarBuscaHistorica(
        Long id,
        String enderecoBase,
        String categorias,
        LocalDateTime criadoEm
    ) {
        Busca busca = new Busca();
        busca.setId(id);
        busca.setEnderecoBase(enderecoBase);
        busca.setLatitude(new BigDecimal("-20.3155"));
        busca.setLongitude(new BigDecimal("-40.3128"));
        busca.setRaioKm(5);
        busca.setCategoriasBuscadas(categorias);
        busca.setTotalEncontrados(1);
        busca.setCriadoEm(criadoEm);
        return busca;
    }
}
