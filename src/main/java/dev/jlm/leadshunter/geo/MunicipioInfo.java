package dev.jlm.leadshunter.geo;

import java.math.BigDecimal;

public record MunicipioInfo(
    String codigoIbge,
    String nome,
    String uf,
    BigDecimal idhm,
    Short idhmReferencia
) {
}
