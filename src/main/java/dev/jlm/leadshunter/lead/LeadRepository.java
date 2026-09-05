package dev.jlm.leadshunter.lead;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {

    Optional<Lead> findByGooglePlaceId(String googlePlaceId);

    boolean existsByGooglePlaceId(String googlePlaceId);

    @Query("""
        SELECT lead
        FROM Lead lead
        WHERE lead.municipioCodigoIbge IS NULL
          AND lead.latitude IS NOT NULL
          AND lead.longitude IS NOT NULL
          AND lead.id > :ultimoId
        ORDER BY lead.id
        """)
    List<Lead> buscarPendentesGeografiaAposId(
        @Param("ultimoId") Long ultimoId,
        Pageable pageable
    );
}
