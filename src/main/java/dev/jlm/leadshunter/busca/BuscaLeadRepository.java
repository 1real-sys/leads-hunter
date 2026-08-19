package dev.jlm.leadshunter.busca;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BuscaLeadRepository extends JpaRepository<BuscaLead, Long> {

    @EntityGraph(attributePaths = "lead")
    List<BuscaLead> findByBuscaIdOrderByScoreNaBuscaDesc(Long buscaId);

    List<BuscaLead> findByLeadId(Long leadId);

    boolean existsByBuscaIdAndLeadId(Long buscaId, Long leadId);
}
