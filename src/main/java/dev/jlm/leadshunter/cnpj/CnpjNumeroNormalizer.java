package dev.jlm.leadshunter.cnpj;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Canonicalizes the establishment number used by both CNPJ matching paths.
 *
 * <p>The implementation is deliberately independent from JPA and Spring so the
 * same examples can be exercised by unit tests and mirrored by the dataset tool
 * and migration backfill.</p>
 */
public final class CnpjNumeroNormalizer {

    public static final String VERSAO = "numero-normalizado-v2";
    public static final String VERSAO_LEGADO = "numero-legado-v1";
    public static final Set<String> SENTINELAS_SEM_NUMERO = Set.of(
        "SN", "SEM", "SEMNU", "SEMNM", "NAOINF", "SNR", "S"
    );

    private static final Pattern NAO_ALFANUMERICO = Pattern.compile("[^0-9A-Z]+");
    private static final Pattern SOMENTE_DIGITOS = Pattern.compile("[0-9]+");
    private static final Pattern ZERO_INICIAL_COM_DIGITO_NAO_ZERO = Pattern.compile(
        "^0*[1-9]"
    );

    private CnpjNumeroNormalizer() {
    }

    /**
     * Applies the V9 rule: trim, uppercase, remove non-alphanumeric characters,
     * discard values without digits and remove only the zero prefix that precedes
     * a non-zero digit. Alphanumeric values such as 48A and A48 remain valid.
     */
    public static String normalizar(String valor) {
        String compacto = compactar(valor);
        if (compacto.isEmpty() || !contemDigito(compacto)) {
            return null;
        }

        if (SOMENTE_DIGITOS.matcher(compacto).matches()) {
            String semZeros = compacto.replaceFirst("^0+", "");
            return semZeros.isEmpty() ? "0" : semZeros;
        }

        if (ZERO_INICIAL_COM_DIGITO_NAO_ZERO.matcher(compacto).find()) {
            return compacto.replaceFirst("^0+", "");
        }
        return compacto;
    }

    /**
     * Reproduces the pre-V9 behavior for the before/after diagnostic.
     */
    public static String normalizarLegado(String valor) {
        String compacto = compactar(valor);
        return compacto.isEmpty() || Set.of("SN", "SEMNUMERO").contains(compacto)
            ? null
            : compacto;
    }

    public static Classificacao classificar(String valor) {
        String compacto = compactar(valor);
        if (compacto.isEmpty()) {
            return Classificacao.AUSENTE;
        }
        if (contemDigito(compacto)) {
            return Classificacao.NUMERO;
        }
        return SENTINELAS_SEM_NUMERO.contains(compacto)
            ? Classificacao.SENTINELA_SEM_NUMERO
            : Classificacao.NUMERO_DESCONHECIDO;
    }

    public static String compacto(String valor) {
        return compactar(valor);
    }

    private static String compactar(String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        return NAO_ALFANUMERICO.matcher(valor.trim().toUpperCase(Locale.ROOT))
            .replaceAll("");
    }

    private static boolean contemDigito(String valor) {
        return valor.chars().anyMatch(Character::isDigit);
    }

    public enum Classificacao {
        AUSENTE,
        NUMERO,
        SENTINELA_SEM_NUMERO,
        NUMERO_DESCONHECIDO
    }

    public record NumeroDescartado(
        String valorBruto,
        String valorCompacto,
        Classificacao classificacao,
        long quantidade
    ) {
    }
}
