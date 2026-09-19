package dev.jlm.leadshunter.busca;

import java.time.LocalDateTime;

public record PesquisaInformacoesExecucaoResponse(
    Long id, Long buscaId, PesquisaInformacoesStatus status,
    LocalDateTime criadoEm, LocalDateTime iniciadoEm, LocalDateTime atualizadoEm,
    LocalDateTime terminadoEm, int totalLeads, int progresso, int processados,
    int ignoradosJaCompletos, int comInstagram, int comSite, int comAmbos,
    int semInformacoes, int falhas, PesquisaInformacoesErro erroCodigo, String erroMensagem,
    boolean usarBrave
) {
    public PesquisaInformacoesExecucaoResponse(
        Long id, Long buscaId, PesquisaInformacoesStatus status,
        LocalDateTime criadoEm, LocalDateTime iniciadoEm, LocalDateTime atualizadoEm,
        LocalDateTime terminadoEm, int totalLeads, int progresso, int processados,
        int ignoradosJaCompletos, int comInstagram, int comSite, int comAmbos,
        int semInformacoes, int falhas, PesquisaInformacoesErro erroCodigo, String erroMensagem
    ) {
        this(id, buscaId, status, criadoEm, iniciadoEm, atualizadoEm, terminadoEm, totalLeads,
            progresso, processados, ignoradosJaCompletos, comInstagram, comSite, comAmbos,
            semInformacoes, falhas, erroCodigo, erroMensagem, true);
    }

    public static PesquisaInformacoesExecucaoResponse de(PesquisaInformacoesExecucao execucao) {
        return new PesquisaInformacoesExecucaoResponse(
            execucao.getId(), execucao.getBusca().getId(), execucao.getStatus(),
            execucao.getCriadoEm(), execucao.getIniciadoEm(), execucao.getAtualizadoEm(),
            execucao.getTerminadoEm(), execucao.getTotalLeads(),
            execucao.getProcessados() + execucao.getIgnoradosJaCompletos() + execucao.getFalhas(),
            execucao.getProcessados(), execucao.getIgnoradosJaCompletos(), execucao.getComInstagram(),
            execucao.getComSite(), execucao.getComAmbos(), execucao.getSemInformacoes(),
            execucao.getFalhas(), execucao.getErroCodigo(), execucao.getErroMensagem(),
            execucao.isUsarBrave()
        );
    }
}
