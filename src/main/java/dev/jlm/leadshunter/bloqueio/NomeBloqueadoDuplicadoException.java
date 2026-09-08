package dev.jlm.leadshunter.bloqueio;

public class NomeBloqueadoDuplicadoException extends RuntimeException {

    public NomeBloqueadoDuplicadoException() {
        super("Já existe um bloqueio cadastrado para esse termo.");
    }

    public NomeBloqueadoDuplicadoException(Throwable cause) {
        super("Já existe um bloqueio cadastrado para esse termo.", cause);
    }
}
