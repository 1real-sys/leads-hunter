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
public class BuscaEmailWorker {
    private final BuscaEmailProcessamento processamento;
    private final BuscaEmailPersistencia persistencia;
    private final ThreadPoolTaskExecutor executor;
    private final Semaphore vagas = new Semaphore(2);
    private final AtomicBoolean pronto = new AtomicBoolean();

    public BuscaEmailWorker(BuscaEmailProcessamento processamento, BuscaEmailPersistencia persistencia) {
        this.processamento = processamento;
        this.persistencia = persistencia;
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("busca-email-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void preparar() {
        int interrompidas = persistencia.recuperarInterrompidas();
        log.info("Busca de e-mails pronta; execuções interrompidas recuperadas={}", interrompidas);
        pronto.set(true);
    }

    public boolean reservar() { return pronto.get() && vagas.tryAcquire(); }
    public void liberarReserva() { vagas.release(); }

    public void enfileirar(Long id) {
        try {
            executor.execute(() -> executar(id));
        } catch (RuntimeException exception) {
            liberarReserva();
            persistencia.falhar(id, "EMAIL_OCUPADO", "A busca de e-mails está ocupada.");
        }
    }

    private void executar(Long id) {
        try {
            Long buscaId = persistencia.iniciar(id);
            if (buscaId != null) processamento.executar(id, buscaId);
        } catch (RuntimeException exception) {
            boolean interrompida = Thread.currentThread().isInterrupted();
            if (interrompida) Thread.interrupted();
            try {
                persistencia.falhar(id, interrompida ? "EMAIL_INTERROMPIDO" : "EMAIL_ERRO_INTERNO",
                    interrompida ? "A busca de e-mails foi interrompida."
                        : "Não foi possível concluir a busca de e-mails.");
            } catch (RuntimeException persistenciaException) {
                log.error("Não foi possível registrar falha da execução de e-mail {}", id);
            } finally {
                if (interrompida) Thread.currentThread().interrupt();
            }
            log.warn("Busca de e-mails encerrada com falha; execução={}", id);
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
