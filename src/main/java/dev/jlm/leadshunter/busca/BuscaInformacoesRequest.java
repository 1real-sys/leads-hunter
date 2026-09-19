package dev.jlm.leadshunter.busca;

/** Opções da execução de pesquisa inteligente solicitadas pelo frontend. */
public record BuscaInformacoesRequest(Boolean usarBrave) {

    public boolean deveUsarBrave() {
        // Ausência do campo mantém o comportamento anterior para clientes antigos.
        return !Boolean.FALSE.equals(usarBrave);
    }
}
