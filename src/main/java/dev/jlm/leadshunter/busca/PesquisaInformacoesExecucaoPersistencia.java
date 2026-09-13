package dev.jlm.leadshunter.busca;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PesquisaInformacoesExecucaoPersistencia {
    static final List<PesquisaInformacoesStatus> ATIVAS = List.of(
        PesquisaInformacoesStatus.PENDENTE, PesquisaInformacoesStatus.EM_ANDAMENTO);

    private final PesquisaInformacoesExecucaoRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long iniciar(Long id) {
        PesquisaInformacoesExecucao execucao = repository.bloquearPorId(id).orElseThrow();
        return execucao.iniciar() ? execucao.getBusca().getId() : null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Long id, Supplier<BuscaInformacoesResponse> passo, PesquisaInformacoesErro erro) {
        PesquisaInformacoesExecucao execucao = repository.bloquearPorId(id).orElseThrow();
        if (execucao.getStatus() != PesquisaInformacoesStatus.EM_ANDAMENTO) {
            throw new IllegalStateException("A execução não está em andamento.");
        }
        execucao.atualizar(passo.get(), erro);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void concluir(Long id) {
        repository.bloquearPorId(id).orElseThrow().concluir();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void falhar(Long id, PesquisaInformacoesErro erro) {
        repository.bloquearPorId(id).ifPresent(execucao -> execucao.falhar(erro));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recuperarInterrompidas() {
        return repository.interromperAtivas(ATIVAS, PesquisaInformacoesStatus.FALHA,
            PesquisaInformacoesErro.PESQUISA_INTERROMPIDA,
            PesquisaInformacoesErro.PESQUISA_INTERROMPIDA.mensagem(), LocalDateTime.now());
    }
}
