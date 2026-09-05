package dev.jlm.leadshunter.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jlm.leadshunter.lead.Lead;
import dev.jlm.leadshunter.lead.LeadRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class MunicipioBackfillServiceTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private MunicipioService municipioService;

    @Test
    void deveProcessarEmLotesEAtualizarSomenteMunicipiosEncontrados() {
        Lead vitoria = lead(1L, "-20.3155", "-40.3128");
        Lead foraDoBrasil = lead(2L, "40.7128", "-74.0060");
        MunicipioInfo infoVitoria = new MunicipioInfo(
            "3205309",
            "Vitória",
            "ES",
            new BigDecimal("0.845"),
            (short) 2010
        );

        when(leadRepository.buscarPendentesGeografiaAposId(
            org.mockito.ArgumentMatchers.eq(0L),
            org.mockito.ArgumentMatchers.any(Pageable.class)
        ))
            .thenReturn(List.of(vitoria, foraDoBrasil));
        when(leadRepository.buscarPendentesGeografiaAposId(
            org.mockito.ArgumentMatchers.eq(2L),
            org.mockito.ArgumentMatchers.any(Pageable.class)
        ))
            .thenReturn(List.of());
        when(municipioService.localizar(vitoria.getLatitude(), vitoria.getLongitude()))
            .thenReturn(Optional.of(infoVitoria));
        when(municipioService.localizar(foraDoBrasil.getLatitude(), foraDoBrasil.getLongitude()))
            .thenReturn(Optional.empty());

        MunicipioBackfillService.Resultado resultado = service().executar();

        assertThat(resultado.analisados()).isEqualTo(2);
        assertThat(resultado.atualizados()).isEqualTo(1);
        assertThat(vitoria.getMunicipioCodigoIbge()).isEqualTo("3205309");
        assertThat(vitoria.getMunicipioNome()).isEqualTo("Vitória");
        assertThat(vitoria.getUf()).isEqualTo("ES");
        assertThat(vitoria.getIdhm()).isEqualByComparingTo("0.845");
        assertThat(vitoria.getIdhmReferencia()).isEqualTo((short) 2010);
        assertThat(foraDoBrasil.getMunicipioCodigoIbge()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Lead>> captor = ArgumentCaptor.forClass(List.class);
        verify(leadRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(vitoria);
    }

    @Test
    void deveSerIdempotenteQuandoNaoExistiremLeadsPendentes() {
        when(leadRepository.buscarPendentesGeografiaAposId(
            org.mockito.ArgumentMatchers.eq(0L),
            org.mockito.ArgumentMatchers.any(Pageable.class)
        ))
            .thenReturn(List.of());

        MunicipioBackfillService.Resultado resultado = service().executar();

        assertThat(resultado.analisados()).isZero();
        assertThat(resultado.atualizados()).isZero();
        verify(leadRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    private MunicipioBackfillService service() {
        return new MunicipioBackfillService(leadRepository, municipioService);
    }

    private Lead lead(Long id, String latitude, String longitude) {
        Lead lead = new Lead();
        lead.setId(id);
        lead.setLatitude(new BigDecimal(latitude));
        lead.setLongitude(new BigDecimal(longitude));
        return lead;
    }
}
