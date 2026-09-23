package dev.jlm.leadshunter.cnpj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CnpjMatchPolicyTest {

    @Test
    void deveSerFailClosedSemFlagOuAllowlist() {
        CnpjMatchPolicy policy = CnpjMatchPolicy.desabilitada();

        assertThat(policy.caminhoExatoHabilitado()).isFalse();
        assertThat(policy.permite("3205309", "ES")).isFalse();
    }

    @Test
    void deveDarPrecedenciaAosMunicipiosSobreUfs() {
        CnpjMatchPolicy policy = new CnpjMatchPolicy(true, "3205309", "PR");

        assertThat(policy.permite("3205309", "ES")).isTrue();
        assertThat(policy.permite("4106902", "PR")).isFalse();
    }

    @Test
    void deveUsarUfQuandoNaoHouverMunicipio() {
        CnpjMatchPolicy policy = new CnpjMatchPolicy(true, null, "ES");

        assertThat(policy.permite("3205309", "ES")).isTrue();
        assertThat(policy.permite("4106902", "PR")).isFalse();
    }

    @Test
    void deveTratarListaMunicipalVaziaComoConfiguracaoExplicita() {
        CnpjMatchPolicy policy = new CnpjMatchPolicy(true, "", "ES");

        assertThat(policy.caminhoExatoHabilitado()).isFalse();
        assertThat(policy.permite("3205309", "ES")).isFalse();
    }

    @Test
    void deveRejeitarCodigoIbgeMalformadoNaConfiguracao() {
        assertThatThrownBy(() -> new CnpjMatchPolicy(true, "32053", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("município");
    }
}
