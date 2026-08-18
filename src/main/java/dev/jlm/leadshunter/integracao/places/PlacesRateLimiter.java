package dev.jlm.leadshunter.integracao.places;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PlacesRateLimiter {

    private final Bucket bucket;

    public PlacesRateLimiter(
        @Value("${app.google-places.rate-limit.requisicoes:10}") long requisicoes,
        @Value("${app.google-places.rate-limit.periodo-segundos:60}") long periodoSegundos
    ) {
        if (requisicoes <= 0) {
            throw new IllegalArgumentException("A quantidade de requisições do rate limit deve ser positiva.");
        }
        if (periodoSegundos <= 0) {
            throw new IllegalArgumentException("O período do rate limit deve ser positivo.");
        }

        Bandwidth limite = Bandwidth.builder()
            .capacity(requisicoes)
            .refillGreedy(requisicoes, Duration.ofSeconds(periodoSegundos))
            .build();
        this.bucket = Bucket.builder()
            .addLimit(limite)
            .build();
    }

    public <T> T executar(Supplier<T> chamadaExterna) {
        if (!bucket.tryConsume(1)) {
            throw new PlacesRateLimitExceededException();
        }
        return chamadaExterna.get();
    }
}
