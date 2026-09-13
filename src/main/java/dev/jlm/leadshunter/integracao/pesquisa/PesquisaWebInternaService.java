package dev.jlm.leadshunter.integracao.pesquisa;

import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PesquisaWebInternaService implements PesquisaInformacoesGateway {

    private final GooglePesquisaGateway client;
    private final ClassificadorUrlService classificador;

    @Autowired
    public PesquisaWebInternaService(
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
        var candidatos = new ArrayList<>(instagram.resultados());
        candidatos.addAll(site.resultados());
        PesquisaInformacoesWebResultado resultado = classificador.classificar(lead, candidatos, candidatos);

        if (resultado.instagram().isEmpty() && possuiLocalizacao(lead)) {
            GooglePesquisaWebResponse semLocal = client.pesquisar(request(lead, TipoPesquisaWeb.INSTAGRAM, true));
            candidatos.addAll(semLocal.resultados());
            // Reavalia o conjunto completo: outra consulta não pode apagar homônimos/conflitos anteriores.
            return classificador.classificar(lead, candidatos, candidatos);
        }
        return resultado;
    }

    private boolean possuiLocalizacao(PesquisaLeadDados lead) {
        return lead.municipio() != null && !lead.municipio().isBlank();
    }

    private GooglePesquisaWebRequest request(PesquisaLeadDados lead, TipoPesquisaWeb tipo) {
        return request(lead, tipo, false);
    }

    private GooglePesquisaWebRequest request(PesquisaLeadDados lead, TipoPesquisaWeb tipo, boolean semLocalizacao) {
        return new GooglePesquisaWebRequest(
            lead.googlePlaceId(),
            lead.nome(),
            lead.categoria(),
            semLocalizacao ? null : lead.enderecoFormatado(),
            semLocalizacao ? null : lead.municipio(),
            semLocalizacao ? null : lead.uf(),
            tipo
        );
    }
}
