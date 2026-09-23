package dev.jlm.leadshunter.cnpj;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** One immutable JSONL diagnostic row. */
public record CnpjMatchRelatorioLinha(
    String tipo,
    Long leadId,
    String googlePlaceId,
    CnpjMatchClassificacao classificacao,
    boolean politicaPermitida,
    String cnpj,
    LocalDate competencia,
    CnpjOrigem origemFinal,
    BigDecimal pontuacao,
    BigDecimal segundaPontuacao,
    BigDecimal gapNome,
    String resultadoLegadoAntes,
    CnpjMatchClassificacao classificacaoLegadoAntes,
    String resultadoNovo,
    boolean normalizacaoNumeroAlterada,
    String numeroBruto,
    CnpjNumeroNormalizer.Classificacao classificacaoNumero,
    List<CnpjService.CandidatoAvaliacao> candidatos
) {
    public CnpjMatchRelatorioLinha {
        candidatos = List.copyOf(candidatos);
    }
}
