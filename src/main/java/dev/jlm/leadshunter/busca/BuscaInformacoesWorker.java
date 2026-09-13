package dev.jlm.leadshunter.busca;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BuscaInformacoesWorker {
    private final BuscaInformacoesService pesquisa;
    private final PesquisaInformacoesExecucaoPersistencia persistencia;
    private final ThreadPoolTaskExecutor executor;
    private final Semaphore vagas = new Semaphore(2);
    private final AtomicBoolean pronto = new AtomicBoolean();

    public BuscaInformacoesWorker(BuscaInformacoesService pesquisa,
                                 PesquisaInformacoesExecucaoPersistencia persistencia) {
        this.pesquisa = pesquisa;
        this.persistencia = persistencia;
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("pesquisa-informacoes-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void preparar() {
        int interrompidas = persistencia.recuperarInterrompidas();
        log.info("Pesquisa inteligente pronta; execuções interrompidas recuperadas={}", interrompidas);
        pronto.set(true);
    }

    public boolean reservar() {
        return pronto.get() && vagas.tryAcquire();
    }

    public void liberarReserva() {
        vagas.release();
    }

    public void enfileirar(Long id) {
        try {
            executor.execute(() -> executar(id));
        } catch (RuntimeException exception) {
            liberarReserva();
            persistencia.falhar(id, PesquisaInformacoesErro.PESQUISA_OCUPADA);
        }
    }

    private void executar(Long id) {
        try {
            Long buscaId = persistencia.iniciar(id);
            if (buscaId == null) return;
            pesquisa.buscarInformacoes(buscaId, (passo, erro) -> persistencia.registrar(id, passo, erro));
            persistencia.concluir(id);
        } catch (RuntimeException exception) {
            boolean interrompida = Thread.currentThread().isInterrupted();
            // Libera a interrupção apenas para registrar o término no banco.
            if (interrompida) Thread.interrupted();
            try {
                persistencia.falhar(id, interrompida ? PesquisaInformacoesErro.PESQUISA_INTERROMPIDA
                    : PesquisaInformacoesErro.PESQUISA_ERRO_INTERNO);
            } catch (RuntimeException persistenciaException) {
                log.error("Não foi possível registrar falha da execução {}; será recuperada no reinício", id);
            } finally {
                if (interrompida) Thread.currentThread().interrupt();
            }
            log.warn("Pesquisa inteligente encerrada com falha; execução={}", id);
        } finally {
            liberarReserva();
        }
    }

    @PreDestroy
    public void encerrar() {
        pronto.set(false);
        executor.shutdown();
    }
}
