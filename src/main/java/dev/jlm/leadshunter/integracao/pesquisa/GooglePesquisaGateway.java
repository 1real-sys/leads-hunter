package dev.jlm.leadshunter.integracao.pesquisa;

interface GooglePesquisaGateway {

    GooglePesquisaWebResponse pesquisar(GooglePesquisaWebRequest request);
}
