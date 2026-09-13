package dev.jlm.leadshunter.busca;

public record BuscaInformacoesResponse(
    int totalLeads,
    int processados,
    int ignoradosJaCompletos,
    int comInstagram,
    int comSite,
    int comAmbos,
    int semInformacoes,
    int falhas
) {
}
