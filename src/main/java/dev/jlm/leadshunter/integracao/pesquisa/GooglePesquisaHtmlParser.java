package dev.jlm.leadshunter.integracao.pesquisa;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Component
class GooglePesquisaHtmlParser {

    private static final Pattern ESPACOS = Pattern.compile("\\s+");
    private static final int TAMANHO_MAXIMO_RESUMO = 500;

    List<GoogleResultadoWeb> extrair(GooglePesquisaPagina pagina, int limite) {
        try {
            Document documento = Jsoup.parse(pagina.html(), pagina.urlFinal().toString());
            validarBloqueio(documento, pagina);

            List<GoogleResultadoWeb> resultados = extrairResultados(documento, pagina.urlFinal(), limite);
            if (!resultados.isEmpty()) {
                return resultados;
            }
            if (representaAusenciaReal(documento)) {
                return List.of();
            }
            throw new GooglePesquisaWebFormatoInvalidoException();
        } catch (GooglePesquisaWebException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GooglePesquisaWebFormatoInvalidoException(exception);
        }
    }

    private void validarBloqueio(Document documento, GooglePesquisaPagina pagina) {
        String texto = documento.text().toLowerCase(Locale.ROOT);
        boolean captcha = documento.selectFirst("form[action*=\"/sorry\"], #captcha, [id*=captcha]") != null;
        boolean javascriptObrigatorio = documento.selectFirst("a[href*=enablejs]") != null
            && texto.contains("javascript");
        boolean bloqueioTextual = texto.contains("tráfego incomum")
            || texto.contains("trafego incomum")
            || texto.contains("unusual traffic")
            || texto.contains("não sou um robô")
            || texto.contains("not a robot");

        if (pagina.statusHttp() == 429 || captcha || javascriptObrigatorio || bloqueioTextual) {
            throw new GooglePesquisaWebBloqueadaException();
        }
        if (pagina.statusHttp() < 200 || pagina.statusHttp() >= 400) {
            throw new GooglePesquisaWebIndisponivelException();
        }
    }

    private List<GoogleResultadoWeb> extrairResultados(Document documento, URI paginaUrl, int limite) {
        List<GoogleResultadoWeb> resultados = new ArrayList<>();
        Set<URI> urlsVistas = new HashSet<>();

        for (Element link : documento.select("a:has(h3)")) {
            Element cabecalho = link.selectFirst("h3");
            if (cabecalho == null || cabecalho.text().isBlank()) {
                continue;
            }

            Optional<URI> url = normalizarUrl(link.attr("href"), paginaUrl);
            if (url.isEmpty() || !urlsVistas.add(url.get())) {
                continue;
            }

            String titulo = compactar(cabecalho.text());
            resultados.add(new GoogleResultadoWeb(url.get(), titulo, extrairResumo(link, titulo)));
            if (resultados.size() == limite) {
                break;
            }
        }

        return List.copyOf(resultados);
    }

    private Optional<URI> normalizarUrl(String href, URI paginaUrl) {
        if (href == null || href.isBlank()) {
            return Optional.empty();
        }

        try {
            URI url = paginaUrl.resolve(new URI(href));
            if (hostGoogle(url.getHost()) && "/url".equals(url.getPath())) {
                String destino = UriComponentsBuilder.fromUri(url).build().getQueryParams().getFirst("q");
                if (destino == null || destino.isBlank()) {
                    return Optional.empty();
                }
                url = new URI(UriUtils.decode(destino, java.nio.charset.StandardCharsets.UTF_8));
            }

            String esquema = url.getScheme();
            if (!("http".equalsIgnoreCase(esquema) || "https".equalsIgnoreCase(esquema))) {
                return Optional.empty();
            }
            if (hostGoogle(url.getHost())) {
                return Optional.empty();
            }
            return Optional.of(url.normalize());
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return Optional.empty();
        }
    }

    private String extrairResumo(Element link, String titulo) {
        Element bloco = link.closest(".MjjYud, .g, [data-snhf]");
        if (bloco == null) {
            bloco = buscarBlocoProximo(link, titulo);
        }
        if (bloco == null) {
            return "";
        }

        String resumo = compactar(bloco.text());
        if (resumo.startsWith(titulo)) {
            resumo = resumo.substring(titulo.length()).strip();
        }
        if (resumo.length() > TAMANHO_MAXIMO_RESUMO) {
            resumo = resumo.substring(0, TAMANHO_MAXIMO_RESUMO).strip();
        }
        return resumo;
    }

    private Element buscarBlocoProximo(Element link, String titulo) {
        Element atual = link.parent();
        for (int nivel = 0; nivel < 5 && atual != null && !"body".equals(atual.normalName()); nivel++) {
            String texto = compactar(atual.text());
            if (texto.length() > titulo.length() + 8 && texto.length() <= 2_000) {
                return atual;
            }
            atual = atual.parent();
        }
        return null;
    }

    private boolean representaAusenciaReal(Document documento) {
        String texto = documento.text().toLowerCase(Locale.ROOT);
        return texto.contains("não encontrou nenhum documento")
            || texto.contains("não há resultados para")
            || texto.contains("nenhum resultado encontrado")
            || texto.contains("did not match any documents")
            || texto.contains("no results found");
    }

    private boolean hostGoogle(String host) {
        if (host == null) {
            return false;
        }
        String normalizado = host.toLowerCase(Locale.ROOT);
        return normalizado.equals("google.com")
            || normalizado.endsWith(".google.com")
            || normalizado.equals("google.com.br")
            || normalizado.endsWith(".google.com.br");
    }

    private String compactar(String valor) {
        return ESPACOS.matcher(valor == null ? "" : valor).replaceAll(" ").strip();
    }
}
