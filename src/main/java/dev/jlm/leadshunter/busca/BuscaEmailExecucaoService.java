package dev.jlm.leadshunter.busca;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class BuscaEmailExecucaoService {
    static final List<PesquisaInformacoesStatus> ATIVAS = List.of(
        PesquisaInformacoesStatus.PENDENTE, PesquisaInformacoesStatus.EM_ANDAMENTO);

    private final BuscaRepository buscas;
    private final BuscaLeadRepository vinculos;
    private final BuscaEmailExecucaoRepository execucoes;
    private final BuscaEmailWorker worker;
    private final int maximoLeads;

    public BuscaEmailExecucaoService(BuscaRepository buscas, BuscaLeadRepository vinculos,
        BuscaEmailExecucaoRepository execucoes, BuscaEmailWorker worker,
        @Value("${pesquisa-email.execucao.max-leads:150}") int maximoLeads) {
        if (maximoLeads < 1 || maximoLeads > 1000) {
            throw new IllegalArgumentException("O limite de leads deve estar entre 1 e 1000.");
        }
        this.buscas = buscas;
        this.vinculos = vinculos;
        this.execucoes = execucoes;
        this.worker = worker;
        this.maximoLeads = maximoLeads;
    }

    @Transactional
    public BuscaEmailResponse iniciar(Long buscaId) {
        Busca busca = buscas.bloquearPorId(buscaId)
            .orElseThrow(() -> new BuscaNaoEncontradaException(buscaId));
        var ativa = execucoes.findFirstByBuscaIdAndStatusInOrderByIdDesc(buscaId, ATIVAS);
        if (ativa.isPresent()) return BuscaEmailResponse.de(ativa.orElseThrow());
        long total = vinculos.countByBuscaId(buscaId);
        if (total > maximoLeads || !worker.reservar()) throw new BuscaEmailLimiteException();
        var sincronizacao = new TransactionSynchronization() {
            private Long id;
            @Override public void afterCommit() { worker.enfileirar(id); }
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) worker.liberarReserva();
            }
        };
        TransactionSynchronizationManager.registerSynchronization(sincronizacao);
        BuscaEmailExecucao execucao = execucoes.saveAndFlush(new BuscaEmailExecucao(busca, (int) total));
        sincronizacao.id = execucao.getId();
        return BuscaEmailResponse.de(execucao);
    }

    @Transactional(readOnly = true)
    public Optional<BuscaEmailResponse> consultar(Long buscaId) {
        if (!buscas.existsById(buscaId)) throw new BuscaNaoEncontradaException(buscaId);
        return execucoes.findFirstByBuscaIdAndStatusInOrderByIdDesc(buscaId, ATIVAS)
            .or(() -> execucoes.findFirstByBuscaIdOrderByIdDesc(buscaId))
            .map(BuscaEmailResponse::de);
    }
}
