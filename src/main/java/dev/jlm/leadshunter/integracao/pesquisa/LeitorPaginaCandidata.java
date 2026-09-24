package dev.jlm.leadshunter.integracao.pesquisa;

import dev.jlm.leadshunter.lead.EmailSiteHost;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern URL_INSTAGRAM_EMBUTIDA = Pattern.compile(
        "(?i)(?:(?:https?:)?//)(?:www\\.|m\\.)?instagram\\.com/[a-z0-9._]{1,30}"
            + "(?:/[a-z0-9._-]+)*"
    );
    private static final Pattern EMAIL = Pattern.compile(
        "(?i)(?<![a-z0-9._%+\u002d])([a-z0-9._%+\u002d]{1,64}@[a-z0-9\u002d]+(?:\\.[a-z0-9\u002d]+)+)(?![a-z0-9\u002d])"
    );
    private static final Pattern CONTATO = Pattern.compile(
        "(?iu)(?:contato|contact|fale[-\\s]?conosco|atendimento)"
    );
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
    private final UrlCandidatoCanonicalizer canonicalizer;
    private final long timeoutMs;
    private final int maxBytes;

    @Autowired
    public LeitorPaginaCandidata(
        UrlCandidatoCanonicalizer canonicalizer,
        @Value("${pesquisa-inteligente.pagina.timeout-ms:10000}") long timeoutMs,
        @Value("${pesquisa-inteligente.pagina.max-bytes:524288}") int maxBytes
    ) {
        this(new TransporteJdk(), LeitorPaginaCandidata::destinoPublico, canonicalizer, timeoutMs, maxBytes);
    }

    public LeitorPaginaCandidata(long timeoutMs, int maxBytes) {
        this(
            new TransporteJdk(),
            LeitorPaginaCandidata::destinoPublico,
            new UrlCandidatoCanonicalizer(),
            timeoutMs,
            maxBytes
        );
    }

    LeitorPaginaCandidata(
        Transporte transporte,
        VerificadorDestino verificador,
        long timeoutMs,
        int maxBytes
    ) {
        this(
            transporte,
            verificador,
            new UrlCandidatoCanonicalizer(),
            timeoutMs,
            maxBytes
        );
    }

    LeitorPaginaCandidata(
        Transporte transporte,
        VerificadorDestino verificador,
        UrlCandidatoCanonicalizer canonicalizer,
        long timeoutMs,
        int maxBytes
    ) {
        if (transporte == null || verificador == null || canonicalizer == null) {
            throw new IllegalArgumentException("Dependências do leitor de página são obrigatórias");
        }
        if (timeoutMs < 500 || maxBytes < 1_024 || maxBytes > 8_388_608) {
            throw new IllegalArgumentException("Configuração inválida do leitor de página");
        }
        this.transporte = transporte;
        this.verificador = verificador;
        this.canonicalizer = canonicalizer;
        this.timeoutMs = timeoutMs;
        this.maxBytes = maxBytes;
    }

    static LeitorPaginaCandidata nenhum() {
        return new LeitorPaginaCandidata(
            (uri, timeout, limites) -> new Resposta(0, null, new byte[0]),
            uri -> false,
            new UrlCandidatoCanonicalizer(),
            1_000,
            1_024
        );
    }

    public Optional<String> ler(URI url) {
        return lerPagina(url)
            .filter(pagina -> !pagina.texto().isBlank())
            .map(PaginaLida::texto);
    }

    public Optional<PaginaLida> lerPagina(URI url) {
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
            return extrairPagina(new String(resposta.corpo(), StandardCharsets.UTF_8), url);
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

    private Optional<PaginaLida> extrairPagina(String html, URI pagina) {
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
        List<URI> links = pagina == null ? List.of() : extrairLinks(documento, pagina);
        String textoExtraido = texto.toString().strip();
        List<String> emails = extrairEmails(documento, textoExtraido);
        List<URI> contatos = pagina == null ? List.of() : extrairLinksContato(documento, pagina);
        if (textoExtraido.isBlank() && links.isEmpty() && emails.isEmpty() && contatos.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PaginaLida(textoExtraido, links, emails, contatos));
    }

    private List<String> extrairEmails(Document documento, String texto) {
        Set<String> encontrados = new LinkedHashSet<>();
        var trechos = new ArrayList<String>();
        trechos.add(texto);
        for (Element link : documento.select("a[href^=mailto:]")) {
            String href = link.attr("href");
            trechos.add(href.substring("mailto:".length()).split("\\?", 2)[0]);
        }
        for (String trecho : trechos) {
            Matcher matcher = EMAIL.matcher(trecho);
            while (matcher.find() && encontrados.size() < 50) {
                String email = matcher.group(1).toLowerCase(Locale.ROOT);
                if (email.length() <= 320) encontrados.add(email);
            }
        }
        return List.copyOf(encontrados);
    }

    private List<URI> extrairLinksContato(Document documento, URI pagina) {
        Set<URI> encontrados = new LinkedHashSet<>();
        String host = EmailSiteHost.de(pagina.toString());
        for (Element link : documento.select("a[href]")) {
            if (!CONTATO.matcher(link.text() + " " + link.attr("href")).find()) continue;
            try {
                URI absoluto = pagina.resolve(link.attr("href").strip());
                canonicalizer.canonicalizar(absoluto, TipoPesquisaWeb.SITE_PROPRIO)
                    .filter(uri -> host != null && host.equals(EmailSiteHost.de(uri.toString())))
                    .filter(uri -> !uri.equals(pagina))
                    .ifPresent(encontrados::add);
            } catch (IllegalArgumentException exception) {
                // Link malformado não interrompe a leitura da página.
            }
        }
        return List.copyOf(encontrados);
    }

    private List<URI> extrairLinks(Document documento, URI pagina) {
        Set<URI> links = new LinkedHashSet<>();
        for (Element elemento : documento.select(
            "a[href], link[rel~=me][href], [data-href], [data-url], [data-link], [onclick]")) {
            String href = elemento.attr("href");
            adicionarHref(links, pagina, href);
            adicionarUrlsEmbutidas(links, pagina, elemento.attr("data-href"));
            adicionarUrlsEmbutidas(links, pagina, elemento.attr("data-url"));
            adicionarUrlsEmbutidas(links, pagina, elemento.attr("data-link"));
            adicionarUrlsEmbutidas(links, pagina, elemento.attr("onclick"));
        }
        // SPAs frequentemente deixam o ícone social em JSON/estado inicial, sem um href.
        // O canonicalizer continua sendo a única porta de entrada para aceitar o perfil.
        adicionarUrlsEmbutidas(links, pagina, documento.html());
        return List.copyOf(links);
    }

    private void adicionarHref(Set<URI> links, URI pagina, String href) {
        if (href == null || href.isBlank()) {
            return;
        }
        try {
            URI absoluto = pagina.resolve(href.strip());
            canonicalizer.canonicalizar(absoluto, TipoPesquisaWeb.INSTAGRAM)
                .ifPresent(links::add);
        } catch (IllegalArgumentException exception) {
            // Um href malformado não pode interromper a leitura das demais evidências.
        }
    }

    private void adicionarUrlsEmbutidas(Set<URI> links, URI pagina, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        Matcher matcher = URL_INSTAGRAM_EMBUTIDA.matcher(valor.replace("\\/", "/"));
        while (matcher.find()) {
            adicionarHref(links, pagina, matcher.group());
        }
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
