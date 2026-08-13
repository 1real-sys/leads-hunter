package dev.jlm.leadshunter.scoring;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import dev.jlm.leadshunter.lead.Temperatura;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class ScoringService {

    private static final BigDecimal RATING_MEDIO = new BigDecimal("3.5");
    private static final BigDecimal RATING_ALTO = new BigDecimal("4.5");

    public Resultado calcular(
        CategoriaNegocio categoria,
        String telefoneNormalizado,
        Integer totalReviews,
        BigDecimal ratingGoogle,
        String businessStatus
    ) {
        int score = 0;
        score += pontosCategoria(categoria);
        score += pontosTelefone(telefoneNormalizado);
        score += pontosReviews(totalReviews);
        score += pontosRating(ratingGoogle);
        score += pontosFuncionamento(businessStatus);

        return new Resultado(score, calcularTemperatura(score));
    }

    private int pontosCategoria(CategoriaNegocio categoria) {
        return categoria != null && categoria != CategoriaNegocio.OUTROS ? 30 : 0;
    }

    private int pontosTelefone(String telefoneNormalizado) {
        return telefoneNormalizado != null && !telefoneNormalizado.isBlank() ? 25 : 0;
    }

    private int pontosReviews(Integer totalReviews) {
        if (totalReviews == null || totalReviews < 0) {
            return 0;
        }
        if (totalReviews <= 10) {
            return 5;
        }
        if (totalReviews <= 50) {
            return 10;
        }
        return 15;
    }

    private int pontosRating(BigDecimal ratingGoogle) {
        if (ratingGoogle == null) {
            return 0;
        }
        if (ratingGoogle.compareTo(RATING_MEDIO) < 0) {
            return 5;
        }
        if (ratingGoogle.compareTo(RATING_ALTO) < 0) {
            return 10;
        }
        return 15;
    }

    private int pontosFuncionamento(String businessStatus) {
        return "OPERATIONAL".equalsIgnoreCase(businessStatus) ? 10 : 0;
    }

    private Temperatura calcularTemperatura(int score) {
        if (score >= 70) {
            return Temperatura.QUENTE;
        }
        if (score >= 40) {
            return Temperatura.MORNO;
        }
        return Temperatura.FRIO;
    }

    public record Resultado(Integer score, Temperatura temperatura) {
    }
}
