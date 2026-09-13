package dev.jlm.leadshunter.integracao.pesquisa;

import java.net.URI;
import java.util.Locale;

enum FontePesquisaWeb {
    BING("https://www.bing.com/search"),
    GOOGLE("https://www.google.com/search"),
    DUCKDUCKGO("https://html.duckduckgo.com/html/"),
    BRAVE("https://search.brave.com/search");

    final URI endereco;

    FontePesquisaWeb(String endereco) { this.endereco = URI.create(endereco); }

    static FontePesquisaWeb deDestino(URI uri) {
        if (uri != null && "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
            && uri.getPort() == -1 && uri.getFragment() == null) {
            for (FontePesquisaWeb fonte : values()) {
                if (fonte.endereco.getHost().equalsIgnoreCase(uri.getHost())
                    && fonte.endereco.getPath().equals(uri.getPath())) return fonte;
            }
        }
        throw new IllegalArgumentException("Destino de pesquisa não permitido");
    }

    boolean permiteRecurso(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
            || uri.getPort() != -1 || uri.getHost() == null) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return switch (this) {
            case BING -> host.equals("bing.com") || host.endsWith(".bing.com");
            case GOOGLE -> dominio(host, "google.com") || dominio(host, "google.com.br")
                || dominio(host, "gstatic.com") || dominio(host, "googleusercontent.com");
            case DUCKDUCKGO -> host.equals("html.duckduckgo.com") || host.equals("duckduckgo.com");
            case BRAVE -> host.equals("search.brave.com") || host.equals("cdn.search.brave.com");
        };
    }

    private static boolean dominio(String host, String dominio) {
        return host.equals(dominio) || host.endsWith("." + dominio);
    }
}
