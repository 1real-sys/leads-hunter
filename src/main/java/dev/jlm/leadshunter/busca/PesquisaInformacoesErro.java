package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.integracao.pesquisa.GooglePesquisaWebBloqueadaException;
import dev.jlm.leadshunter.integracao.pesquisa.GooglePesquisaWebFormatoInvalidoException;
import dev.jlm.leadshunter.integracao.pesquisa.GooglePesquisaWebOcupadaException;
import dev.jlm.leadshunter.integracao.pesquisa.GooglePesquisaWebTimeoutException;

public enum PesquisaInformacoesErro {
    PESQUISA_BLOQUEADA("A fonte de pesquisa bloqueou temporariamente o acesso. Aguarde antes de tentar novamente."),
    PESQUISA_TIMEOUT("A pesquisa excedeu o tempo limite."),
    PESQUISA_FORMATO_INVALIDO("O formato da resposta de pesquisa não pôde ser reconhecido."),
    PESQUISA_INDISPONIVEL("A pesquisa pública está indisponível no momento."),
    PESQUISA_OCUPADA("A pesquisa inteligente está ocupada. Tente novamente mais tarde."),
    PESQUISA_INTERROMPIDA("A execução foi interrompida. Os resultados já salvos foram preservados. Inicie uma nova tentativa."),
    PESQUISA_ERRO_INTERNO("Não foi possível concluir a pesquisa. Os resultados já salvos foram preservados.");

    private final String mensagem;

    PesquisaInformacoesErro(String mensagem) {
        this.mensagem = mensagem;
    }

    public String mensagem() {
        return mensagem;
    }

    public static PesquisaInformacoesErro deFalhaExterna(RuntimeException erro) {
        if (erro instanceof GooglePesquisaWebBloqueadaException) return PESQUISA_BLOQUEADA;
        if (erro instanceof GooglePesquisaWebTimeoutException) return PESQUISA_TIMEOUT;
        if (erro instanceof GooglePesquisaWebFormatoInvalidoException) return PESQUISA_FORMATO_INVALIDO;
        if (erro instanceof GooglePesquisaWebOcupadaException) return PESQUISA_OCUPADA;
        return PESQUISA_INDISPONIVEL;
    }
}
