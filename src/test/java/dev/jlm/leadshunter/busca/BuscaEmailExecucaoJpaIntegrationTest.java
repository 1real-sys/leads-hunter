package dev.jlm.leadshunter.busca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.jlm.leadshunter.integracao.pesquisa.EmailLeadService;
import dev.jlm.leadshunter.lead.CategoriaNegocio;
import dev.jlm.leadshunter.lead.Lead;
import dev.jlm.leadshunter.lead.LeadRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
class BuscaEmailExecucaoJpaIntegrationTest {
    @Autowired private BuscaEmailExecucaoService service;
    @Autowired private BuscaEmailPersistencia persistencia;
    @Autowired private BuscaEmailProcessamento processamento;
    @Autowired private BuscaEmailExecucaoRepository execucoes;
    @Autowired private BuscaRepository buscas;
    @Autowired private BuscaLeadRepository vinculos;
    @Autowired private LeadRepository leads;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @MockitoBean private BuscaEmailWorker worker;
    @MockitoBean private EmailLeadService extrator;

    private TransactionTemplate tx;
    private final List<Long> buscaIds = new ArrayList<>();
    private final List<Long> leadIds = new ArrayList<>();

    @BeforeEach
    void preparar() {
        tx = new TransactionTemplate(transactionManager);
        when(worker.reservar()).thenReturn(true);
    }

    @AfterEach
    void limparFixtures() {
        tx.executeWithoutResult(status -> {
            for (Long id : buscaIds) {
                entityManager.createQuery("delete from BuscaEmailExecucao e where e.busca.id = :id")
                    .setParameter("id", id).executeUpdate();
                vinculos.deleteAll(vinculos.findByBuscaIdOrderByScoreNaBuscaDesc(id));
                vinculos.flush();
                buscas.deleteById(id);
            }
            leads.deleteAllById(leadIds);
        });
    }

    @Test
    void enfileiraAposCommitImpedeDuplicataAtivaEReiniciaAposConclusao() {
        Long buscaId = criarBusca(0);
        var criada = tx.execute(status -> {
            var resposta = service.iniciar(buscaId);
            verify(worker, never()).enfileirar(any());
            return resposta;
        });
        verify(worker).enfileirar(criada.id());
        assertThat(service.iniciar(buscaId).id()).isEqualTo(criada.id());
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> execucoes.saveAndFlush(
            new BuscaEmailExecucao(buscas.getReferenceById(buscaId), 0))))
            .isInstanceOf(DataIntegrityViolationException.class);

        persistencia.iniciar(criada.id());
        persistencia.concluir(criada.id());
        assertThat(service.consultar(buscaId).orElseThrow().status())
            .isEqualTo(PesquisaInformacoesStatus.CONCLUIDA);
        assertThat(service.iniciar(buscaId).id()).isNotEqualTo(criada.id());
    }

    @Test
    void rollbackLiberaReservaENaoEnfileira() {
        Long buscaId = criarBusca(0);
        tx.executeWithoutResult(status -> {
            service.iniciar(buscaId);
            status.setRollbackOnly();
        });
        verify(worker, never()).enfileirar(any());
        verify(worker).liberarReserva();
        assertThat(service.consultar(buscaId)).isEmpty();
    }

    @Test
    void processaSemTransacaoDeRedeEContaLeadsNaoEnderecos() {
        Long buscaId = criarBusca(4);
        tx.executeWithoutResult(status -> {
            var existentes = vinculos.findByBuscaIdOrderByScoreNaBuscaDesc(buscaId);
            existentes.get(1).getLead().setEmail("anterior@loja.example.com.br");
            existentes.get(2).getLead().setWebsite(null);
        });
        var id = service.iniciar(buscaId).id();
        persistencia.iniciar(id);
        when(extrator.extrair(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new EmailLeadService.Resultado(EmailLeadService.Estado.ENCONTRADO,
                "contato@loja.example.com.br", "loja.example.com.br", true);
        }).thenThrow(new IllegalStateException("falha de leitura"));

        processamento.executar(id, buscaId);

        var resultado = service.consultar(buscaId).orElseThrow();
        assertThat(resultado.status()).isEqualTo(PesquisaInformacoesStatus.CONCLUIDA_COM_FALHAS);
        assertThat(resultado.totalLeads()).isEqualTo(4);
        assertThat(resultado.progresso()).isEqualTo(4);
        assertThat(resultado.ignoradosJaComEmail()).isEqualTo(1);
        assertThat(resultado.ignoradosSemSite()).isEqualTo(1);
        assertThat(resultado.processados()).isEqualTo(2);
        assertThat(resultado.encontrados()).isEqualTo(1);
        assertThat(resultado.falhas()).isEqualTo(1);
        assertThat(resultado.descartadosDominioExterno()).isEqualTo(1);
        assertThat(leads.findById(leadIds.getFirst()).orElseThrow().getEmail())
            .isEqualTo("contato@loja.example.com.br");
        assertThat(leads.findById(leadIds.getFirst()).orElseThrow().getEmailOrigemHost())
            .isEqualTo("loja.example.com.br");
    }

    @Test
    void trocaDeHostDuranteLeituraNaoGravaEmailAntigo() {
        Long buscaId = criarBusca(1);
        Long id = service.iniciar(buscaId).id();
        persistencia.iniciar(id);
        var snapshot = persistencia.carregar(buscaId).getFirst();
        tx.executeWithoutResult(status -> leads.findById(snapshot.id()).orElseThrow()
            .setWebsite("https://outra.example.com.br/"));
        persistencia.registrar(id, snapshot, new EmailLeadService.Resultado(
            EmailLeadService.Estado.ENCONTRADO, "contato@loja.example.com.br",
            "loja.example.com.br", false));
        persistencia.concluir(id);
        assertThat(leads.findById(snapshot.id()).orElseThrow().getEmail()).isNull();
        assertThat(service.consultar(buscaId).orElseThrow().semEmailElegivel()).isEqualTo(1);
    }

    private Long criarBusca(int quantidade) {
        return tx.execute(status -> {
            Busca busca = buscas.save(new Busca());
            buscaIds.add(busca.getId());
            for (int i = 0; i < quantidade; i++) {
                Lead lead = new Lead();
                lead.setGooglePlaceId("email-" + UUID.randomUUID());
                lead.setNome("Padaria teste");
                lead.setCategoria(CategoriaNegocio.PADARIA);
                lead.setWebsite("https://loja.example.com.br/");
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
