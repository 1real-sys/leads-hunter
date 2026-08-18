package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

public record BuscaCacheKey(
    BigDecimal latitude,
    BigDecimal longitude,
    Integer raioKm,
    List<CategoriaNegocio> categorias
) {

    private static final int CASAS_DECIMAIS_COORDENADA = 4;

    public BuscaCacheKey {
        categorias = List.copyOf(categorias);
    }

    public static BuscaCacheKey from(BuscaRequest request) {
        List<CategoriaNegocio> categoriasOrdenadas = request.categorias().stream()
            .distinct()
            .sorted(Comparator.comparing(CategoriaNegocio::name))
            .toList();

        return new BuscaCacheKey(
            arredondar(request.latitude()),
            arredondar(request.longitude()),
            request.raioKm(),
            categoriasOrdenadas
        );
    }

    private static BigDecimal arredondar(BigDecimal coordenada) {
        return coordenada.setScale(CASAS_DECIMAIS_COORDENADA, RoundingMode.HALF_UP);
    }
}
