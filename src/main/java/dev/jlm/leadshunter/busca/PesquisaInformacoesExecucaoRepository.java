package dev.jlm.leadshunter.busca;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PesquisaInformacoesExecucaoRepository extends JpaRepository<PesquisaInformacoesExecucao, Long> {
    Optional<PesquisaInformacoesExecucao> findFirstByBuscaIdOrderByIdDesc(Long buscaId);

    Optional<PesquisaInformacoesExecucao> findFirstByBuscaIdAndStatusInOrderByIdDesc(
        Long buscaId, Collection<PesquisaInformacoesStatus> status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from PesquisaInformacoesExecucao e where e.id = :id")
    Optional<PesquisaInformacoesExecucao> bloquearPorId(Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PesquisaInformacoesExecucao e set e.status = :falha,
            e.erroCodigo = :codigo, e.erroMensagem = :mensagem,
            e.terminadoEm = :agora, e.atualizadoEm = :agora
        where e.status in :ativas
        """)
    int interromperAtivas(Collection<PesquisaInformacoesStatus> ativas,
        PesquisaInformacoesStatus falha, PesquisaInformacoesErro codigo, String mensagem, LocalDateTime agora);
}
