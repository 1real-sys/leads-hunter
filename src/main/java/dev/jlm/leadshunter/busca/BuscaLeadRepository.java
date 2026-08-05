package dev.jlm.leadshunter.busca;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BuscaLeadRepository extends JpaRepository<BuscaLead, Long> {

    List<BuscaLead> findByBuscaId(Long buscaId);

    List<BuscaLead> findByLeadId(Long leadId);

    boolean existsByBuscaIdAndLeadId(Long buscaId, Long leadId);
}
