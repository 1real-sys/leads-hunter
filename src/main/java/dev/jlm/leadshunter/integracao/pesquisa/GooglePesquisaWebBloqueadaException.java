package dev.jlm.leadshunter.integracao.pesquisa;

public class GooglePesquisaWebBloqueadaException extends GooglePesquisaWebException {

    public GooglePesquisaWebBloqueadaException() {
        super("A fonte de pesquisa bloqueou temporariamente o acesso automatizado.");
    }
}
