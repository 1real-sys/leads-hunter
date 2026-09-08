package dev.jlm.leadshunter.bloqueio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NomeBloqueadoServiceTest {

    @Mock
    private NomeBloqueadoRepository repository;

    @Test
    void deveCadastrarTermoExibidoENormalizado() {
        when(repository.findByTermoNormalizado("supermercados bh")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(NomeBloqueado.class))).thenAnswer(invocation -> {
            NomeBloqueado salvo = invocation.getArgument(0);
            ReflectionTestUtils.setField(salvo, "id", 12L);
            ReflectionTestUtils.setField(salvo, "criadoEm", LocalDateTime.of(2026, 9, 8, 11, 0));
            return salvo;
        });

        NomeBloqueadoResponse response = criarService().cadastrar("  Supérmercados BH  ");

        ArgumentCaptor<NomeBloqueado> captor = ArgumentCaptor.forClass(NomeBloqueado.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTermo()).isEqualTo("Supérmercados BH");
        assertThat(captor.getValue().getTermoNormalizado()).isEqualTo("supermercados bh");
        assertThat(response.id()).isEqualTo(12L);
        assertThat(response.termo()).isEqualTo("Supérmercados BH");
    }

    @Test
    void deveRejeitarDuplicidadeNormalizadaAntesDePersistir() {
        when(repository.findByTermoNormalizado("supermercados bh"))
            .thenReturn(Optional.of(new NomeBloqueado("Supermercados BH", "supermercados bh")));

        assertThatThrownBy(() -> criarService().cadastrar("SUPERMERCADOS BH"))
            .isInstanceOf(NomeBloqueadoDuplicadoException.class)
            .hasMessage("Já existe um bloqueio cadastrado para esse termo.");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void deveTraduzirConcorrenciaDeUnicidadeParaErroDeDominio() {
        when(repository.findByTermoNormalizado("extrabom")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(NomeBloqueado.class)))
            .thenThrow(new DataIntegrityViolationException("duplicado"));

        assertThatThrownBy(() -> criarService().cadastrar("Extrabom"))
            .isInstanceOf(NomeBloqueadoDuplicadoException.class)
            .hasMessage("Já existe um bloqueio cadastrado para esse termo.");
    }

    @Test
    void deveValidarTermoAntesDeConsultarOPersistir() {
        NomeBloqueadoService service = criarService();

        assertThatThrownBy(() -> service.cadastrar("  "))
            .isInstanceOf(NomeBloqueadoInvalidoException.class);
        assertThatThrownBy(() -> service.cadastrar("ab"))
            .isInstanceOf(NomeBloqueadoInvalidoException.class);
        assertThatThrownBy(() -> service.cadastrar("a".repeat(121)))
            .isInstanceOf(NomeBloqueadoInvalidoException.class);
        verify(repository, never()).findByTermoNormalizado(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void deveListarEmOrdemFornecidaPeloRepositorio() {
        NomeBloqueado primeiro = criarPersistido(1L, "Supermercados BH", "supermercados bh");
        NomeBloqueado segundo = criarPersistido(2L, "Extrabom", "extrabom");
        when(repository.findAllByOrderByCriadoEmAscIdAsc()).thenReturn(List.of(primeiro, segundo));

        assertThat(criarService().listar())
            .extracting(NomeBloqueadoResponse::termo)
            .containsExactly("Supermercados BH", "Extrabom");
    }

    @Test
    void deveIdentificarNomePorSubstringNormalizada() {
        NomeBloqueadoService service = criarService();

        assertThat(service.estaBloqueado(
            "Supermercádos BH Centro",
            List.of("supermercados bh")
        )).isTrue();
        assertThat(service.estaBloqueado("Mercado do Bairro", List.of("supermercados bh")))
            .isFalse();
        assertThat(service.estaBloqueado(null, List.of("supermercados bh"))).isFalse();
    }

    @Test
    void deveListarSomenteOsTermosNormalizadosParaAplicacaoNaBusca() {
        when(repository.findAllByOrderByCriadoEmAscIdAsc()).thenReturn(List.of(
            criarPersistido(1L, "Supermercados BH", "supermercados bh"),
            criarPersistido(2L, "Extrabom", "extrabom")
        ));

        assertThat(criarService().listarTermosNormalizados())
            .containsExactly("supermercados bh", "extrabom");
    }

    @Test
    void deveRemoverCadastroExistente() {
        NomeBloqueado existente = criarPersistido(5L, "Extrabom", "extrabom");
        when(repository.findById(5L)).thenReturn(Optional.of(existente));

        criarService().remover(5L);

        verify(repository).delete(existente);
    }

    @Test
    void deveRetornarErroAoRemoverIdInexistente() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> criarService().remover(99L))
            .isInstanceOf(NomeBloqueadoNaoEncontradoException.class)
            .hasMessageContaining("99");
        verify(repository, never()).delete(any());
    }

    private NomeBloqueadoService criarService() {
        return new NomeBloqueadoService(repository);
    }

    private NomeBloqueado criarPersistido(Long id, String termo, String normalizado) {
        NomeBloqueado nomeBloqueado = new NomeBloqueado(termo, normalizado);
        ReflectionTestUtils.setField(nomeBloqueado, "id", id);
        ReflectionTestUtils.setField(nomeBloqueado, "criadoEm", LocalDateTime.of(2026, 9, 8, 11, 0));
        return nomeBloqueado;
    }
}
