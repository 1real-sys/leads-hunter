package dev.jlm.leadshunter.busca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jlm.leadshunter.integracao.places.PlacesApiClient;
import dev.jlm.leadshunter.integracao.places.PlacesSearchRequest;
import dev.jlm.leadshunter.integracao.places.PlacesSearchResponse;
import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BuscaServiceTest {

    @Mock
    private BuscaRepository buscaRepository;

    @Mock
    private PlacesApiClient placesApiClient;

    @Test
    void deveBuscarLocaisPersistirResumoERetornarResultados() {
        BuscaRequest request = new BuscaRequest(
            "Centro, Curitiba - PR",
            new BigDecimal("-25.4284"),
            new BigDecimal("-49.2733"),
            5,
            List.of(CategoriaNegocio.PADARIA, CategoriaNegocio.MERCADO)
        );
        PlacesSearchResponse placesResponse = new PlacesSearchResponse(List.of(
            new PlacesSearchResponse.PlaceResult(
                "place-1",
                "Padaria Central",
                CategoriaNegocio.PADARIA,
                "Rua Central, 100",
                new BigDecimal("-25.4300"),
                new BigDecimal("-49.2700"),
                new BigDecimal("4.5"),
                120,
                "OPERATIONAL",
                List.of("bakery")
            )
        ));
        when(placesApiClient.buscarProximos(any(PlacesSearchRequest.class)))
            .thenReturn(placesResponse);
        when(buscaRepository.saveAndFlush(any(Busca.class))).thenAnswer(invocation -> {
            Busca busca = invocation.getArgument(0);
            busca.setId(10L);
            busca.setCriadoEm(LocalDateTime.of(2026, 8, 11, 10, 0));
            return busca;
        });

        BuscaResponse response = new BuscaService(buscaRepository, placesApiClient).criar(request);

        ArgumentCaptor<PlacesSearchRequest> placesRequestCaptor =
            ArgumentCaptor.forClass(PlacesSearchRequest.class);
        verify(placesApiClient).buscarProximos(placesRequestCaptor.capture());
        assertThat(placesRequestCaptor.getValue())
            .usingRecursiveComparison()
            .isEqualTo(new PlacesSearchRequest(
                request.latitude(),
                request.longitude(),
                request.raioKm(),
                request.categorias()
            ));

        ArgumentCaptor<Busca> buscaCaptor = ArgumentCaptor.forClass(Busca.class);
        verify(buscaRepository).saveAndFlush(buscaCaptor.capture());
        assertThat(buscaCaptor.getValue().getCategoriasBuscadas()).isEqualTo("PADARIA,MERCADO");
        assertThat(buscaCaptor.getValue().getTotalEncontrados()).isEqualTo(1);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.totalEncontrados()).isEqualTo(1);
        assertThat(response.leads()).hasSize(1);
        assertThat(response.leads().getFirst().nome()).isEqualTo("Padaria Central");
        assertThat(response.leads().getFirst().categoria()).isEqualTo(CategoriaNegocio.PADARIA);
    }
}
