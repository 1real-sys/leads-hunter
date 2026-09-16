package dev.jlm.leadshunter.integracao.pesquisa;

import dev.jlm.leadshunter.lead.CategoriaNegocio;

public record GooglePesquisaWebRequest(
    String googlePlaceId,
    String nome,
    CategoriaNegocio categoria,
    String enderecoFormatado,
    String municipio,
    String uf,
    TipoPesquisaWeb tipo,
    ConfirmacaoPerfilInstagram confirmacao
) {

    public GooglePesquisaWebRequest(String googlePlaceId, String nome, CategoriaNegocio categoria,
                                    String enderecoFormatado, String municipio, String uf, TipoPesquisaWeb tipo) {
        this(googlePlaceId, nome, categoria, enderecoFormatado, municipio, uf, tipo, null);
    }

    public GooglePesquisaWebRequest {
        if (googlePlaceId == null || googlePlaceId.isBlank()) {
            throw new IllegalArgumentException("googlePlaceId é obrigatório");
        }
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome é obrigatório");
        }
        if (categoria == null) {
            throw new IllegalArgumentException("categoria é obrigatória");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("tipo é obrigatório");
        }
        if (confirmacao != null && tipo != TipoPesquisaWeb.INSTAGRAM) {
            throw new IllegalArgumentException("Confirmação exige pesquisa de Instagram");
        }
    }
}
