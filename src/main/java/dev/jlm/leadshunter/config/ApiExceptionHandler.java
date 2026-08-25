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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        String mensagem = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse("A requisição contém dados inválidos.");
        return resposta(HttpStatus.BAD_REQUEST, "VALIDACAO_INVALIDA", mensagem, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        return resposta(
            HttpStatus.BAD_REQUEST,
            "REQUISICAO_INVALIDA",
            "O corpo da requisição está ausente ou contém dados inválidos.",
            request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request
    ) {
        return resposta(
            HttpStatus.BAD_REQUEST,
            "REQUISICAO_INVALIDA",
            "O parâmetro de consulta '" + exception.getName() + "' é inválido.",
            request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
        Exception exception,
        HttpServletRequest request
    ) {
        LOGGER.error(
            "Erro interno não tratado em {} {} (tipo={})",
            request.getMethod(),
            request.getRequestURI(),
            exception.getClass().getName()
        );
        return resposta(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "ERRO_INTERNO",
            "Ocorreu um erro interno. Tente novamente mais tarde.",
            request
        );
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
