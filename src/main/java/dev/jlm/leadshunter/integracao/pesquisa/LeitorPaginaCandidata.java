package dev.jlm.leadshunter.integracao.pesquisa;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Abre uma página pública já entregue pelo buscador e devolve o texto útil para validar se
 * telefone, endereço ou CNPJ do lead aparecem lá. Não segue redirecionamentos, limita o
 * destino a HTTP/HTTPS público, corta o corpo por tamanho e nunca propaga falha técnica.
 */
@Component
public class LeitorPaginaCandidata {

    static final int TAMANHO_MAXIMO_TEXTO = 120_000;
    private static final int TAMANHO_MAXIMO_SCRIPT = 40_000;
    private static final Set<String> HOSTS_BLOQUEADOS = Set.of("localhost", "metadata.google.internal");
    private static final Set<String> CONTEUDOS_ACEITOS = Set.of(
        "text/html", "text/plain", "application/xhtml+xml", "application/xml", "application/json"
    );

    @FunctionalInterface
    interface Transporte {
        Resposta buscar(URI uri, long timeoutMs, int maxBytes) throws IOException, InterruptedException;
    }

    @FunctionalInterface
    interface VerificadorDestino {
        boolean permitido(URI uri);
    }

    record Resposta(int status, String tipoConteudo, byte[] corpo) {
    }

    private final Transporte transporte;
    private final VerificadorDestino verificador;
    private final long timeoutMs;
    private final int maxBytes;

    @Autowired
    public LeitorPaginaCandidata(
        @Value("${pesquisa-inteligente.pagina.timeout-ms:10000}") long timeoutMs,
        @Value("${pesquisa-inteligente.pagina.max-bytes:524288}") int maxBytes
    ) {
        this(new TransporteJdk(), LeitorPaginaCandidata::destinoPublico, timeoutMs, maxBytes);
    }

    LeitorPaginaCandidata(
        Transporte transporte,
        VerificadorDestino verificador,
        long timeoutMs,
        int maxBytes
    ) {
        if (timeoutMs < 500 || maxBytes < 1_024 || maxBytes > 8_388_608) {
            throw new IllegalArgumentException("Configuração inválida do leitor de página");
        }
        this.transporte = transporte;
        this.verificador = verificador;
        this.timeoutMs = timeoutMs;
        this.maxBytes = maxBytes;
    }

    static LeitorPaginaCandidata nenhum() {
        return new LeitorPaginaCandidata(
            (uri, timeout, limites) -> new Resposta(0, null, new byte[0]), uri -> false, 1_000, 1_024);
    }

    public Optional<String> ler(URI url) {
        if (url == null || !verificador.permitido(url)) {
            return Optional.empty();
        }
        try {
            Resposta resposta = transporte.buscar(url, timeoutMs, maxBytes);
            if (resposta == null || resposta.status() < 200 || resposta.status() >= 300
                || resposta.corpo() == null || resposta.corpo().length == 0
                || !tipoAceito(resposta.tipoConteudo())) {
                return Optional.empty();
            }
            String texto = extrairTexto(new String(resposta.corpo(), StandardCharsets.UTF_8));
            return texto.isBlank() ? Optional.empty() : Optional.of(texto);
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    private boolean tipoAceito(String tipoConteudo) {
        if (tipoConteudo == null || tipoConteudo.isBlank()) {
            return false;
        }
        String base = tipoConteudo.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return CONTEUDOS_ACEITOS.contains(base);
    }

    private String extrairTexto(String html) {
        Document documento = Jsoup.parse(html);
        StringBuilder texto = new StringBuilder();
        for (Element meta : documento.select(
            "meta[name=description], meta[property=og:description], meta[name=twitter:description]")) {
            anexar(texto, meta.attr("content"));
        }
        // Perfis e páginas costumam guardar telefone/endereço em JSON estruturado pequeno.
        // Scripts gigantes (estado da aplicação) são ignorados para não afogar o texto útil.
        for (Element script : documento.select(
            "script[type=application/json], script[type=application/ld+json]")) {
            String dados = script.data();
            if (dados.length() <= TAMANHO_MAXIMO_SCRIPT) {
                anexar(texto, dados);
            }
        }
        if (documento.body() != null) {
            anexar(texto, documento.body().text());
        }
        return texto.toString().strip();
    }

    private void anexar(StringBuilder texto, String trecho) {
        if (trecho == null || trecho.isBlank() || texto.length() >= TAMANHO_MAXIMO_TEXTO) {
            return;
        }
        int disponivel = TAMANHO_MAXIMO_TEXTO - texto.length();
        texto.append(trecho.length() <= disponivel ? trecho : trecho.substring(0, disponivel)).append('\n');
    }

    static boolean destinoPublico(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null) {
            return false;
        }
        String esquema = uri.getScheme();
        if (esquema == null || !(esquema.equalsIgnoreCase("http") || esquema.equalsIgnoreCase("https"))) {
            return false;
        }
        if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (HOSTS_BLOQUEADOS.contains(host) || host.endsWith(".localhost")
            || host.endsWith(".local") || host.endsWith(".internal")) {
            return false;
        }
        try {
            for (InetAddress endereco : InetAddress.getAllByName(host)) {
                if (endereco.isAnyLocalAddress() || endereco.isLoopbackAddress()
                    || endereco.isLinkLocalAddress() || endereco.isSiteLocalAddress()
                    || endereco.isMulticastAddress()) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private static final class TransporteJdk implements Transporte {

        private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        @Override
        public Resposta buscar(URI uri, long timeoutMs, int maxBytes) throws IOException, InterruptedException {
            HttpRequest requisicao = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8")
                .header("User-Agent", "Mozilla/5.0 (compatible; LeadsHunterBot/1.0)")
                .GET()
                .build();
            try {
                HttpResponse<InputStream> resposta = client.send(requisicao, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream corpo = resposta.body()) {
                    return new Resposta(
                        resposta.statusCode(),
                        resposta.headers().firstValue("Content-Type").orElse(null),
                        lerLimitado(corpo, maxBytes)
                    );
                }
            } catch (HttpTimeoutException exception) {
                return new Resposta(0, null, new byte[0]);
            }
        }

        private byte[] lerLimitado(InputStream corpo, int maxBytes) throws IOException {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int lidos;
            while ((lidos = corpo.read(buffer)) != -1) {
                total += lidos;
                if (total > maxBytes) {
                    return saida.toByteArray();
                }
                saida.write(buffer, 0, lidos);
            }
            return saida.toByteArray();
        }
    }
}
