package dev.jlm.leadshunter.busca;

public class PesquisaInformacoesLimiteException extends RuntimeException {
    public PesquisaInformacoesLimiteException() {
        super("A pesquisa está ocupada ou a busca excede o limite de leads permitido. Tente novamente mais tarde ou use uma busca menor.");
    }
}
