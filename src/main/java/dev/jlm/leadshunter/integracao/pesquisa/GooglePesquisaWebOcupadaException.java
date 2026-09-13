package dev.jlm.leadshunter.integracao.pesquisa;

public class GooglePesquisaWebOcupadaException extends GooglePesquisaWebException {

    public GooglePesquisaWebOcupadaException() {
        super("A fila local da pesquisa inteligente está ocupada. Tente novamente em instantes.");
    }
}
