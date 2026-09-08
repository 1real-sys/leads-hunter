package dev.jlm.leadshunter.bloqueio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class NomeBloqueadoRepositoryTest {

    @Autowired
    private NomeBloqueadoRepository repository;

    @Test
    void devePersistirConsultarEListarEmOrdemDeCriacao() {
        NomeBloqueado primeiro = repository.saveAndFlush(
            new NomeBloqueado("Supermercados BH", "supermercados bh")
        );
        NomeBloqueado segundo = repository.saveAndFlush(
            new NomeBloqueado("Extrabom", "extrabom")
        );

        List<NomeBloqueado> cadastrados = repository.findAllByOrderByCriadoEmAscIdAsc();

        assertThat(primeiro.getId()).isNotNull();
        assertThat(primeiro.getCriadoEm()).isNotNull();
        assertThat(repository.findByTermoNormalizado("supermercados bh"))
            .contains(primeiro);
        assertThat(cadastrados).containsExactly(primeiro, segundo);
    }

    @Test
    void deveRejeitarTermoNormalizadoDuplicadoNoBanco() {
        repository.saveAndFlush(new NomeBloqueado("Supermercados BH", "supermercados bh"));

        assertThatThrownBy(() -> repository.saveAndFlush(
            new NomeBloqueado("supermercados bh", "supermercados bh")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
