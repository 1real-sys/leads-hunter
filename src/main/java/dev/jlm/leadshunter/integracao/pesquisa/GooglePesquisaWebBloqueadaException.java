package dev.jlm.leadshunter.integracao.pesquisa;

public class GooglePesquisaWebBloqueadaException extends GooglePesquisaWebException {

    public GooglePesquisaWebBloqueadaException() {
        super("O Google bloqueou temporariamente a pesquisa automatizada.");
    }
}
