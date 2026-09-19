package dev.jlm.leadshunter.busca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.jlm.leadshunter.integracao.pesquisa.*;
import dev.jlm.leadshunter.lead.*;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class PesquisaInformacoesHttpIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private BuscaRepository buscas;
    @Autowired private LeadRepository leads;
    @Autowired private BuscaLeadRepository vinculos;
    @Autowired private BuscaInformacoesExecucaoService service;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @MockitoBean private PesquisaInformacoesGateway gateway;

    @Test
    void postRetornaEnquantoWorkerPesquisaEGetRecuperaProgressoEConclusao() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        List<Long> leadIds = new ArrayList<>();
        Long buscaId = tx.execute(status -> {
            Busca busca = buscas.save(new Busca());
            for (int i = 0; i < 2; i++) {
                Lead lead = new Lead();
                lead.setGooglePlaceId("info-http-" + UUID.randomUUID());
                lead.setNome("Padaria Teste");
                lead.setCategoria(CategoriaNegocio.PADARIA);
                lead.setObservacoes("Anotação comercial");
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
        var entrouNoSegundo = new CountDownLatch(1);
        var liberarSegundo = new CountDownLatch(1);
        var chamadas = new AtomicInteger();
        when(gateway.pesquisar(any(), anyBoolean())).thenAnswer(invocacao -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (chamadas.incrementAndGet() == 1) {
                return new PesquisaInformacoesWebResultado(Optional.empty(), Optional.of(URI.create("https://padaria.example/")));
            }
            entrouNoSegundo.countDown();
            if (!liberarSegundo.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Teste não liberou a pesquisa");
            throw new GooglePesquisaWebFormatoInvalidoException(new RuntimeException("HTML com segredo"));
        });
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        String rota = "/api/buscas/" + buscaId + "/informacoes";
        try {
            mvc.perform(get(rota)).andExpect(status().isNoContent());
            mvc.perform(post(rota)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDENTE"));
            assertThat(entrouNoSegundo.await(5, TimeUnit.SECONDS)).isTrue();
            Long execucao = service.consultar(buscaId).orElseThrow().id();
            mvc.perform(get(rota)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.progresso").value(1))
                .andExpect(jsonPath("$.comSite").value(1));
            mvc.perform(post(rota)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(execucao));
            liberarSegundo.countDown();
            aguardarTermino(buscaId);
            mvc.perform(get(rota)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA_COM_FALHAS"))
                .andExpect(jsonPath("$.progresso").value(2))
                .andExpect(jsonPath("$.falhas").value(1))
                .andExpect(jsonPath("$.erroCodigo").value("PESQUISA_FORMATO_INVALIDO"))
                .andExpect(jsonPath("$.erroMensagem").value(PesquisaInformacoesErro.PESQUISA_FORMATO_INVALIDO.mensagem()))
                .andExpect(jsonPath("$.terminadoEm").isNotEmpty());
            assertThat(leads.findById(leadIds.get(0)).orElseThrow().getObservacoes())
                .startsWith("Anotação comercial").contains("https://padaria.example/");
            assertThat(leads.findById(leadIds.get(1)).orElseThrow().getObservacoes()).isEqualTo("Anotação comercial");
            verify(gateway, times(2)).pesquisar(any(), anyBoolean());
        } finally {
            liberarSegundo.countDown();
            if (service.consultar(buscaId).isPresent()) aguardarTermino(buscaId);
            tx.executeWithoutResult(status -> {
                entityManager.createQuery("delete from PesquisaInformacoesExecucao e where e.busca.id = :id")
                    .setParameter("id", buscaId).executeUpdate();
                vinculos.deleteAll(vinculos.findByBuscaIdOrderByScoreNaBuscaDesc(buscaId));
                vinculos.flush();
                buscas.deleteById(buscaId);
                leads.deleteAllById(leadIds);
            });
        }
    }

    private void aguardarTermino(Long buscaId) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (service.consultar(buscaId).orElseThrow().status().ativa() && System.nanoTime() < limite) {
            Thread.sleep(20);
        }
        assertThat(service.consultar(buscaId).orElseThrow().status().ativa()).isFalse();
    }
}
