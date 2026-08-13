package dev.jlm.leadshunter.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import dev.jlm.leadshunter.lead.Temperatura;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScoringServiceTest {

    private final ScoringService service = new ScoringService();

    @Test
    void deveCalcularPontuacaoMaximaDaRegraAtual() {
        ScoringService.Resultado resultado = service.calcular(
            CategoriaNegocio.RESTAURANTE,
            "5527999990000",
            100,
            new BigDecimal("4.8"),
            "OPERATIONAL"
        );

        assertThat(resultado.score()).isEqualTo(95);
        assertThat(resultado.temperatura()).isEqualTo(Temperatura.QUENTE);
    }

    @Test
    void deveClassificarComoFrioQuandoNaoHaSinaisDeQualificacao() {
        ScoringService.Resultado resultado = service.calcular(
            CategoriaNegocio.OUTROS,
            null,
            null,
            null,
            null
        );

        assertThat(resultado.score()).isZero();
        assertThat(resultado.temperatura()).isEqualTo(Temperatura.FRIO);
    }

    @Test
    void deveClassificarComoMornoNoLimiteDeQuarentaPontos() {
        ScoringService.Resultado resultado = service.calcular(
            CategoriaNegocio.PADARIA,
            null,
            11,
            null,
            null
        );

        assertThat(resultado.score()).isEqualTo(40);
        assertThat(resultado.temperatura()).isEqualTo(Temperatura.MORNO);
    }

    @Test
    void deveClassificarComoQuenteNoLimiteDeSetentaPontos() {
        ScoringService.Resultado resultado = service.calcular(
            CategoriaNegocio.MERCADO,
            "552733334444",
            0,
            new BigDecimal("3.5"),
            null
        );

        assertThat(resultado.score()).isEqualTo(70);
        assertThat(resultado.temperatura()).isEqualTo(Temperatura.QUENTE);
    }

    @ParameterizedTest
    @CsvSource({
        "0, 5",
        "10, 5",
        "11, 10",
        "50, 10",
        "51, 15"
    })
    void deveCalcularPontosPorQuantidadeDeReviews(Integer totalReviews, Integer pontos) {
        ScoringService.Resultado resultado = service.calcular(
            CategoriaNegocio.OUTROS,
            null,
            totalReviews,
            null,
            null
        );

        assertThat(resultado.score()).isEqualTo(pontos);
    }

    @ParameterizedTest
    @CsvSource({
        "1.0, 5",
        "3.49, 5",
        "3.5, 10",
        "4.49, 10",
        "4.5, 15",
        "5.0, 15"
    })
    void deveCalcularPontosPorRating(String rating, Integer pontos) {
        ScoringService.Resultado resultado = service.calcular(
            CategoriaNegocio.OUTROS,
            null,
            null,
            new BigDecimal(rating),
            null
        );

        assertThat(resultado.score()).isEqualTo(pontos);
    }

    @Test
    void deveReconhecerStatusOperacionalSemDiferenciarMaiusculas() {
        ScoringService.Resultado resultado = service.calcular(
            CategoriaNegocio.OUTROS,
            null,
            null,
            null,
            "operational"
        );

        assertThat(resultado.score()).isEqualTo(10);
        assertThat(resultado.temperatura()).isEqualTo(Temperatura.FRIO);
    }
}
