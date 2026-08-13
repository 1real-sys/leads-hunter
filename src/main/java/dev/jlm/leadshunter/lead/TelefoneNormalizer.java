package dev.jlm.leadshunter.lead;

import org.springframework.stereotype.Component;

@Component
public class TelefoneNormalizer {

    private static final String DDI_BRASIL = "55";

    public String normalizar(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return null;
        }

        String valor = telefone.trim();
        boolean formatoInternacional = valor.startsWith("+") || valor.startsWith("00");
        String digitos = valor.replaceAll("\\D", "");

        if (digitos.startsWith("00")) {
            digitos = digitos.substring(2);
        }

        if (digitos.startsWith(DDI_BRASIL)
            && (digitos.length() == 12 || digitos.length() == 13)) {
            String numeroNacional = digitos.substring(DDI_BRASIL.length());
            return numeroNacionalValido(numeroNacional) ? digitos : null;
        }

        if (formatoInternacional) {
            return null;
        }

        return numeroNacionalValido(digitos) ? DDI_BRASIL + digitos : null;
    }

    private boolean numeroNacionalValido(String numero) {
        if (numero.length() != 10 && numero.length() != 11) {
            return false;
        }

        int ddd = Integer.parseInt(numero.substring(0, 2));
        return ddd >= 11 && ddd <= 99;
    }
}
