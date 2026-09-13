package dev.jlm.leadshunter.integracao.pesquisa;

public class GooglePesquisaWebTimeoutException extends GooglePesquisaWebException {

    public GooglePesquisaWebTimeoutException() {
        super("A pesquisa pública do Google excedeu o tempo limite.");
    }

    public GooglePesquisaWebTimeoutException(Throwable cause) {
        super("A pesquisa pública do Google excedeu o tempo limite.", cause);
    }
}
