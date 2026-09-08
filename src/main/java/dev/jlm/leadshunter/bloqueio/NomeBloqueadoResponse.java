package dev.jlm.leadshunter.bloqueio;

import java.time.LocalDateTime;

public record NomeBloqueadoResponse(
    Long id,
    String termo,
    LocalDateTime criadoEm
) {

    static NomeBloqueadoResponse from(NomeBloqueado nomeBloqueado) {
        return new NomeBloqueadoResponse(
            nomeBloqueado.getId(),
            nomeBloqueado.getTermo(),
            nomeBloqueado.getCriadoEm()
        );
    }
}
