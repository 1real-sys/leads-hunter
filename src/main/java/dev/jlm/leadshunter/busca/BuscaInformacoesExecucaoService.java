package dev.jlm.leadshunter.busca;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class BuscaInformacoesExecucaoService {
    private final BuscaRepository buscas;
    private final BuscaLeadRepository vinculos;
    private final PesquisaInformacoesExecucaoRepository execucoes;
    private final BuscaInformacoesWorker worker;
    private final int maximoLeads;

    public BuscaInformacoesExecucaoService(BuscaRepository buscas, BuscaLeadRepository vinculos,
        PesquisaInformacoesExecucaoRepository execucoes, BuscaInformacoesWorker worker,
        @Value("${pesquisa-inteligente.execucao.max-leads:150}") int maximoLeads) {
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
    public PesquisaInformacoesExecucaoResponse iniciar(Long buscaId) {
        Busca busca = buscas.bloquearPorId(buscaId)
            .orElseThrow(() -> new BuscaNaoEncontradaException(buscaId));
        var ativa = execucoes.findFirstByBuscaIdAndStatusInOrderByIdDesc(
            buscaId, PesquisaInformacoesExecucaoPersistencia.ATIVAS);
        if (ativa.isPresent()) return PesquisaInformacoesExecucaoResponse.de(ativa.get());

        long total = vinculos.countByBuscaId(buscaId);
        if (total > maximoLeads || !worker.reservar()) {
            throw new PesquisaInformacoesLimiteException();
        }
        // A reserva também é devolvida se o INSERT ou o commit falharem.
        var sincronizacao = new TransactionSynchronization() {
            private Long id;

            @Override public void afterCommit() { worker.enfileirar(id); }
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) worker.liberarReserva();
            }
        };
        TransactionSynchronizationManager.registerSynchronization(sincronizacao);
        PesquisaInformacoesExecucao execucao = execucoes.saveAndFlush(
            new PesquisaInformacoesExecucao(busca, (int) total));
        sincronizacao.id = execucao.getId();
        return PesquisaInformacoesExecucaoResponse.de(execucao);
    }

    @Transactional(readOnly = true)
    public Optional<PesquisaInformacoesExecucaoResponse> consultar(Long buscaId) {
        if (!buscas.existsById(buscaId)) throw new BuscaNaoEncontradaException(buscaId);
        return execucoes.findFirstByBuscaIdAndStatusInOrderByIdDesc(
                buscaId, PesquisaInformacoesExecucaoPersistencia.ATIVAS)
            .or(() -> execucoes.findFirstByBuscaIdOrderByIdDesc(buscaId))
            .map(PesquisaInformacoesExecucaoResponse::de);
    }
}
