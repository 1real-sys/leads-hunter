package dev.jlm.leadshunter.busca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BuscaInformacoesWorkerTest {
    @Test
    void limitaFilaExecutaEmOutraThreadEConclui() throws Exception {
        var pesquisa = mock(BuscaInformacoesService.class);
        var persistencia = mock(PesquisaInformacoesExecucaoPersistencia.class);
        var worker = new BuscaInformacoesWorker(pesquisa, persistencia);
        var entrou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var terminou = new CountDownLatch(2);
        Thread chamadora = Thread.currentThread();
        when(persistencia.iniciarComConfiguracao(anyLong()))
            .thenAnswer(i -> new PesquisaInformacoesExecucaoContexto(i.getArgument(0), true));
        when(pesquisa.buscarInformacoes(anyLong(), anyBoolean(), any())).thenAnswer(i -> {
            assertThat(Thread.currentThread()).isNotEqualTo(chamadora);
            entrou.countDown();
            assertThat(liberar.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        });
        doAnswer(i -> { terminou.countDown(); return null; }).when(persistencia).concluir(anyLong());
        try {
            assertThat(worker.reservar()).isFalse();
            worker.preparar();
            assertThat(worker.reservar()).isTrue();
            worker.enfileirar(1L);
            assertThat(entrou.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(worker.reservar()).isTrue();
            worker.enfileirar(2L);
            assertThat(worker.reservar()).isFalse();
            verify(persistencia, never()).iniciarComConfiguracao(2L);
            liberar.countDown();
            assertThat(terminou.await(5, TimeUnit.SECONDS)).isTrue();
            verify(persistencia).concluir(1L);
            verify(persistencia).concluir(2L);
        } finally {
            liberar.countDown();
            worker.encerrar();
        }
    }

    @Test
    void falhaInesperadaPersisteErroSeguroELiberaVaga() throws Exception {
        var pesquisa = mock(BuscaInformacoesService.class);
        var persistencia = mock(PesquisaInformacoesExecucaoPersistencia.class);
        var worker = new BuscaInformacoesWorker(pesquisa, persistencia);
        when(persistencia.iniciarComConfiguracao(1L))
            .thenReturn(new PesquisaInformacoesExecucaoContexto(9L, false));
        when(pesquisa.buscarInformacoes(eq(9L), eq(false), any()))
            .thenThrow(new RuntimeException("segredo"));
        try {
            worker.preparar();
            assertThat(worker.reservar()).isTrue();
            worker.enfileirar(1L);
            verify(persistencia, timeout(5000)).falhar(1L, PesquisaInformacoesErro.PESQUISA_ERRO_INTERNO);
            verify(persistencia, never()).concluir(any());
        } finally {
            worker.encerrar();
        }
    }

    @Test
    void rejeicaoDoExecutorMarcaFalhaEmVezDeDeixarPendente() {
        var persistencia = mock(PesquisaInformacoesExecucaoPersistencia.class);
        var worker = new BuscaInformacoesWorker(mock(BuscaInformacoesService.class), persistencia);
        worker.preparar();
        assertThat(worker.reservar()).isTrue();
        worker.encerrar();
        worker.enfileirar(1L);
        verify(persistencia).falhar(1L, PesquisaInformacoesErro.PESQUISA_OCUPADA);
        assertThat(worker.reservar()).isFalse();
    }
}
