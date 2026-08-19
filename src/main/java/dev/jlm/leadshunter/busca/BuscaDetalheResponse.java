package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import dev.jlm.leadshunter.lead.StatusFunil;
import dev.jlm.leadshunter.lead.Temperatura;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BuscaDetalheResponse(
    Long id,
    String enderecoBase,
    BigDecimal latitude,
    BigDecimal longitude,
    Integer raioKm,
    List<CategoriaNegocio> categorias,
    Integer totalEncontrados,
    LocalDateTime criadoEm,
    List<LeadHistoricoResponse> leads
) {

    public record LeadHistoricoResponse(
        Long id,
        String nome,
        CategoriaNegocio categoria,
        String enderecoFormatado,
        String telefone,
        String whatsappUrl,
        Integer scoreNaBusca,
        Temperatura temperaturaNaBusca,
        StatusFunil status,
        String observacoes,
        LocalDateTime ultimoContatoEm
    ) {
    }
}
