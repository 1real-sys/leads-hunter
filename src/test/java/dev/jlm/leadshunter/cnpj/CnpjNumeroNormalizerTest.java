package dev.jlm.leadshunter.cnpj;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CnpjNumeroNormalizerTest {

    @Test
    void deveAplicarRegraCanonicaDeNumeros() {
        assertThat(CnpjNumeroNormalizer.normalizar("48")).isEqualTo("48");
        assertThat(CnpjNumeroNormalizer.normalizar("048")).isEqualTo("48");
        assertThat(CnpjNumeroNormalizer.normalizar("48A")).isEqualTo("48A");
        assertThat(CnpjNumeroNormalizer.normalizar("048A")).isEqualTo("48A");
        assertThat(CnpjNumeroNormalizer.normalizar("000")).isEqualTo("0");
        assertThat(CnpjNumeroNormalizer.normalizar("000A")).isEqualTo("000A");
        assertThat(CnpjNumeroNormalizer.normalizar("A48")).isEqualTo("A48");
    }

    @Test
    void deveDescartarSemNumeroESinalizarDesconhecidos() {
        assertThat(CnpjNumeroNormalizer.normalizar("SN")).isNull();
        assertThat(CnpjNumeroNormalizer.normalizar("S/Nº")).isNull();
        assertThat(CnpjNumeroNormalizer.normalizar("SEM NUMERO")).isNull();
        assertThat(CnpjNumeroNormalizer.normalizar("SEM")).isNull();
        assertThat(CnpjNumeroNormalizer.normalizar("NAOINF")).isNull();
        assertThat(CnpjNumeroNormalizer.normalizar("O")).isNull();
        assertThat(CnpjNumeroNormalizer.classificar("NAOINF"))
            .isEqualTo(CnpjNumeroNormalizer.Classificacao.SENTINELA_SEM_NUMERO);
        assertThat(CnpjNumeroNormalizer.classificar("BLOCOA"))
            .isEqualTo(CnpjNumeroNormalizer.Classificacao.NUMERO_DESCONHECIDO);
    }
}
