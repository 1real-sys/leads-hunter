package dev.jlm.leadshunter.busca;

/** Dados imutáveis necessários para o worker continuar uma execução já persistida. */
public record PesquisaInformacoesExecucaoContexto(Long buscaId, boolean usarBrave) {
}
