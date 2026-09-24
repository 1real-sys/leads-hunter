package dev.jlm.leadshunter.busca;

public class BuscaEmailLimiteException extends RuntimeException {
    public BuscaEmailLimiteException() {
        super("A busca de e-mails está ocupada ou excede o limite de leads.");
    }
}
