package dev.jlm.leadshunter.integracao.pesquisa;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PesquisaWebInternaService implements PesquisaInformacoesGateway {

    private final GooglePesquisaGateway client;
    private final ClassificadorUrlService classificador;

    @Autowired
    public PesquisaWebInternaService(
        GooglePesquisaWebClient client,
        ClassificadorUrlService classificador
    ) {
        this((GooglePesquisaGateway) client, classificador);
    }

    PesquisaWebInternaService(
        GooglePesquisaGateway client,
        ClassificadorUrlService classificador
    ) {
        this.client = client;
        this.classificador = classificador;
    }

    @Override
    public PesquisaInformacoesWebResultado pesquisar(PesquisaLeadDados lead) {
        if (lead == null) {
            throw new IllegalArgumentException("lead é obrigatório");
        }

        GooglePesquisaWebResponse instagram = client.pesquisar(request(lead, TipoPesquisaWeb.INSTAGRAM));
        GooglePesquisaWebResponse site = client.pesquisar(request(lead, TipoPesquisaWeb.SITE_PROPRIO));
        return classificador.classificar(lead, instagram.resultados(), site.resultados());
    }

    private GooglePesquisaWebRequest request(PesquisaLeadDados lead, TipoPesquisaWeb tipo) {
        return new GooglePesquisaWebRequest(
            lead.googlePlaceId(),
            lead.nome(),
            lead.categoria(),
            lead.enderecoFormatado(),
            lead.municipio(),
            lead.uf(),
            tipo
        );
    }
}
