package dev.jlm.leadshunter.cnpj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
class CnpjMatchAplicacaoBarreiraTest {

    private static final LocalDate COMPETENCIA = LocalDate.of(2026, 9, 8);

    @TempDir
    Path temporario;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private CnpjService cnpjService;

    @Test
    void deveVetarTodaPromocaoQuandoUmaLinhaForErrada() throws Exception {
        CnpjMatchPolicy policy = CnpjMatchPolicy.habilitadaParaMunicipios("3204708");
        Artefatos artefatos = gerarRelatorio(policy);
        escreverRevisao(artefatos, List.of(
            revisao(653L, "51526147000150", "CERTO"),
            revisao(654L, "11111111000191", "ERRADO")
        ));

        CnpjMatchAplicacaoService.ResultadoAplicacao resultado = service(policy).aplicar(
            artefatos.relatorio(), artefatos.revisao(), 2
        );

        assertThat(resultado.itens())
            .noneMatch(item -> item.status() == CnpjMatchAplicacaoService.StatusAplicacao.APLICADO);
        assertThat(resultado.revisao().totalAprovacoes()).isEqualTo(2);
        assertThat(resultado.revisao().certos()).isEqualTo(1);
        assertThat(resultado.revisao().errados()).isEqualTo(1);
        assertThat(resultado.revisao().taxaCobertura()).isEqualByComparingTo("1.0000");
        verifyNoInteractions(leadRepository, cnpjService);
    }

    @Test
    void deveVetarTodaPromocaoQuandoARevisaoNaoCobrirTodosOsAprovados() throws Exception {
        CnpjMatchPolicy policy = CnpjMatchPolicy.habilitadaParaMunicipios("3204708");
        Artefatos artefatos = gerarRelatorio(policy);
        escreverRevisao(artefatos, List.of(revisao(653L, "51526147000150", "CERTO")));

        CnpjMatchAplicacaoService.ResultadoAplicacao resultado = service(policy).aplicar(
            artefatos.relatorio(), artefatos.revisao(), 2
        );

        assertThat(resultado.itens())
            .noneMatch(item -> item.status() == CnpjMatchAplicacaoService.StatusAplicacao.APLICADO);
        assertThat(resultado.revisao().totalAprovacoes()).isEqualTo(2);
        assertThat(resultado.revisao().certos()).isEqualTo(1);
        assertThat(resultado.revisao().pendentes()).isEqualTo(1);
        assertThat(resultado.revisao().taxaCobertura()).isEqualByComparingTo("0.5000");
        verifyNoInteractions(leadRepository, cnpjService);
    }

    @Test
    void deveAplicarSomenteCertoEExcluirInconclusivoDaPrecisao() throws Exception {
        CnpjMatchPolicy policy = CnpjMatchPolicy.habilitadaParaMunicipios("3204708");
        Artefatos artefatos = gerarRelatorio(policy);
        escreverRevisao(artefatos, List.of(
            revisao(653L, "51526147000150", "CERTO"),
            revisao(654L, "11111111000191", "INCONCLUSIVO")
        ));
        prepararAplicacaoValida(653L, "51526147000150");

        CnpjMatchAplicacaoService.ResultadoAplicacao resultado = service(policy).aplicar(
            artefatos.relatorio(), artefatos.revisao(), 2
        );

        assertThat(resultado.itens()).extracting(CnpjMatchAplicacaoService.ItemAplicado::status)
            .containsExactly(
                CnpjMatchAplicacaoService.StatusAplicacao.APLICADO,
                CnpjMatchAplicacaoService.StatusAplicacao.REJEITADO_REVISAO_NAO_CERTO
            );
        assertThat(resultado.revisao().totalAprovacoes()).isEqualTo(2);
        assertThat(resultado.revisao().certos()).isEqualTo(1);
        assertThat(resultado.revisao().inconclusivos()).isEqualTo(1);
        assertThat(resultado.revisao().taxaCobertura()).isEqualByComparingTo("0.5000");
        verify(leadRepository).save(any());
    }

    private Artefatos gerarRelatorio(CnpjMatchPolicy policy) {
        Path relatorio = temporario.resolve("relatorio.jsonl");
        Path revisao = temporario.resolve("revisao.csv");
        CnpjMatchRelatorioWriter.RelatorioGerado gerado = new CnpjMatchRelatorioWriter(
            new ObjectMapper()
        ).escrever(
            relatorio,
            revisao,
            List.of(
                linha(653L, "51526147000150"),
                linha(654L, "11111111000191")
            ),
            policy
        );
        return new Artefatos(relatorio, revisao, gerado.hashSha256());
    }

    private void escreverRevisao(Artefatos artefatos, List<String> linhas) throws Exception {
        Files.writeString(
            artefatos.revisao(),
            "leadId,cnpj,competencia,classificacao,revisao,evidenciaUrl,evidencia,hashRelatorio\n"
                + String.join("\n", linhas) + "\n",
            StandardCharsets.UTF_8
        );
    }

    private String revisao(Long leadId, String cnpj, String rotulo) {
        return String.join(",",
            csv(String.valueOf(leadId)),
            csv(cnpj),
            csv(COMPETENCIA.toString()),
            csv("ENDERECO_UNICO"),
            csv(rotulo),
            csv("https://exemplo.test/fonte"),
            csv("vínculo físico confirmado"),
            csv(CnpjMatchRelatorioWriter.sha256(temporario.resolve("relatorio.jsonl")))
        );
    }

    private void prepararAplicacaoValida(Long leadId, String cnpj) {
        Lead lead = new Lead();
        lead.setId(leadId);
        CnpjService.Correspondencia correspondencia = correspondencia(cnpj);
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));
        when(cnpjService.avaliarParaDiagnostico(lead)).thenReturn(
            new CnpjService.AvaliacaoMatch(
                CnpjMatchClassificacao.ENDERECO_UNICO,
                true,
                correspondencia,
                List.of(),
                correspondencia.confianca(),
                null,
                null,
                false,
                false
            )
        );
    }

    private CnpjMatchAplicacaoService service(CnpjMatchPolicy policy) {
        return new CnpjMatchAplicacaoService(
            leadRepository,
            cnpjService,
            policy,
            new ObjectMapper()
        );
    }

    private static CnpjMatchRelatorioLinha linha(Long leadId, String cnpj) {
        CnpjService.Correspondencia correspondencia = correspondencia(cnpj);
        return new CnpjMatchRelatorioLinha(
            "lead",
            leadId,
            "place-" + leadId,
            CnpjMatchClassificacao.ENDERECO_UNICO,
            true,
            cnpj,
            COMPETENCIA,
            CnpjOrigem.ENDERECO_EXATO,
            correspondencia.confianca(),
            null,
            null,
            null,
            CnpjMatchClassificacao.SEM_CORRESPONDENCIA,
            cnpj,
            false,
            "48",
            CnpjNumeroNormalizer.Classificacao.NUMERO,
            List.of()
        );
    }

    private static CnpjService.Correspondencia correspondencia(String cnpj) {
        return new CnpjService.Correspondencia(
            cnpj,
            "EMPRESA TESTE LTDA",
            COMPETENCIA,
            new BigDecimal("0.7000"),
            CnpjOrigem.ENDERECO_EXATO
        );
    }

    private static String csv(String valor) {
        return '"' + valor.replace("\"", "\"\"") + '"';
    }

    private record Artefatos(Path relatorio, Path revisao, String hash) {
    }
}
