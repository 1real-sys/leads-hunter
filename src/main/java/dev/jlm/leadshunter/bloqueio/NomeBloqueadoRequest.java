package dev.jlm.leadshunter.bloqueio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NomeBloqueadoRequest(
    @NotBlank(message = "Informe o termo a bloquear")
    @Size(min = 3, max = 120, message = "O termo deve ter entre 3 e 120 caracteres")
    String termo
) {
}
