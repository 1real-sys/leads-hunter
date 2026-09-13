package dev.jlm.leadshunter.integracao.pesquisa;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class UrlCandidatoCanonicalizer {

    private static final Pattern USUARIO_INSTAGRAM = Pattern.compile("[a-z0-9._]{1,30}");
    private static final Pattern IPV4 = Pattern.compile("(?:\\d{1,3}\\.){3}\\d{1,3}");

    private static final Set<String> CAMINHOS_INSTAGRAM_PROIBIDOS = Set.of(
        "about", "accounts", "challenge", "developer", "direct", "directory", "emails",
        "explore", "legal", "login", "p", "privacy", "push", "reel", "reels", "share",
        "stories", "terms", "tv", "web"
    );

    private static final Set<String> DOMINIOS_NAO_OFICIAIS = Set.of(
        "google.com", "google.com.br", "googleusercontent.com", "gstatic.com", "googleapis.com",
        "bing.com", "facebook.com", "fb.com", "instagram.com", "threads.net", "tiktok.com",
        "twitter.com", "x.com", "youtube.com", "youtu.be", "linkedin.com", "pinterest.com",
        "wa.me", "whatsapp.com", "linktr.ee", "beacons.ai", "bio.site", "campsite.bio",
        "taplink.cc", "lnk.bio", "solo.to", "carrd.co", "ifood.com.br", "rappi.com.br",
        "aiqfome.com", "deliverymuch.com.br", "anota.ai", "mercadolivre.com.br", "amazon.com.br",
        "shopee.com.br", "olx.com.br", "tripadvisor.com", "tripadvisor.com.br", "yelp.com",
        "foursquare.com", "restaurantguru.com.br", "kekanto.com.br", "telelistas.net",
        "apontador.com.br", "solutudo.com.br", "cylex.com.br", "guiamais.com.br",
        "listamais.com.br", "econodata.com.br", "cnpj.biz", "casadosdados.com.br",
        "empresasdobrasil.com", "consultacnpj.com"
    );

    Optional<URI> canonicalizar(URI original, TipoPesquisaWeb tipo) {
        if (original == null || tipo == null) {
            return Optional.empty();
        }
        try {
            String esquema = normalizarEsquema(original.getScheme());
            String host = normalizarHost(original);
            if (esquema == null || host == null || original.getUserInfo() != null || hostPrivado(host)) {
                return Optional.empty();
            }
            if (original.getPort() != -1 && !portaPadrao(esquema, original.getPort())) {
                return Optional.empty();
            }
            return tipo == TipoPesquisaWeb.INSTAGRAM
                ? canonicalizarInstagram(original, host)
                : canonicalizarSite(esquema, host);
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return Optional.empty();
        }
    }

    String chaveDeduplicacao(URI uri, TipoPesquisaWeb tipo) {
        if (tipo == TipoPesquisaWeb.INSTAGRAM) {
            return uri.toString().toLowerCase(Locale.ROOT);
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private Optional<URI> canonicalizarInstagram(URI original, String host) throws URISyntaxException {
        if (!(host.equals("instagram.com") || host.equals("www.instagram.com") || host.equals("m.instagram.com"))) {
            return Optional.empty();
        }

        String caminho = original.getPath();
        if (caminho == null) {
            return Optional.empty();
        }
        String[] segmentos = caminho.split("/");
        String usuario = null;
        for (String segmento : segmentos) {
            if (!segmento.isBlank()) {
                if (usuario != null) {
                    return Optional.empty();
                }
                usuario = segmento.toLowerCase(Locale.ROOT);
            }
        }
        if (usuario == null
            || CAMINHOS_INSTAGRAM_PROIBIDOS.contains(usuario)
            || usuario.startsWith("#")
            || !USUARIO_INSTAGRAM.matcher(usuario).matches()
            || usuario.startsWith(".")
            || usuario.endsWith(".")) {
            return Optional.empty();
        }

        return Optional.of(new URI("https", null, "www.instagram.com", -1, "/" + usuario, null, null));
    }

    private Optional<URI> canonicalizarSite(String esquema, String host) throws URISyntaxException {
        if (dominioBloqueado(host)) {
            return Optional.empty();
        }
        return Optional.of(new URI(esquema, null, host, -1, "/", null, null));
    }

    private String normalizarEsquema(String esquema) {
        if (esquema == null) {
            return null;
        }
        String normalizado = esquema.toLowerCase(Locale.ROOT);
        return normalizado.equals("http") || normalizado.equals("https") ? normalizado : null;
    }

    private String normalizarHost(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return null;
        }
        return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
    }

    private boolean portaPadrao(String esquema, int porta) {
        return ("http".equals(esquema) && porta == 80) || ("https".equals(esquema) && porta == 443);
    }

    private boolean hostPrivado(String host) {
        return host.equals("localhost")
            || host.endsWith(".localhost")
            || host.endsWith(".local")
            || host.endsWith(".internal")
            || host.contains(":")
            || IPV4.matcher(host).matches()
            || !host.contains(".");
    }

    private boolean dominioBloqueado(String host) {
        for (String bloqueado : DOMINIOS_NAO_OFICIAIS) {
            if (host.equals(bloqueado) || host.endsWith("." + bloqueado)) {
                return true;
            }
        }
        return false;
    }
}
