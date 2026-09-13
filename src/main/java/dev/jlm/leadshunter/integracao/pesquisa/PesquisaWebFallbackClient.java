package dev.jlm.leadshunter.integracao.pesquisa;

import java.net.URI;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** Reutiliza os DTOs internos existentes; falha técnica é diferente de resultado vazio. */
@Component
@Slf4j
public class PesquisaWebFallbackClient implements GooglePesquisaGateway {
    private final GooglePesquisaGateway google;
    private final GooglePesquisaWebNavigator navegador;
    private final PesquisaAlternativaHtmlParser parser;
    private final java.util.function.Function<GooglePesquisaWebRequest, String> consulta;
    private final LongSupplier relogio;
    private final EnumMap<FontePesquisaWeb, AtomicLong> suspensasAte = new EnumMap<>(FontePesquisaWeb.class);

    @Autowired
    public PesquisaWebFallbackClient(GooglePesquisaWebClient google, PlaywrightGooglePesquisaNavigator navegador,
                                    PesquisaAlternativaHtmlParser parser) {
        this(google, navegador, parser, google::montarConsulta, System::currentTimeMillis);
    }

    PesquisaWebFallbackClient(GooglePesquisaGateway google, GooglePesquisaWebNavigator navegador,
                             PesquisaAlternativaHtmlParser parser,
                             java.util.function.Function<GooglePesquisaWebRequest, String> consulta, LongSupplier relogio) {
        this.google = google;
        this.navegador = navegador;
        this.parser = parser;
        this.consulta = consulta;
        this.relogio = relogio;
        for (FontePesquisaWeb fonte : FontePesquisaWeb.values()) suspensasAte.put(fonte, new AtomicLong());
    }

    @Override
    public GooglePesquisaWebResponse pesquisar(GooglePesquisaWebRequest request) {
        for (FontePesquisaWeb fonte : FontePesquisaWeb.values()) {
            if (Thread.currentThread().isInterrupted()) throw new GooglePesquisaWebIndisponivelException();
            if (relogio.getAsLong() < suspensasAte.get(fonte).get()) continue;
            try {
                if (fonte == FontePesquisaWeb.GOOGLE) return google.pesquisar(request);
                String texto = consulta.apply(request);
                URI uri = UriComponentsBuilder.fromUri(fonte.endereco).queryParam("q", texto)
                    .build().encode().toUri();
                var pagina = navegador.navegar(uri);
                return new GooglePesquisaWebResponse(request.googlePlaceId(), request.tipo(), texto,
                    parser.extrair(fonte, pagina, 10));
            } catch (GooglePesquisaWebOcupadaException exception) {
                // Falta de capacidade local não é falha de uma fonte e não deve multiplicar trabalho.
                throw exception;
            } catch (GooglePesquisaWebException exception) {
                long pausa = exception instanceof GooglePesquisaWebBloqueadaException ? 3_600_000 : 300_000;
                suspensasAte.get(fonte).accumulateAndGet(relogio.getAsLong() + pausa, Math::max);
                log.info("Pesquisa pública suspensa; fonte={}, intervaloMs={}", fonte, pausa);
            }
        }
        throw new GooglePesquisaWebIndisponivelException(
            "As fontes públicas de pesquisa estão temporariamente indisponíveis. Aguarde antes de tentar novamente.");
    }
}
