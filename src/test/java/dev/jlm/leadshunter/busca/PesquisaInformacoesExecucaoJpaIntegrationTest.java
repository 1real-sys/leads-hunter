package dev.jlm.leadshunter.busca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.jlm.leadshunter.integracao.pesquisa.*;
import dev.jlm.leadshunter.lead.*;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class PesquisaInformacoesExecucaoJpaIntegrationTest {
    @Autowired private BuscaInformacoesExecucaoService service;
    @Autowired private PesquisaInformacoesExecucaoPersistencia persistencia;
    @Autowired private PesquisaInformacoesExecucaoRepository execucoes;
    @Autowired private BuscaRepository buscas;
    @Autowired private BuscaLeadRepository vinculos;
    @Autowired private LeadRepository leads;
    @Autowired private BuscaInformacoesService pesquisaService;
    @Autowired private BuscaInformacoesPersistencia observacoes;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @MockitoBean private BuscaInformacoesWorker worker;
    @MockitoBean private PesquisaInformacoesGateway gateway;

    private TransactionTemplate tx;
    private final List<Long> buscaIds = new ArrayList<>();
    private final List<Long> leadIds = new ArrayList<>();
    private static final PesquisaInformacoesWebResultado SITE = new PesquisaInformacoesWebResultado(
        Optional.empty(), Optional.of(URI.create("https://padaria.example/")));

    @BeforeEach
    void preparar() {
        tx = new TransactionTemplate(transactionManager);
        when(worker.reservar()).thenReturn(true);
    }

    @AfterEach
    void limparSomenteFixturesCriadas() {
        tx.executeWithoutResult(status -> {
            for (Long id : buscaIds) {
                entityManager.createQuery("delete from PesquisaInformacoesExecucao e where e.busca.id = :id")
                    .setParameter("id", id).executeUpdate();
                vinculos.deleteAll(vinculos.findByBuscaIdOrderByScoreNaBuscaDesc(id));
                vinculos.flush();
                buscas.deleteById(id);
            }
            leads.deleteAllById(leadIds);
        });
    }

    @Test
    void disparaSomenteAposCommitEDevolveMesmaExecucaoAtiva() {
        Long busca = criarBusca(2);
        var criada = tx.execute(status -> {
            var resposta = service.iniciar(busca);
            assertThat(resposta.status()).isEqualTo(PesquisaInformacoesStatus.PENDENTE);
            assertThat(resposta.totalLeads()).isEqualTo(2);
            assertThat(resposta.progresso()).isZero();
            verify(worker, never()).enfileirar(any());
            return resposta;
        });
        verify(worker).enfileirar(criada.id());
        assertThat(service.iniciar(busca).id()).isEqualTo(criada.id());
        verify(worker, times(1)).reservar();
        verify(worker, times(1)).enfileirar(any());
    }

    @Test
    void rollbackNaoDisparaWorkerELiberaReserva() {
        Long busca = criarBusca(0);
        tx.executeWithoutResult(status -> {
            service.iniciar(busca);
            status.setRollbackOnly();
        });
        verify(worker, never()).enfileirar(any());
        verify(worker).liberarReserva();
        assertThat(service.consultar(busca)).isEmpty();
    }

    @Test
    void requisicoesConcorrentesCriamSomenteUmaExecucao() throws Exception {
        Long busca = criarBusca(0);
        CountDownLatch largada = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Long> iniciar = () -> {
                assertThat(largada.await(5, TimeUnit.SECONDS)).isTrue();
                return service.iniciar(busca).id();
            };
            Future<Long> primeira = pool.submit(iniciar);
            Future<Long> segunda = pool.submit(iniciar);
            largada.countDown();
            assertThat(primeira.get(10, TimeUnit.SECONDS)).isEqualTo(segunda.get(10, TimeUnit.SECONDS));
        }
        verify(worker, times(1)).reservar();
        verify(worker, times(1)).enfileirar(any());
    }

    @Test
    void bancoImpedeSegundaExecucaoAtivaMesmoForaDoService() {
        Long busca = criarBusca(0);
        service.iniciar(busca);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> execucoes.saveAndFlush(
            new PesquisaInformacoesExecucao(buscas.getReferenceById(busca), 0))))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persisteProgressoPorLeadEConcluiComFalhaParcialSemTransacaoNaInternet() {
        Long busca = criarBusca(2);
        Long id = service.iniciar(busca).id();
        assertThat(persistencia.iniciar(id)).isEqualTo(busca);
        when(gateway.pesquisar(any())).thenAnswer(invocacao -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return SITE;
        }).thenAnswer(invocacao -> {
            assertThat(service.consultar(busca).orElseThrow().progresso()).isEqualTo(1);
            throw new GooglePesquisaWebTimeoutException(new RuntimeException("segredo interno"));
        });
        pesquisaService.buscarInformacoes(busca, (passo, erro) -> persistencia.registrar(id, passo, erro));
        persistencia.concluir(id);
        var resultado = service.consultar(busca).orElseThrow();
        assertThat(resultado.status()).isEqualTo(PesquisaInformacoesStatus.CONCLUIDA_COM_FALHAS);
        assertThat(resultado.progresso()).isEqualTo(2);
        assertThat(resultado.processados()).isEqualTo(1);
        assertThat(resultado.falhas()).isEqualTo(1);
        assertThat(resultado.comSite()).isEqualTo(1);
        assertThat(resultado.erroCodigo()).isEqualTo(PesquisaInformacoesErro.PESQUISA_TIMEOUT);
        assertThat(resultado.erroMensagem()).doesNotContain("segredo");
        assertThat(resultado.iniciadoEm()).isNotNull();
        assertThat(resultado.terminadoEm()).isNotNull();
        assertThat(leads.findById(leadIds.get(0)).orElseThrow().getObservacoes()).contains("Anotação manual");
        assertThat(leads.findById(leadIds.get(1)).orElseThrow().getObservacoes()).isEqualTo("Anotação manual");
    }

    @Test
    void rollbackDoProgressoTambemDesfazObservacaoDaqueleLead() {
        Long busca = criarBusca(1);
        Long id = service.iniciar(busca).id();
        persistencia.iniciar(id);
        assertThatThrownBy(() -> persistencia.registrar(id, () -> {
            observacoes.atualizarObservacoes(leadIds.getFirst(), SITE);
            throw new IllegalStateException("falha simulada após atualização");
        }, null)).isInstanceOf(IllegalStateException.class);
        assertThat(service.consultar(busca).orElseThrow().progresso()).isZero();
        assertThat(leads.findById(leadIds.getFirst()).orElseThrow().getObservacoes()).isEqualTo("Anotação manual");
    }

    @Test
    void captchaConcluiComFalhaTotalESemNovasConsultas() {
        Long busca = criarBusca(3);
        Long id = service.iniciar(busca).id();
        persistencia.iniciar(id);
        when(gateway.pesquisar(any())).thenThrow(new GooglePesquisaWebBloqueadaException());
        pesquisaService.buscarInformacoes(busca, (passo, erro) -> persistencia.registrar(id, passo, erro));
        persistencia.concluir(id);
        var resultado = service.consultar(busca).orElseThrow();
        assertThat(resultado.status()).isEqualTo(PesquisaInformacoesStatus.FALHA);
        assertThat(resultado.falhas()).isEqualTo(3);
        assertThat(resultado.erroCodigo()).isEqualTo(PesquisaInformacoesErro.PESQUISA_BLOQUEADA);
        verify(gateway, times(1)).pesquisar(any());
    }

    @Test
    void concluiBuscaVaziaEConsultaUltimaExecucao() {
        Long busca = criarBusca(0);
        Long id = service.iniciar(busca).id();
        persistencia.iniciar(id);
        persistencia.concluir(id);
        assertThat(service.consultar(busca).orElseThrow().status()).isEqualTo(PesquisaInformacoesStatus.CONCLUIDA);
        Long nova = service.iniciar(busca).id();
        assertThat(nova).isNotEqualTo(id);
        persistencia.falhar(nova, PesquisaInformacoesErro.PESQUISA_ERRO_INTERNO);
        assertThat(service.consultar(busca).orElseThrow().id()).isEqualTo(nova);
        assertThat(persistencia.iniciar(nova)).isNull();
    }

    @Test
    void recuperaPendentesEEmAndamentoPreservandoContadoresETerminais() {
        Long primeira = criarBusca(1);
        Long segunda = criarBusca(0);
        Long terceira = criarBusca(0);
        Long id = service.iniciar(primeira).id();
        persistencia.iniciar(id);
        persistencia.registrar(id, () -> new BuscaInformacoesResponse(1, 1, 0, 0, 1, 0, 0, 0), null);
        service.iniciar(segunda);
        Long terminal = service.iniciar(terceira).id();
        persistencia.iniciar(terminal);
        persistencia.concluir(terminal);
        assertThat(persistencia.recuperarInterrompidas()).isEqualTo(2);
        var recuperada = service.consultar(primeira).orElseThrow();
        assertThat(recuperada.status()).isEqualTo(PesquisaInformacoesStatus.FALHA);
        assertThat(recuperada.progresso()).isEqualTo(1);
        assertThat(recuperada.erroCodigo()).isEqualTo(PesquisaInformacoesErro.PESQUISA_INTERROMPIDA);
        assertThat(service.consultar(segunda).orElseThrow().terminadoEm()).isNotNull();
        assertThat(service.consultar(terceira).orElseThrow().status()).isEqualTo(PesquisaInformacoesStatus.CONCLUIDA);
        assertThat(service.iniciar(primeira).id()).isNotEqualTo(id);
    }

    @Test
    void recusaCapacidadeOuVolumeExcedidoSemCriarExecucao() {
        Long busca = criarBusca(0);
        when(worker.reservar()).thenReturn(false);
        assertThatThrownBy(() -> service.iniciar(busca)).isInstanceOf(PesquisaInformacoesLimiteException.class);
        assertThat(service.consultar(busca)).isEmpty();
        Long grande = criarBusca(2);
        var limitado = new BuscaInformacoesExecucaoService(buscas, vinculos, execucoes, worker, 1);
        clearInvocations(worker);
        assertThatThrownBy(() -> tx.execute(status -> limitado.iniciar(grande)))
            .isInstanceOf(PesquisaInformacoesLimiteException.class);
        verifyNoInteractions(worker);
        assertThat(service.consultar(grande)).isEmpty();
    }

    @Test
    void buscaInexistenteNaoReservaWorker() {
        assertThatThrownBy(() -> service.iniciar(Long.MAX_VALUE)).isInstanceOf(BuscaNaoEncontradaException.class);
        assertThatThrownBy(() -> service.consultar(Long.MAX_VALUE)).isInstanceOf(BuscaNaoEncontradaException.class);
        verifyNoInteractions(worker);
    }

    private Long criarBusca(int quantidade) {
        return tx.execute(status -> {
            Busca busca = buscas.save(new Busca());
            buscaIds.add(busca.getId());
            for (int i = 0; i < quantidade; i++) {
                Lead lead = new Lead();
                lead.setGooglePlaceId("info-014-" + UUID.randomUUID());
                lead.setNome("Padaria teste");
                lead.setCategoria(CategoriaNegocio.PADARIA);
                lead.setObservacoes("Anotação manual");
                lead = leads.save(lead);
                leadIds.add(lead.getId());
                BuscaLead vinculo = new BuscaLead();
                vinculo.setBusca(busca);
                vinculo.setLead(lead);
                vinculo.setScoreNaBusca(100 - i);
                vinculos.save(vinculo);
            }
            return busca.getId();
        });
    }
}
