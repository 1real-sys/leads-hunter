package dev.jlm.leadshunter.integracao.pesquisa;

import java.net.URI;
import java.util.List;

public record PaginaLida(String texto, List<URI> links) {

    public PaginaLida {
        texto = texto == null ? "" : texto;
        links = links == null ? List.of() : List.copyOf(links);
    }
}
