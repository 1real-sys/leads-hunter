package dev.jlm.leadshunter.config;

import java.time.Instant;

public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String codigo,
    String mensagem,
    String path
) {
}
