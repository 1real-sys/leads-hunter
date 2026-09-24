package dev.jlm.leadshunter.lead;

import java.net.IDN;
import java.net.URI;
import java.util.Locale;

/** Host que vincula o e-mail capturado ao site informado para o lead. */
public final class EmailSiteHost {
    private EmailSiteHost() {
    }

    public static String de(String website) {
        if (website == null || website.isBlank()) return null;
        try {
            URI uri = URI.create(website.strip());
            String esquema = uri.getScheme();
            String host = uri.getHost();
            if (esquema == null || host == null || uri.getUserInfo() != null
                || !(esquema.equalsIgnoreCase("http") || esquema.equalsIgnoreCase("https"))) return null;
            String normalizado = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            return normalizado.startsWith("www.") ? normalizado.substring(4) : normalizado;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
