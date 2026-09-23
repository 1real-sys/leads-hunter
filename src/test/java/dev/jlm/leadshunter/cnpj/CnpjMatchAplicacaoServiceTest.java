package dev.jlm.leadshunter.cnpj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jlm.leadshunter.lead.Lead;
import dev.jlm.leadshunter.lead.LeadRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CnpjMatchAplicacaoServiceTest {

    @TempDir
    Path temporario;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private CnpjService cnpjService;

    @Test
    void deveAplicarSomenteLinhaCertaComEvidenciaEHashValido() throws Exception {
        CnpjMatchPolicy policy = CnpjMatchPolicy.habilitadaParaMunicipios("3204708");
        Path relatorio = temporario.resolve("relatorio.jsonl");
        Path revisao = temporario.resolve("revisao.csv");
        CnpjService.Correspondencia correspondencia = new CnpjService.Correspondencia(
            "51526147000150",
            "DROGARIA DE SOUSA ALVES LTDA",
            LocalDate.of(2026, 9, 8),
            new BigDecimal("0.7000"),
            CnpjOrigem.ENDERECO_EXATO
        );
        CnpjMatchRelatorioWriter.RelatorioGerado gerado = new CnpjMatchRelatorioWriter(
            new ObjectMapper()
        ).escrever(
            relatorio,
            revisao,
            List.of(new CnpjMatchRelatorioLinha(
                "lead",
                653L,
                "place-653",
                CnpjMatchClassificacao.ENDERECO_UNICO,
                true,
                correspondencia.cnpj(),
                correspondencia.dataBase(),
                correspondencia.origem(),
                correspondencia.confianca(),
                null,
                null,
                null,
                CnpjMatchClassificacao.SEM_CORRESPONDENCIA,
                correspondencia.cnpj(),
                false,
                "48",
                CnpjNumeroNormalizer.Classificacao.NUMERO,
                List.of()
            )),
            policy
        );
        Files.writeString(
            revisao,
            "leadId,cnpj,competencia,classificacao,revisao,evidenciaUrl,evidencia,hashRelatorio\n"
                + csv("653") + ","
                + csv(correspondencia.cnpj()) + ","
                + csv("2026-09-08") + ","
                + csv("ENDERECO_UNICO") + ","
                + csv("CERTO") + ","
                + csv("https://exemplo.test/fonte") + ","
                + csv("endereço e CNPJ confirmados") + ","
                + csv(gerado.hashSha256()) + "\n",
            StandardCharsets.UTF_8
        );

        Lead lead = new Lead();
        lead.setId(653L);
        when(leadRepository.findById(653L)).thenReturn(Optional.of(lead));
        when(cnpjService.avaliarParaDiagnostico(lead)).thenReturn(new CnpjService.AvaliacaoMatch(
            CnpjMatchClassificacao.ENDERECO_UNICO,
            true,
            correspondencia,
            List.of(),
            correspondencia.confianca(),
            null,
            null,
            false,
            false
        ));

        CnpjMatchAplicacaoService.ResultadoAplicacao resultado =
            new CnpjMatchAplicacaoService(
                leadRepository,
                cnpjService,
                policy,
                new ObjectMapper()
            ).aplicar(relatorio, revisao, 1);

        assertThat(resultado.hashRelatorio()).isEqualTo(gerado.hashSha256());
        assertThat(resultado.revisao().totalAprovacoes()).isEqualTo(1);
        assertThat(resultado.revisao().certos()).isEqualTo(1);
        assertThat(resultado.revisao().taxaCobertura()).isEqualByComparingTo("1.0000");
        assertThat(resultado.itens()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(
                CnpjMatchAplicacaoService.StatusAplicacao.APLICADO
            );
            assertThat(item.leadId()).isEqualTo(653L);
        });
        assertThat(lead.getCnpj()).isEqualTo(correspondencia.cnpj());
        assertThat(lead.getCnpjOrigem()).isEqualTo(CnpjOrigem.ENDERECO_EXATO);
        verify(leadRepository).save(lead);
    }

    private static String csv(String valor) {
        return '"' + valor.replace("\"", "\"\"") + '"';
    }
}
