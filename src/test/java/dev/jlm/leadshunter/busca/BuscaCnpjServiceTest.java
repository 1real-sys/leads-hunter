package dev.jlm.leadshunter.busca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import dev.jlm.leadshunter.cnpj.CnpjService;
import dev.jlm.leadshunter.lead.Lead;
import dev.jlm.leadshunter.lead.StatusFunil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BuscaCnpjServiceTest {
    @Mock private BuscaRepository buscaRepository;
    @Mock private BuscaLeadRepository buscaLeadRepository;
    @Mock private CnpjService cnpjService;
    @InjectMocks private BuscaCnpjService service;

    @Test
    void devePreencherSomenteCorrespondidosEIgnorarCnpjExistente() {
        Lead preenchido = new Lead();
        preenchido.setCnpj("12345678000190");
        preenchido.setRazaoSocial("Razão anterior");
        preenchido.setCnpjDataBase(LocalDate.of(2020, 1, 1));
        Lead anterior = new Lead();
        org.springframework.beans.BeanUtils.copyProperties(preenchido, anterior);
        Lead encontrado = new Lead();
        encontrado.setStatus(StatusFunil.CONTATADO);
        encontrado.setObservacoes("Retornar amanhã");
        encontrado.setUltimoContatoEm(LocalDateTime.of(2026, 9, 1, 12, 0));
        encontrado.setScore(80);
        Lead semCorrespondencia = new Lead();
        when(buscaRepository.existsById(42L)).thenReturn(true);
        when(buscaLeadRepository.findByBuscaIdOrderByScoreNaBuscaDesc(42L))
            .thenReturn(List.of(vinculo(preenchido), vinculo(encontrado), vinculo(semCorrespondencia)));
        when(cnpjService.corresponder(encontrado)).thenReturn(Optional.of(new CnpjService.Correspondencia(
            "43869215000156", "Empresa encontrada", LocalDate.of(2026, 9, 8), new BigDecimal("0.9500")
        )));
        when(cnpjService.corresponder(semCorrespondencia)).thenReturn(Optional.empty());

        assertThat(service.buscarCnpj(42L)).isEqualTo(new BuscaCnpjResponse(3, 1, 1, 1));
        assertThat(preenchido).usingRecursiveComparison().isEqualTo(anterior);
        verify(cnpjService, never()).corresponder(preenchido);
        assertThat(encontrado.getCnpj()).isEqualTo("43869215000156");
        assertThat(encontrado.getRazaoSocial()).isEqualTo("Empresa encontrada");
        assertThat(encontrado.getCnpjDataBase()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(encontrado.getCnpjConfianca()).isEqualByComparingTo("0.95");
        assertThat(encontrado.getCnpjCorrespondidoEm()).isNotNull();
        assertThat(encontrado.getStatus()).isEqualTo(StatusFunil.CONTATADO);
        assertThat(encontrado.getObservacoes()).isEqualTo("Retornar amanhã");
        assertThat(encontrado.getUltimoContatoEm()).isEqualTo(LocalDateTime.of(2026, 9, 1, 12, 0));
        assertThat(encontrado.getScore()).isEqualTo(80);
        assertThat(semCorrespondencia.getCnpj()).isNull();
        assertThat(semCorrespondencia.getRazaoSocial()).isNull();
        assertThat(semCorrespondencia.getCnpjCorrespondidoEm()).isNull();
    }

    @Test
    void deveRejeitarBuscaInexistenteAntesDeCarregarLeads() {
        assertThatThrownBy(() -> service.buscarCnpj(999L)).isInstanceOf(BuscaNaoEncontradaException.class);
        verifyNoInteractions(buscaLeadRepository, cnpjService);
    }

    @Test
    void deveRetornarZerosParaBuscaVazia() {
        when(buscaRepository.existsById(42L)).thenReturn(true);
        when(buscaLeadRepository.findByBuscaIdOrderByScoreNaBuscaDesc(42L)).thenReturn(List.of());
        assertThat(service.buscarCnpj(42L)).isEqualTo(new BuscaCnpjResponse(0, 0, 0, 0));
        verifyNoInteractions(cnpjService);
    }

    private BuscaLead vinculo(Lead lead) {
        BuscaLead vinculo = new BuscaLead();
        vinculo.setLead(lead);
        return vinculo;
    }
}
