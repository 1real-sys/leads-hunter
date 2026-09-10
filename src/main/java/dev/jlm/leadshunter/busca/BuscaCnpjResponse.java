package dev.jlm.leadshunter.busca;

public record BuscaCnpjResponse(
    int totalLeads,
    int ignoradosJaComCnpj,
    int encontrados,
    int semCorrespondencia
) {
}
