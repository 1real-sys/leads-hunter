package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BuscaResumoResponse(
    Long id,
    String enderecoBase,
    BigDecimal latitude,
    BigDecimal longitude,
    Integer raioKm,
    List<CategoriaNegocio> categorias,
    Integer totalEncontrados,
    LocalDateTime criadoEm
) {
}
