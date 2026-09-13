package dev.jlm.leadshunter.integracao.pesquisa;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Fonte principal de pesquisa: API oficial do Brave Search, sem navegador e sem scraping. */
@Component
public class BravePesquisaApiClient implements GooglePesquisaGateway {

    static final String URL_BUSCA = "https://api.search.brave.com/res/v1/web/search";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int TAMANHO_MAXIMO_TEXTO = 500;

    @FunctionalInterface
    interface Transporte {
        Resposta buscar(URI uri, String apiKey, long timeoutMs, int maxRespostaBytes);
    }

    record Resposta(int status, byte[] corpo) {
    }

    private final Transporte transporte;
    private final Function<GooglePesquisaWebRequest, String> consulta;
    private final String apiKey;
    private final boolean habilitado;
    private final long timeoutMs;
    private final int maxResultados;
    private final int maxRespostaBytes;

    @Autowired
    public BravePesquisaApiClient(
        @Value("${pesquisa-inteligente.brave.api-key:}") String apiKey,
        @Value("${pesquisa-inteligente.brave.habilitado:true}") boolean habilitado,
        @Value("${pesquisa-inteligente.brave.timeout-ms:15000}") long timeoutMs,
        @Value("${pesquisa-inteligente.brave.max-resultados:10}") int maxResultados,
        @Value("${pesquisa-inteligente.brave.max-resposta-bytes:2097152}") int maxRespostaBytes
    ) {
        this(new TransporteJdk(), BravePesquisaApiClient::montarConsulta, apiKey, habilitado,
            timeoutMs, maxResultados, maxRespostaBytes);
    }

    BravePesquisaApiClient(
        Transporte transporte,
        Function<GooglePesquisaWebRequest, String> consulta,
        String apiKey,
        boolean habilitado,
        long timeoutMs,
        int maxResultados,
        int maxRespostaBytes
    ) {
        if (timeoutMs < 1_000 || maxResultados < 1 || maxResultados > 20
            || maxRespostaBytes < 16_384 || maxRespostaBytes > 16_777_216) {
            throw new IllegalArgumentException("Configuração inválida da pesquisa Brave");
        }
        this.transporte = transporte;
        this.consulta = consulta;
        this.apiKey = apiKey;
        this.habilitado = habilitado;
        this.timeoutMs = timeoutMs;
        this.maxResultados = maxResultados;
        this.maxRespostaBytes = maxRespostaBytes;
    }

    public boolean habilitado() {
        return habilitado && apiKey != null && !apiKey.isBlank();
    }

    static String montarConsulta(GooglePesquisaWebRequest request) {
        String nome = limparTermo(request.nome());
        if (nome.isBlank()) {
            throw new IllegalArgumentException("nome deve conter texto pesquisável");
        }
        String local = localizacao(request);
        String sufixo = request.tipo() == TipoPesquisaWeb.INSTAGRAM ? " instagram" : "";
        return (nome + (local.isBlank() ? "" : " " + local) + sufixo).strip();
    }

    private static String localizacao(GooglePesquisaWebRequest request) {
        String municipio = limparTermo(request.municipio());
        String uf = limparTermo(request.uf()).toUpperCase(Locale.ROOT);
        if (!municipio.isBlank()) {
            return uf.isBlank() ? municipio : municipio + " " + uf;
        }
        return limparTermo(request.enderecoFormatado());
    }

    private static String limparTermo(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replaceAll("[\\p{Cntrl}\"`\\\\]+", " ").replaceAll("\\s+", " ").strip();
    }

    @Override
    public GooglePesquisaWebResponse pesquisar(GooglePesquisaWebRequest request) {
        if (!habilitado()) {
            throw new GooglePesquisaWebIndisponivelException(
                "A pesquisa pela API do Brave não está configurada."
            );
        }
        String textoConsulta = consulta.apply(request);
        URI uri = UriComponentsBuilder.fromUriString(URL_BUSCA)
            .queryParam("q", textoConsulta)
            .queryParam("count", maxResultados)
            .queryParam("country", "BR")
            .queryParam("search_lang", "pt-br")
            .queryParam("safesearch", "moderate")
            .queryParam("spellcheck", false)
            .queryParam("operators", false)
            .queryParam("extra_snippets", true)
            .queryParam("text_decorations", false)
            .queryParam("result_filter", "web")
            .build()
            .encode()
            .toUri();

        Resposta resposta = transporte.buscar(uri, apiKey, timeoutMs, maxRespostaBytes);
        return new GooglePesquisaWebResponse(
            request.googlePlaceId(),
            request.tipo(),
            textoConsulta,
            extrair(resposta)
        );
    }

