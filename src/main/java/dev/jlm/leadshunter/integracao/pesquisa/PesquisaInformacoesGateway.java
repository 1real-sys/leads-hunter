package dev.jlm.leadshunter.integracao.pesquisa;

@FunctionalInterface
public interface PesquisaInformacoesGateway {

    PesquisaInformacoesWebResultado pesquisar(PesquisaLeadDados lead, boolean usarBrave);

    default PesquisaInformacoesWebResultado pesquisar(PesquisaLeadDados lead) {
        return pesquisar(lead, true);
    }
}
