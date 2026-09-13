package dev.jlm.leadshunter.integracao.pesquisa;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import dev.jlm.leadshunter.lead.Lead;

public record PesquisaLeadDados(
    String googlePlaceId,
    String nome,
    CategoriaNegocio categoria,
    String enderecoFormatado,
    String logradouro,
    String numero,
    String bairro,
    String municipio,
    String uf,
    String telefoneNormalizado,
    String cnpj,
    String razaoSocial
) {

    public PesquisaLeadDados {
        if (googlePlaceId == null || googlePlaceId.isBlank()) {
            throw new IllegalArgumentException("googlePlaceId é obrigatório");
        }
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome é obrigatório");
        }
        if (categoria == null) {
            throw new IllegalArgumentException("categoria é obrigatória");
        }
    }

    public static PesquisaLeadDados de(Lead lead) {
        if (lead == null) {
            throw new IllegalArgumentException("lead é obrigatório");
        }
        return new PesquisaLeadDados(
            lead.getGooglePlaceId(),
            lead.getNome(),
            lead.getCategoria(),
            lead.getEnderecoFormatado(),
            lead.getLogradouro(),
            lead.getNumero(),
            lead.getBairro(),
            lead.getMunicipioNome(),
            lead.getUf(),
            lead.getTelefoneNormalizado(),
            lead.getCnpj(),
            lead.getRazaoSocial()
        );
    }
}
