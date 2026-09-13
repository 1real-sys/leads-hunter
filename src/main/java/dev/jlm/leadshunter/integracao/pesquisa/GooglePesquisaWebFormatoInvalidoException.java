package dev.jlm.leadshunter.integracao.pesquisa;

public class GooglePesquisaWebFormatoInvalidoException extends GooglePesquisaWebException {

    public GooglePesquisaWebFormatoInvalidoException() {
        super("O formato da página de pesquisa mudou ou não pôde ser reconhecido.");
    }

    public GooglePesquisaWebFormatoInvalidoException(Throwable cause) {
        super("O formato da página de pesquisa mudou ou não pôde ser reconhecido.", cause);
    }
}
