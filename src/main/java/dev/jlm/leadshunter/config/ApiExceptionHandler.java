package dev.jlm.leadshunter.config;

import dev.jlm.leadshunter.busca.BuscaNaoEncontradaException;
import dev.jlm.leadshunter.integracao.places.PlacesApiConfigurationException;
import dev.jlm.leadshunter.integracao.places.PlacesApiInvalidResponseException;
import dev.jlm.leadshunter.integracao.places.PlacesApiQuotaExceededException;
import dev.jlm.leadshunter.integracao.places.PlacesApiRequestRejectedException;
import dev.jlm.leadshunter.integracao.places.PlacesApiUnavailableException;
import dev.jlm.leadshunter.integracao.places.PlacesRateLimitExceededException;
import dev.jlm.leadshunter.lead.LeadNaoEncontradoException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PlacesRateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimit(
        PlacesRateLimitExceededException exception,
        HttpServletRequest request
    ) {
        return resposta(HttpStatus.TOO_MANY_REQUESTS, "GOOGLE_PLACES_RATE_LIMIT", exception.getMessage(), request);
    }

    @ExceptionHandler(PlacesApiQuotaExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleQuota(
        PlacesApiQuotaExceededException exception,
        HttpServletRequest request
    ) {
        return resposta(HttpStatus.TOO_MANY_REQUESTS, "GOOGLE_PLACES_QUOTA_EXCEEDED", exception.getMessage(), request);
    }

    @ExceptionHandler(PlacesApiConfigurationException.class)
    public ResponseEntity<ApiErrorResponse> handleConfiguration(
        PlacesApiConfigurationException exception,
        HttpServletRequest request
    ) {
        return resposta(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_PLACES_CONFIGURATION", exception.getMessage(), request);
    }

    @ExceptionHandler(PlacesApiUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnavailable(
        PlacesApiUnavailableException exception,
        HttpServletRequest request
    ) {
        return resposta(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_PLACES_UNAVAILABLE", exception.getMessage(), request);
    }

    @ExceptionHandler(PlacesApiInvalidResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidResponse(
        PlacesApiInvalidResponseException exception,
        HttpServletRequest request
    ) {
        return resposta(HttpStatus.BAD_GATEWAY, "GOOGLE_PLACES_INVALID_RESPONSE", exception.getMessage(), request);
    }

    @ExceptionHandler(PlacesApiRequestRejectedException.class)
    public ResponseEntity<ApiErrorResponse> handleRequestRejected(
        PlacesApiRequestRejectedException exception,
        HttpServletRequest request
    ) {
        return resposta(HttpStatus.BAD_GATEWAY, "GOOGLE_PLACES_REQUEST_REJECTED", exception.getMessage(), request);
    }

    @ExceptionHandler(BuscaNaoEncontradaException.class)
    public ResponseEntity<ApiErrorResponse> handleBuscaNotFound(
        BuscaNaoEncontradaException exception,
        HttpServletRequest request
    ) {
        return resposta(HttpStatus.NOT_FOUND, "BUSCA_NAO_ENCONTRADA", exception.getMessage(), request);
    }

    @ExceptionHandler(LeadNaoEncontradoException.class)
    public ResponseEntity<ApiErrorResponse> handleLeadNotFound(
        LeadNaoEncontradoException exception,
        HttpServletRequest request
    ) {
        return resposta(HttpStatus.NOT_FOUND, "LEAD_NAO_ENCONTRADO", exception.getMessage(), request);
    }

    private ResponseEntity<ApiErrorResponse> resposta(
        HttpStatus status,
        String codigo,
        String mensagem,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
            Instant.now(),
            status.value(),
            codigo,
            mensagem,
            request.getRequestURI()
        ));
    }
}
