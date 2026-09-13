package dev.jlm.leadshunter.integracao.pesquisa;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Escolhe a fonte ativa: API do Brave quando configurada; scraping apenas se habilitado explicitamente. */
@Component
@Primary
class PesquisaWebGateway implements GooglePesquisaGateway {

    private final BravePesquisaApiClient brave;
    private final PesquisaWebFallbackClient scraping;
    private final boolean scrapingHabilitado;

    @Autowired
    PesquisaWebGateway(
        BravePesquisaApiClient brave,
        PesquisaWebFallbackClient scraping,
        @Value("${pesquisa-inteligente.scraping.habilitado:false}") boolean scrapingHabilitado
    ) {
        this.brave = brave;
        this.scraping = scraping;
        this.scrapingHabilitado = scrapingHabilitado;
    }

    @Override
    public GooglePesquisaWebResponse pesquisar(GooglePesquisaWebRequest request) {
        if (brave.habilitado()) {
            return brave.pesquisar(request);
        }
        if (scrapingHabilitado) {
            return scraping.pesquisar(request);
        }
        throw new GooglePesquisaWebIndisponivelException(
            "Nenhuma fonte de pesquisa configurada. Defina a variável de ambiente BRAVE_SEARCH_API_KEY."
        );
    }
}
