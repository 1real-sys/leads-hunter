package dev.jlm.leadshunter.busca;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BuscaRepository extends JpaRepository<Busca, Long> {

    List<Busca> findAllByOrderByCriadoEmDesc();
}
