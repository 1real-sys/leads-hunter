package dev.jlm.leadshunter.busca;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.jlm.leadshunter.integracao.places.PlacesSearchResponse;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BuscaPlacesCache {

    private final Cache<BuscaCacheKey, PlacesSearchResponse> cache;

    public BuscaPlacesCache(
        @Value("${app.cache.buscas.expiracao-minutos:30}") long expiracaoMinutos,
        @Value("${app.cache.buscas.tamanho-maximo:100}") long tamanhoMaximo
    ) {
        if (expiracaoMinutos <= 0) {
            throw new IllegalArgumentException("A expiração do cache de buscas deve ser positiva.");
        }
        if (tamanhoMaximo <= 0) {
            throw new IllegalArgumentException("O tamanho máximo do cache de buscas deve ser positivo.");
        }

        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(expiracaoMinutos))
            .maximumSize(tamanhoMaximo)
            .build();
    }

    public PlacesSearchResponse buscarOuCarregar(
        BuscaCacheKey chave,
        Supplier<PlacesSearchResponse> carregador
    ) {
        return cache.get(chave, ignored -> carregador.get());
    }
}
