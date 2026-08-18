package dev.jlm.leadshunter.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class LeadServiceTest {

    @Mock
    private LeadRepository leadRepository;

    @Test
    void deveListarLeadsComFiltrosEMapearResposta() {
        Lead lead = criarLeadCompleto();
        when(leadRepository.findAll(any(Example.class), any(Sort.class)))
            .thenReturn(List.of(lead));
        LeadService service = new LeadService(leadRepository);

        List<LeadResponse> resposta = service.listar(
            StatusFunil.QUALIFICADO,
            CategoriaNegocio.PADARIA,
            Temperatura.QUENTE
        );

        ArgumentCaptor<Example<Lead>> exampleCaptor = ArgumentCaptor.forClass(Example.class);
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(leadRepository).findAll(exampleCaptor.capture(), sortCaptor.capture());
        assertThat(exampleCaptor.getValue().getProbe().getStatus())
            .isEqualTo(StatusFunil.QUALIFICADO);
        assertThat(exampleCaptor.getValue().getProbe().getCategoria())
            .isEqualTo(CategoriaNegocio.PADARIA);
        assertThat(exampleCaptor.getValue().getProbe().getTemperatura())
            .isEqualTo(Temperatura.QUENTE);
        assertThat(sortCaptor.getValue().getOrderFor("score").getDirection())
            .isEqualTo(Sort.Direction.DESC);

        assertThat(resposta).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(15L);
            assertThat(item.googlePlaceId()).isEqualTo("place-15");
            assertThat(item.nome()).isEqualTo("Padaria Central");
            assertThat(item.telefoneNormalizado()).isEqualTo("5527999990000");
            assertThat(item.score()).isEqualTo(95);
            assertThat(item.status()).isEqualTo(StatusFunil.QUALIFICADO);
            assertThat(item.observacoes()).isEqualTo("Retornar na sexta");
        });
    }

    @Test
    void deveBuscarLeadPorId() {
        Lead lead = criarLeadCompleto();
        when(leadRepository.findById(15L)).thenReturn(Optional.of(lead));

        LeadResponse resposta = new LeadService(leadRepository).buscarPorId(15L);

        assertThat(resposta.id()).isEqualTo(15L);
        assertThat(resposta.nome()).isEqualTo("Padaria Central");
    }

    @Test
    void deveRetornarErroQuandoLeadNaoExistir() {
        when(leadRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new LeadService(leadRepository).buscarPorId(99L))
            .isInstanceOf(LeadNaoEncontradoException.class)
            .hasMessageContaining("99");
    }

    @Test
    void deveAtualizarDadosComerciaisDoLead() {
        Lead lead = criarLeadCompleto();
        LocalDateTime novoContato = LocalDateTime.of(2026, 8, 17, 9, 30);
        AtualizarLeadRequest request = new AtualizarLeadRequest(
            StatusFunil.CONTATADO,
            "Cliente pediu uma proposta",
            novoContato
        );
        when(leadRepository.findById(15L)).thenReturn(Optional.of(lead));
        when(leadRepository.saveAndFlush(lead)).thenReturn(lead);

        LeadResponse resposta = new LeadService(leadRepository).atualizar(15L, request);

        assertThat(lead.getStatus()).isEqualTo(StatusFunil.CONTATADO);
        assertThat(lead.getObservacoes()).isEqualTo("Cliente pediu uma proposta");
        assertThat(lead.getUltimoContatoEm()).isEqualTo(novoContato);
        assertThat(lead.getGooglePlaceId()).isEqualTo("place-15");
        assertThat(resposta.status()).isEqualTo(StatusFunil.CONTATADO);
        verify(leadRepository).saveAndFlush(lead);
    }

    @Test
    void devePreservarCamposOmitidosNaAtualizacao() {
        Lead lead = criarLeadCompleto();
        LocalDateTime contatoAnterior = lead.getUltimoContatoEm();
        when(leadRepository.findById(15L)).thenReturn(Optional.of(lead));
        when(leadRepository.saveAndFlush(lead)).thenReturn(lead);

        new LeadService(leadRepository).atualizar(
            15L,
            new AtualizarLeadRequest(StatusFunil.GANHO, null, null)
        );

        assertThat(lead.getStatus()).isEqualTo(StatusFunil.GANHO);
        assertThat(lead.getObservacoes()).isEqualTo("Retornar na sexta");
        assertThat(lead.getUltimoContatoEm()).isEqualTo(contatoAnterior);
    }

    @Test
    void deveRetornarErroAoAtualizarLeadInexistente() {
        when(leadRepository.findById(99L)).thenReturn(Optional.empty());
        AtualizarLeadRequest request = new AtualizarLeadRequest(StatusFunil.CONTATADO, null, null);

        assertThatThrownBy(() -> new LeadService(leadRepository).atualizar(99L, request))
            .isInstanceOf(LeadNaoEncontradoException.class)
            .hasMessageContaining("99");
        verify(leadRepository, never()).saveAndFlush(any());
    }

    private Lead criarLeadCompleto() {
        Lead lead = new Lead();
        lead.setId(15L);
        lead.setGooglePlaceId("place-15");
        lead.setNome("Padaria Central");
        lead.setCategoria(CategoriaNegocio.PADARIA);
        lead.setEnderecoFormatado("Rua Central, 100");
        lead.setTelefone("(27) 99999-0000");
        lead.setTelefoneNormalizado("5527999990000");
        lead.setLatitude(new BigDecimal("-20.3155"));
        lead.setLongitude(new BigDecimal("-40.3128"));
        lead.setRatingGoogle(new BigDecimal("4.8"));
        lead.setTotalReviews(120);
        lead.setScore(95);
        lead.setTemperatura(Temperatura.QUENTE);
        lead.setStatus(StatusFunil.QUALIFICADO);
        lead.setObservacoes("Retornar na sexta");
        lead.setUltimoContatoEm(LocalDateTime.of(2026, 8, 16, 14, 0));
        lead.setCriadoEm(LocalDateTime.of(2026, 8, 15, 10, 0));
        lead.setAtualizadoEm(LocalDateTime.of(2026, 8, 16, 14, 0));
        return lead;
    }
}
