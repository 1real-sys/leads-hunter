package dev.jlm.leadshunter.lead;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class WhatsAppLinkGeneratorTest {

    private final WhatsAppLinkGenerator generator = new WhatsAppLinkGenerator();

    @ParameterizedTest
    @CsvSource({
        "552733334444, https://wa.me/552733334444",
        "5527999990000, https://wa.me/5527999990000",
        "555533334444, https://wa.me/555533334444"
    })
    void deveGerarLinkParaTelefoneBrasileiroNormalizado(String telefone, String esperado) {
        assertThat(generator.gerar(telefone)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ",
        "(27) 99999-0000",
        "27999990000",
        "5510999990000",
        "14155552671"
    })
    void naoDeveGerarLinkParaTelefoneAusenteOuInvalido(String telefone) {
        assertThat(generator.gerar(telefone)).isNull();
    }
}
