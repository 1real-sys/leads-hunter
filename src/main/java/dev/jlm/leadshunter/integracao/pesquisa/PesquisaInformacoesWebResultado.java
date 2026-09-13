package dev.jlm.leadshunter.integracao.pesquisa;

import java.net.URI;
import java.util.Optional;

public record PesquisaInformacoesWebResultado(Optional<URI> instagram, Optional<URI> siteProprio) {

    public PesquisaInformacoesWebResultado {
        instagram = instagram == null ? Optional.empty() : instagram;
        siteProprio = siteProprio == null ? Optional.empty() : siteProprio;
    }
}
