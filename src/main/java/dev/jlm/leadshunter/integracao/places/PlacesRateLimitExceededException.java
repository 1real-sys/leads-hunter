package dev.jlm.leadshunter.integracao.places;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class PlacesRateLimitExceededException extends RuntimeException {

    public PlacesRateLimitExceededException() {
        super("Limite temporário de consultas à Google Places atingido. Tente novamente em instantes.");
    }
}
