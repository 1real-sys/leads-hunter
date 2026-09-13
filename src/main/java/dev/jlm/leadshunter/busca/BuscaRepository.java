package dev.jlm.leadshunter.busca;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BuscaRepository extends JpaRepository<Busca, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Busca b where b.id = :id")
    Optional<Busca> bloquearPorId(Long id);

    List<Busca> findAllByOrderByCriadoEmDesc();
}
