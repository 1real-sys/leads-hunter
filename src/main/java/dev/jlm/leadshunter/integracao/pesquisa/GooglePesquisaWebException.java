package dev.jlm.leadshunter.integracao.pesquisa;

public abstract class GooglePesquisaWebException extends RuntimeException {

    protected GooglePesquisaWebException(String message) {
        super(message);
    }

    protected GooglePesquisaWebException(String message, Throwable cause) {
        super(message, cause);
    }
}
