package dev.jlm.leadshunter.cnpj;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class CnpjMatchRelatorioWriterTest {

    @TempDir
    Path temporario;

    @Test
    void deveEscreverJsonlCsvEHashDoConteudoExato() throws Exception {
        Path jsonl = temporario.resolve("relatorio.jsonl");
        Path csv = temporario.resolve("revisao.csv");
        CnpjMatchRelatorioLinha linha = new CnpjMatchRelatorioLinha(
            "lead",
            653L,
            "place-653",
            CnpjMatchClassificacao.ENDERECO_UNICO,
            true,
            "51526147000150",
            LocalDate.of(2026, 9, 8),
            CnpjOrigem.ENDERECO_EXATO,
            new BigDecimal("0.7000"),
            null,
            null,
            null,
            CnpjMatchClassificacao.SEM_CORRESPONDENCIA,
            "51526147000150",
            false,
            "48",
            CnpjNumeroNormalizer.Classificacao.NUMERO,
            List.of()
        );

        CnpjMatchRelatorioWriter.RelatorioGerado resultado = new CnpjMatchRelatorioWriter(
            new ObjectMapper()
        ).escrever(
            jsonl,
            csv,
            List.of(linha),
            List.of(new CnpjNumeroNormalizer.NumeroDescartado(
                "O",
                "O",
                CnpjNumeroNormalizer.Classificacao.NUMERO_DESCONHECIDO,
                30
            )),
            CnpjMatchPolicy.habilitadaParaMunicipios("3204708")
        );

        assertThat(resultado.hashSha256()).isEqualTo(CnpjMatchRelatorioWriter.sha256(jsonl));
        assertThat(Files.readString(jsonl, StandardCharsets.UTF_8))
            .contains(
                "cnpj-match-report-v1",
                "ENDERECO_UNICO",
                "numerosDescartados",
                "NUMERO_DESCONHECIDO",
                "\"quantidade\":30"
            );
        assertThat(Files.readString(csv, StandardCharsets.UTF_8))
            .contains("leadId,cnpj,competencia", "51526147000150", resultado.hashSha256());
    }
}
