package dev.jlm.leadshunter.lead;

import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDateTime;

public record AtualizarLeadRequest(
    StatusFunil status,
    String observacoes,
    LocalDateTime ultimoContatoEm
) {

    @AssertTrue(message = "Informe ao menos um campo para atualização")
    public boolean isPossuiCampoInformado() {
        return status != null || observacoes != null || ultimoContatoEm != null;
    }
}
