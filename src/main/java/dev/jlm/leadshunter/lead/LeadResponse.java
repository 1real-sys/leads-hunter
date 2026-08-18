package dev.jlm.leadshunter.lead;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LeadResponse(
    Long id,
    String googlePlaceId,
    String nome,
    CategoriaNegocio categoria,
    String enderecoFormatado,
    String telefone,
    String telefoneNormalizado,
    String whatsappUrl,
    BigDecimal latitude,
    BigDecimal longitude,
    BigDecimal ratingGoogle,
    Integer totalReviews,
    Integer score,
    Temperatura temperatura,
    StatusFunil status,
    String observacoes,
    LocalDateTime ultimoContatoEm,
    LocalDateTime criadoEm,
    LocalDateTime atualizadoEm
) {

    public static LeadResponse from(Lead lead, WhatsAppLinkGenerator whatsAppLinkGenerator) {
        return new LeadResponse(
            lead.getId(),
            lead.getGooglePlaceId(),
            lead.getNome(),
            lead.getCategoria(),
            lead.getEnderecoFormatado(),
            lead.getTelefone(),
            lead.getTelefoneNormalizado(),
            whatsAppLinkGenerator.gerar(lead.getTelefoneNormalizado()),
            lead.getLatitude(),
            lead.getLongitude(),
            lead.getRatingGoogle(),
            lead.getTotalReviews(),
            lead.getScore(),
            lead.getTemperatura(),
            lead.getStatus(),
            lead.getObservacoes(),
            lead.getUltimoContatoEm(),
            lead.getCriadoEm(),
            lead.getAtualizadoEm()
        );
    }
}
