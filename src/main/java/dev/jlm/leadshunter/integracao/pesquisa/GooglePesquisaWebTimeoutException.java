package dev.jlm.leadshunter.integracao.pesquisa;

public class GooglePesquisaWebTimeoutException extends GooglePesquisaWebException {

    public GooglePesquisaWebTimeoutException() {
        super("A pesquisa externa excedeu o tempo limite.");
    }

    public GooglePesquisaWebTimeoutException(Throwable cause) {
        super("A pesquisa externa excedeu o tempo limite.", cause);
    }
}
