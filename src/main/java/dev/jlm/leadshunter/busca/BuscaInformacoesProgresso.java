package dev.jlm.leadshunter.busca;

import java.util.function.Supplier;

/** Executa a persistência do lead e a atualização do resumo na mesma transação curta. */
@FunctionalInterface
public interface BuscaInformacoesProgresso {
    void registrar(Supplier<BuscaInformacoesResponse> passo, PesquisaInformacoesErro erro);
}
