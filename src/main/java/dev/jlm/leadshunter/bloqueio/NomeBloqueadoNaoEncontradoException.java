package dev.jlm.leadshunter.bloqueio;

public class NomeBloqueadoNaoEncontradoException extends RuntimeException {

    public NomeBloqueadoNaoEncontradoException(Long id) {
        super("Bloqueio não encontrado: " + id);
    }
}
