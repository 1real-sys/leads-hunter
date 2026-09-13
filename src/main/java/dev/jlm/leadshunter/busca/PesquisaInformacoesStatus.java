package dev.jlm.leadshunter.busca;

public enum PesquisaInformacoesStatus {
    PENDENTE, EM_ANDAMENTO, CONCLUIDA, CONCLUIDA_COM_FALHAS, FALHA;

    public boolean ativa() {
        return this == PENDENTE || this == EM_ANDAMENTO;
    }
}
