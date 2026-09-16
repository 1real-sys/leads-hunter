package dev.jlm.leadshunter.integracao.pesquisa;

import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PesquisaWebInternaService implements PesquisaInformacoesGateway {

    /** Teto de consultas ao buscador pago por lead: duas de descoberta e uma de fallback/confirmação. */
    static final int MAXIMO_BUSCAS = 3;
    /** Teto de páginas abertas por lead, sempre a partir de candidatos já relacionados. */
    static final int MAXIMO_PAGINAS_VALIDADAS = 3;

    private final GooglePesquisaGateway client;
    private final ClassificadorUrlService classificador;
    private final LeitorPaginaCandidata leitor;

    PesquisaWebInternaService(GooglePesquisaGateway client, ClassificadorUrlService classificador) {
        this(client, classificador, LeitorPaginaCandidata.nenhum());
    }

    @Autowired
    public PesquisaWebInternaService(
        GooglePesquisaGateway client,
        ClassificadorUrlService classificador,
        LeitorPaginaCandidata leitor
    ) {
        this.client = client;
        this.classificador = classificador;
        this.leitor = leitor;
    }

    @Override
    public PesquisaInformacoesWebResultado pesquisar(PesquisaLeadDados lead) {
        if (lead == null) {
            throw new IllegalArgumentException("lead é obrigatório");
        }

        var candidatos = new ArrayList<GoogleResultadoWeb>();
        int buscas = 0;

        candidatos.addAll(client.pesquisar(request(lead, TipoPesquisaWeb.INSTAGRAM)).resultados());
        buscas++;
        candidatos.addAll(client.pesquisar(request(lead, TipoPesquisaWeb.SITE_PROPRIO)).resultados());
        buscas++;
        PesquisaInformacoesWebResultado resultado = classificador.classificar(lead, candidatos, candidatos);

        if (resultado.instagram().isEmpty() && possuiLocalizacao(lead) && buscas < MAXIMO_BUSCAS) {
            candidatos.addAll(client.pesquisar(request(lead, TipoPesquisaWeb.INSTAGRAM, true)).resultados());
            buscas++;
            // Reavalia o conjunto completo: outra consulta não pode apagar homônimos/conflitos anteriores.
            resultado = classificador.classificar(lead, candidatos, candidatos);
        }

        // Valida candidatos pendentes abrindo a própria página. Falha ou bloqueio não concluem ausência.
        int paginas = 0;
        for (var candidato : classificador.candidatosParaValidar(lead, candidatos)) {
            if (paginas >= MAXIMO_PAGINAS_VALIDADAS) break;
            if (resultado.instagram().isPresent() && resultado.siteProprio().isPresent()) break;
            paginas++;
            var conteudo = leitor.ler(candidato.url());
            if (conteudo.isEmpty()) continue;
            candidatos.add(new GoogleResultadoWeb(candidato.url(), candidato.titulo(), conteudo.orElseThrow()));
            resultado = classificador.classificar(lead, candidatos, candidatos);
        }

        var telefone = ConfirmacaoPerfilInstagram.telefoneNacional(lead.telefoneNormalizado());
        if (resultado.instagram().isEmpty() && telefone.isPresent() && buscas < MAXIMO_BUSCAS) {
            // No máximo uma confirmação, respeitando o teto total de consultas por lead.
            for (var perfil : classificador.perfisParaConfirmar(lead, candidatos)) {
                if (buscas >= MAXIMO_BUSCAS) break;
                var consulta = new GooglePesquisaWebRequest(lead.googlePlaceId(), lead.nome(), lead.categoria(),
                    null, null, null, TipoPesquisaWeb.INSTAGRAM,
                    new ConfirmacaoPerfilInstagram(perfil.getPath().substring(1), telefone.orElseThrow()));
                candidatos.addAll(client.pesquisar(consulta).resultados());
                buscas++;
            }
            resultado = classificador.classificar(lead, candidatos, candidatos);
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
