package dev.jlm.leadshunter.busca;

import java.time.LocalDateTime;

public record BuscaEmailResponse(
    Long id, Long buscaId, PesquisaInformacoesStatus status,
    LocalDateTime criadoEm, LocalDateTime iniciadoEm, LocalDateTime atualizadoEm,
    LocalDateTime terminadoEm, int totalLeads, int progresso, int ignoradosJaComEmail,
    int ignoradosSemSite, int processados, int encontrados, int semEmailElegivel,
    int descartadosDominioExterno, int falhas, String erroCodigo, String erroMensagem
) {
    public static BuscaEmailResponse de(BuscaEmailExecucao e) {
        return new BuscaEmailResponse(e.getId(), e.getBusca().getId(), e.getStatus(),
            e.getCriadoEm(), e.getIniciadoEm(), e.getAtualizadoEm(), e.getTerminadoEm(),
            e.getTotalLeads(), e.getIgnoradosJaComEmail() + e.getIgnoradosSemSite()
                + e.getProcessados(), e.getIgnoradosJaComEmail(), e.getIgnoradosSemSite(),
            e.getProcessados(), e.getEncontrados(), e.getSemEmailElegivel(),
            e.getDescartadosDominioExterno(), e.getFalhas(), e.getErroCodigo(), e.getErroMensagem());
    }
}
