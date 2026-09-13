package dev.jlm.leadshunter.integracao.pesquisa;

@FunctionalInterface
public interface PesquisaInformacoesGateway {

    PesquisaInformacoesWebResultado pesquisar(PesquisaLeadDados lead);
}
