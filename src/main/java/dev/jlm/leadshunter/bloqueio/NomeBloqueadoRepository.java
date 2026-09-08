package dev.jlm.leadshunter.bloqueio;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NomeBloqueadoRepository extends JpaRepository<NomeBloqueado, Long> {

    List<NomeBloqueado> findAllByOrderByCriadoEmAscIdAsc();

    Optional<NomeBloqueado> findByTermoNormalizado(String termoNormalizado);
}