    private List<GoogleResultadoWeb> extrair(Resposta resposta) {
        int status = resposta.status();
        if (status == 429) {
            throw new GooglePesquisaWebBloqueadaException();
        }
        if (status == 401 || status == 403) {
            throw new GooglePesquisaWebIndisponivelException(
                "A API de pesquisa recusou a credencial configurada."
            );
        }
        if (status < 200 || status >= 300) {
            throw new GooglePesquisaWebIndisponivelException();
        }
        if (resposta.corpo().length > maxRespostaBytes) {
            throw new GooglePesquisaWebFormatoInvalidoException();
        }

        try {
            JsonNode raiz = JSON.readTree(resposta.corpo());
            if (raiz == null || !raiz.isObject()) throw new GooglePesquisaWebFormatoInvalidoException();
            JsonNode web = raiz.path("web");
            if (!web.isMissingNode() && !web.isObject()) throw new GooglePesquisaWebFormatoInvalidoException();
            if (web.isMissingNode() && !("search".equals(raiz.path("type").asText()) && raiz.path("query").isObject())) {
                throw new GooglePesquisaWebFormatoInvalidoException();
            }
            JsonNode resultados = raiz.path("web").path("results");
            if (!resultados.isArray()) {
                if (!resultados.isMissingNode()) throw new GooglePesquisaWebFormatoInvalidoException();
                return List.of();
            }
            List<GoogleResultadoWeb> saida = new ArrayList<>();
            Set<URI> vistos = new HashSet<>();
            for (JsonNode item : resultados) {
                URI url = normalizar(item.path("url").asText(""));
                if (url == null || !vistos.add(url)) {
                    continue;
                }
                saida.add(new GoogleResultadoWeb(
                    url,
                    limpar(item.path("title").asText("")),
                    resumo(item)
                ));
                if (saida.size() == maxResultados) {
                    break;
                }
            }
            return List.copyOf(saida);
        } catch (JacksonException exception) {
            throw new GooglePesquisaWebFormatoInvalidoException(exception);
        }
    }

    private URI normalizar(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url).normalize();
            String esquema = uri.getScheme();
            if (!("http".equalsIgnoreCase(esquema) || "https".equalsIgnoreCase(esquema))
                || uri.getHost() == null || uri.getUserInfo() != null) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String limpar(String valor) {
        String texto = Jsoup.parse(valor == null ? "" : valor).text().strip();
        return texto.length() <= TAMANHO_MAXIMO_TEXTO ? texto : texto.substring(0, TAMANHO_MAXIMO_TEXTO).strip();
    }

    private String resumo(JsonNode item) {
        // Até seis trechos de 500 caracteres da mesma URL, sem misturar estabelecimentos/resultados.
        Set<String> trechos = new LinkedHashSet<>();
        trechos.add(limpar(item.path("description").asText("")));
        JsonNode extras = item.path("extra_snippets");
        if (extras.isArray()) {
            int lidos = 0;
            for (JsonNode extra : extras) {
                if (lidos++ == 5) break;
                if (extra.isString()) trechos.add(limpar(extra.asText()));
            }
        }
        trechos.remove("");
        return String.join("\n", trechos);
    }

    private static final class TransporteJdk implements Transporte {

        private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        @Override
        public Resposta buscar(URI uri, String apiKey, long timeoutMs, int maxRespostaBytes) {
            HttpRequest requisicao = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json")
                .header("X-Subscription-Token", apiKey)
                .GET()
                .build();
            try {
                HttpResponse<InputStream> resposta = client.send(
                    requisicao,
                    HttpResponse.BodyHandlers.ofInputStream()
                );
                try (InputStream corpo = resposta.body()) {
                    return new Resposta(resposta.statusCode(), lerLimitado(corpo, maxRespostaBytes));
                }
            } catch (HttpTimeoutException exception) {
                throw new GooglePesquisaWebTimeoutException(exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new GooglePesquisaWebIndisponivelException(exception);
            } catch (IOException exception) {
                throw new GooglePesquisaWebIndisponivelException(exception);
            }
        }

        private byte[] lerLimitado(InputStream corpo, int maxRespostaBytes) throws IOException {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int lidos;
            while ((lidos = corpo.read(buffer)) != -1) {
                total += lidos;
                if (total > maxRespostaBytes) {
                    throw new GooglePesquisaWebFormatoInvalidoException();
                }
                saida.write(buffer, 0, lidos);
            }
            return saida.toByteArray();
        }
    }
}
