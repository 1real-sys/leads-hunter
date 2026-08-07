package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BuscaResponse(
    Long id,
    String enderecoBase,
    BigDecimal latitude,
    BigDecimal longitude,
    Integer raioKm,
    List<CategoriaNegocio> categorias,
    Integer totalEncontrados,
    LocalDateTime criadoEm,
    List<LeadEncontradoResponse> leads
) {

    public record LeadEncontradoResponse(
        Long id,
        String nome,
        CategoriaNegocio categoria,
        String enderecoFormatado,
        String telefone,
        Integer score,
        String temperatura
    ) {
    }
}
