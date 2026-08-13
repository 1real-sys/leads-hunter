package dev.jlm.leadshunter.lead;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TelefoneNormalizerTest {

    private final TelefoneNormalizer normalizer = new TelefoneNormalizer();

    @ParameterizedTest
    @CsvSource({
        "'(27) 3333-4444', 552733334444",
        "'(27) 99999-0000', 5527999990000",
        "'+55 27 99999-0000', 5527999990000",
        "'0055 27 99999-0000', 5527999990000",
        "'(55) 3333-4444', 555533334444"
    })
    void deveNormalizarTelefoneBrasileiro(String telefone, String esperado) {
        assertThat(normalizer.normalizar(telefone)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ",
        "3333-4444",
        "0800 123 4567",
        "+1 415 555 2671",
        "+55 10 99999-0000"
    })
    void deveIgnorarTelefoneAusenteOuInvalido(String telefone) {
        assertThat(normalizer.normalizar(telefone)).isNull();
    }
}
