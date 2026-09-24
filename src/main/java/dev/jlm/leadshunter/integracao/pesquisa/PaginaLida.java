package dev.jlm.leadshunter.integracao.pesquisa;

import java.net.URI;
import java.util.List;

public record PaginaLida(String texto, List<URI> links, List<String> emails, List<URI> linksContato) {

    public PaginaLida(String texto, List<URI> links) {
        this(texto, links, List.of(), List.of());
    }

    public PaginaLida {
        texto = texto == null ? "" : texto;
        links = links == null ? List.of() : List.copyOf(links);
        emails = emails == null ? List.of() : List.copyOf(emails);
        linksContato = linksContato == null ? List.of() : List.copyOf(linksContato);
    }
}
