package dev.jlm.leadshunter.integracao.places;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

class PlacesRateLimiterTest {

    @Test
    void deveBloquearChamadasAcimaDaCapacidade() {
        PlacesRateLimiter rateLimiter = new PlacesRateLimiter(2, 60);
        AtomicInteger chamadasExecutadas = new AtomicInteger();

        assertThat(rateLimiter.executar(chamadasExecutadas::incrementAndGet)).isEqualTo(1);
        assertThat(rateLimiter.executar(chamadasExecutadas::incrementAndGet)).isEqualTo(2);
        assertThatThrownBy(() -> rateLimiter.executar(chamadasExecutadas::incrementAndGet))
            .isInstanceOf(PlacesRateLimitExceededException.class)
            .hasMessageContaining("Limite temporário");
        assertThat(chamadasExecutadas).hasValue(2);
    }

    @Test
    void deveResponderComStatusHttp429QuandoLimiteForExcedido() {
        ResponseStatus responseStatus = PlacesRateLimitExceededException.class
            .getAnnotation(ResponseStatus.class);

        assertThat(responseStatus).isNotNull();
        assertThat(responseStatus.value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void deveValidarConfiguracao() {
        assertThatThrownBy(() -> new PlacesRateLimiter(0, 60))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requisições");
        assertThatThrownBy(() -> new PlacesRateLimiter(10, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("período");
    }
}
