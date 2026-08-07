package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record BuscaRequest(
    @Size(max = 255)
    String enderecoBase,

    @NotNull
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    BigDecimal latitude,

    @NotNull
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    BigDecimal longitude,

    @NotNull
    @Positive
    @Max(20)
    Integer raioKm,

    @NotEmpty
    List<@NotNull CategoriaNegocio> categorias
) {
}
