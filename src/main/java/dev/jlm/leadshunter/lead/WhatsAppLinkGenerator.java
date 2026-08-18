package dev.jlm.leadshunter.lead;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppLinkGenerator {

    private static final String URL_BASE = "https://wa.me/";
    private static final Pattern TELEFONE_BRASILEIRO_NORMALIZADO = Pattern.compile(
        "^55(?:1[1-9]|[2-9][0-9])\\d{8,9}$"
    );

    public String gerar(String telefoneNormalizado) {
        if (telefoneNormalizado == null
            || !TELEFONE_BRASILEIRO_NORMALIZADO.matcher(telefoneNormalizado).matches()) {
            return null;
        }

        return URL_BASE + telefoneNormalizado;
    }
}
