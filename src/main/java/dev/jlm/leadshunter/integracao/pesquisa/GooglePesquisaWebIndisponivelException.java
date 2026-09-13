package dev.jlm.leadshunter.integracao.pesquisa;

public class GooglePesquisaWebIndisponivelException extends GooglePesquisaWebException {

    public GooglePesquisaWebIndisponivelException() {
        super("A pesquisa pública do Google está indisponível no momento.");
    }

    public GooglePesquisaWebIndisponivelException(String message) {
        super(message);
    }

    public GooglePesquisaWebIndisponivelException(Throwable cause) {
        super("A pesquisa pública do Google está indisponível no momento.", cause);
    }
}
