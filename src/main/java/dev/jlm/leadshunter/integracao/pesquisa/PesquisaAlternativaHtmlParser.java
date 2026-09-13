package dev.jlm.leadshunter.integracao.pesquisa;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Component
class PesquisaAlternativaHtmlParser {
    List<GoogleResultadoWeb> extrair(FontePesquisaWeb fonte, GooglePesquisaPagina pagina, int limite) {
        var documento = Jsoup.parse(pagina.html(), pagina.urlFinal().toString());
        String texto = documento.text().toLowerCase(Locale.ROOT);
        if (pagina.statusHttp() == 429 || pagina.statusHttp() == 403
            || documento.selectFirst("#captcha, [id*=captcha], form[action*=anomaly], .anomaly-modal") != null
            || texto.contains("unusual traffic") || texto.contains("verify you are human")
            || texto.contains("confirm you are a human") || texto.contains("select all squares")
            || texto.contains("bots use duckduckgo") || texto.contains("proof of work")) {
            throw new GooglePesquisaWebBloqueadaException();
        }
        if (pagina.statusHttp() < 200 || pagina.statusHttp() >= 400) throw new GooglePesquisaWebIndisponivelException();
        var resultados = new ArrayList<GoogleResultadoWeb>();
        var vistos = new HashSet<URI>();
        for (Element bloco : documento.select(blocoResultado(fonte))) {
            Element link = bloco.selectFirst(linkResultado(fonte));
            if (link == null || link.text().isBlank()) continue;
            try {
                URI url = resolverDestino(fonte, pagina.urlFinal().resolve(link.attr("href")));
                if (url == null || url.getHost() == null || url.getUserInfo() != null
                    || !("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()))
                    || fonte.permiteRecurso(url) || !vistos.add(url)) continue;
                Element resumo = bloco.selectFirst(resumoResultado(fonte));
                String descricao = resumo == null ? "" : resumo.text();
                resultados.add(new GoogleResultadoWeb(url, link.text(), descricao.substring(0, Math.min(500, descricao.length()))));
                if (resultados.size() == limite) break;
            } catch (IllegalArgumentException ignored) {
                // Um candidato malformado não invalida outros resultados.
            }
        }
        if (!resultados.isEmpty()) return List.copyOf(resultados);
        if (texto.contains("no results found") || texto.contains("there are no results")
            || texto.contains("não foram encontrados resultados")
            || texto.contains("nenhum resultado encontrado")) {
            // A mensagem de ausência só é conclusiva sem resultados orgânicos reconhecidos.
            return List.of();
        }
        throw new GooglePesquisaWebFormatoInvalidoException();
    }

    private String blocoResultado(FontePesquisaWeb fonte) {
        return switch (fonte) {
            case BING -> "li.b_algo";
            case DUCKDUCKGO -> ".result:not(.result--ad)";
            case GOOGLE, BRAVE -> "#web .snippet";
        };
    }

    private String linkResultado(FontePesquisaWeb fonte) {
        return switch (fonte) {
            case BING -> "h2 a";
            case DUCKDUCKGO -> "a.result__a";
            case GOOGLE, BRAVE -> "a.heading-serpresult";
        };
    }

    private String resumoResultado(FontePesquisaWeb fonte) {
        return switch (fonte) {
            case BING -> ".b_caption p, .b_algoSlug";
            case DUCKDUCKGO -> ".result__snippet";
            case GOOGLE, BRAVE -> ".snippet-description";
        };
    }

    private URI resolverDestino(FontePesquisaWeb fonte, URI url) {
        if (fonte == FontePesquisaWeb.DUCKDUCKGO && fonte.permiteRecurso(url)) {
            String destino = UriComponentsBuilder.fromUri(url).build().getQueryParams().getFirst("uddg");
            return destino == null ? null : URI.create(UriUtils.decode(destino, StandardCharsets.UTF_8));
        }
        if (fonte == FontePesquisaWeb.BING && fonte.permiteRecurso(url)
            && url.getPath() != null && url.getPath().startsWith("/ck/a")) {
            return decodificarRedirecionamentoBing(
                UriComponentsBuilder.fromUri(url).build().getQueryParams().getFirst("u"));
        }
        return url;
    }

    private URI decodificarRedirecionamentoBing(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String base64 = valor.startsWith("a1") ? valor.substring(2) : valor;
        int sobra = base64.length() % 4;
        if (sobra != 0) base64 = base64 + "=".repeat(4 - sobra);
        try {
            return URI.create(new String(Base64.getUrlDecoder().decode(base64), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
