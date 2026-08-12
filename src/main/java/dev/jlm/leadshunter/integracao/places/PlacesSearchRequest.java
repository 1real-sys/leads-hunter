package dev.jlm.leadshunter.integracao.places;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.math.BigDecimal;
import java.util.List;

public record PlacesSearchRequest(
    BigDecimal latitude,
    BigDecimal longitude,
    Integer raioKm,
    List<CategoriaNegocio> categorias
) {
}
