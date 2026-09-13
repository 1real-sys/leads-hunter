package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.integracao.pesquisa.PesquisaLeadDados;

public record BuscaInformacoesLead(
    Long leadId,
    PesquisaLeadDados dados,
    String observacoes
) {

    public BuscaInformacoesLead {
        if (leadId == null || dados == null) {
            throw new IllegalArgumentException("leadId e dados são obrigatórios");
        }
    }
}
