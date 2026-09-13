package dev.jlm.leadshunter.integracao.pesquisa;

public class GooglePesquisaWebFormatoInvalidoException extends GooglePesquisaWebException {

    public GooglePesquisaWebFormatoInvalidoException() {
        super("O formato da resposta de pesquisa não pôde ser reconhecido.");
    }

    public GooglePesquisaWebFormatoInvalidoException(Throwable cause) {
        super("O formato da resposta de pesquisa não pôde ser reconhecido.", cause);
    }
}
