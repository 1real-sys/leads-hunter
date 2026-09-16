package dev.jlm.leadshunter.integracao.pesquisa;

import java.util.Optional;

/** Consulta de evidência de um perfil já descoberto, sem transformar os termos em prova. */
public record ConfirmacaoPerfilInstagram(String usuario, String telefoneNacional) {

    public ConfirmacaoPerfilInstagram {
        if (usuario == null || !usuario.matches("[a-z0-9._]{1,30}")
            || telefoneNacional == null || !telefoneNacional.matches("[1-9]\\d{9,10}")) {
            throw new IllegalArgumentException("Perfil e telefone válidos são obrigatórios para confirmação");
        }
    }

    static Optional<String> telefoneNacional(String valor) {
        String numero = valor == null ? "" : valor.replaceAll("\\D", "");
        if ((numero.length() == 12 || numero.length() == 13) && numero.startsWith("55")) {
            numero = numero.substring(2);
        }
        return numero.matches("[1-9]\\d{9,10}") ? Optional.of(numero) : Optional.empty();
    }

    String consulta() {
        int inicioUltimosDigitos = telefoneNacional.length() - 4;
        return usuario + " " + telefoneNacional.substring(0, 2) + " "
            + telefoneNacional.substring(2, inicioUltimosDigitos) + "-"
            + telefoneNacional.substring(inicioUltimosDigitos);
    }
}
