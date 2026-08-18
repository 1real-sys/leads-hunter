package dev.jlm.leadshunter.lead;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AtualizarLeadRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void deveRejeitarAtualizacaoSemCampos() {
        AtualizarLeadRequest request = new AtualizarLeadRequest(null, null, null);

        assertThat(validator.validate(request))
            .singleElement()
            .satisfies(violacao -> assertThat(violacao.getMessage())
                .isEqualTo("Informe ao menos um campo para atualização"));
    }

    @Test
    void deveAceitarCadaCampoAtualizavel() {
        assertThat(validator.validate(
            new AtualizarLeadRequest(StatusFunil.CONTATADO, null, null)
        )).isEmpty();
        assertThat(validator.validate(
            new AtualizarLeadRequest(null, "Novo contato", null)
        )).isEmpty();
        assertThat(validator.validate(
            new AtualizarLeadRequest(null, null, LocalDateTime.now())
        )).isEmpty();
    }
}
