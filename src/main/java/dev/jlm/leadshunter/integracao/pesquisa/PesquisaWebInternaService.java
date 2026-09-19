package dev.jlm.leadshunter.integracao.pesquisa;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final UrlCandidatoCanonicalizer canonicalizer;

    PesquisaWebInternaService(GooglePesquisaGateway client, ClassificadorUrlService classificador) {
        this(client, classificador, LeitorPaginaCandidata.nenhum(), new UrlCandidatoCanonicalizer());
    }

    @Autowired
    public PesquisaWebInternaService(
        GooglePesquisaGateway client,
        ClassificadorUrlService classificador,
        LeitorPaginaCandidata leitor,
        UrlCandidatoCanonicalizer canonicalizer
    ) {
        this.client = client;
        this.classificador = classificador;
        this.leitor = leitor;
        this.canonicalizer = canonicalizer;
    }

    PesquisaWebInternaService(
        GooglePesquisaGateway client,
        ClassificadorUrlService classificador,
        LeitorPaginaCandidata leitor
    ) {
        this(client, classificador, leitor, new UrlCandidatoCanonicalizer());
    }

    @Override
    public PesquisaInformacoesWebResultado pesquisar(PesquisaLeadDados lead) {
        return pesquisar(lead, true);
    }

    @Override
    public PesquisaInformacoesWebResultado pesquisar(PesquisaLeadDados lead, boolean usarBrave) {
        if (lead == null) {
            throw new IllegalArgumentException("lead é obrigatório");
        }

        var candidatos = new ArrayList<GoogleResultadoWeb>();
        Set<URI> paginasLidas = new LinkedHashSet<>();
        int buscas = 0;
        int paginas = 0;
        boolean siteConfirmado = false;
        PesquisaInformacoesWebResultado resultado = new PesquisaInformacoesWebResultado(null, null);

        Optional<URI> siteOficial = siteOficial(lead);
        if (siteOficial.isPresent()) {
            URI url = siteOficial.orElseThrow();
            candidatos.add(new GoogleResultadoWeb(url, lead.nome(), ""));
            paginasLidas.add(url);
            paginas++;
            leitor.lerPagina(url).ifPresent(pagina -> adicionarEvidenciasDaPagina(
                lead, candidatos, url, lead.nome(), pagina));
            resultado = classificar(lead, candidatos);
            if (!possuiAmbos(resultado)) {
                paginas = abrirPaginasPendentes(lead, candidatos, paginasLidas, paginas, resultado);
                resultado = classificar(lead, candidatos);
            }
            siteConfirmado = possuiResultado(resultado);
        }

        if (!siteConfirmado && usarBrave) {
            candidatos.addAll(client.pesquisar(request(lead, TipoPesquisaWeb.INSTAGRAM)).resultados());
            buscas++;
            candidatos.addAll(client.pesquisar(request(lead, TipoPesquisaWeb.SITE_PROPRIO)).resultados());
            buscas++;
            resultado = classificar(lead, candidatos);

            if (resultado.instagram().isEmpty() && possuiLocalizacao(lead) && buscas < MAXIMO_BUSCAS) {
                candidatos.addAll(client.pesquisar(request(lead, TipoPesquisaWeb.INSTAGRAM, true)).resultados());
                buscas++;
                // Reavalia o conjunto completo: outra consulta não pode apagar homônimos/conflitos anteriores.
                resultado = classificar(lead, candidatos);
            }
        }

        // Valida candidatos pendentes abrindo a própria página. Falha ou bloqueio não concluem ausência.
        if (!possuiAmbos(resultado)) {
            paginas = abrirPaginasPendentes(lead, candidatos, paginasLidas, paginas, resultado);
            resultado = classificar(lead, candidatos);
        }

        var telefone = ConfirmacaoPerfilInstagram.telefoneNacional(lead.telefoneNormalizado());
        if (usarBrave && !siteConfirmado && resultado.instagram().isEmpty()
            && telefone.isPresent() && buscas < MAXIMO_BUSCAS) {
            // No máximo uma confirmação, respeitando o teto total de consultas por lead.
            for (var perfil : classificador.perfisParaConfirmar(lead, candidatos)) {
                if (buscas >= MAXIMO_BUSCAS) break;
                var consulta = new GooglePesquisaWebRequest(lead.googlePlaceId(), lead.nome(), lead.categoria(),
                    null, null, null, TipoPesquisaWeb.INSTAGRAM,
                    new ConfirmacaoPerfilInstagram(perfil.getPath().substring(1), telefone.orElseThrow()));
                candidatos.addAll(client.pesquisar(consulta).resultados());
                buscas++;
            }
            resultado = classificar(lead, candidatos);
        }
        return resultado;
    }

    private PesquisaInformacoesWebResultado classificar(
        PesquisaLeadDados lead,
        List<GoogleResultadoWeb> candidatos
    ) {
        return classificador.classificar(lead, candidatos, candidatos);
    }

    private int abrirPaginasPendentes(
        PesquisaLeadDados lead,
        ArrayList<GoogleResultadoWeb> candidatos,
        Set<URI> paginasLidas,
        int paginas,
        PesquisaInformacoesWebResultado resultado
    ) {
        while (paginas < MAXIMO_PAGINAS_VALIDADAS && !possuiAmbos(resultado)) {
            var pendentes = classificador.candidatosParaValidar(lead, candidatos).stream()
                .filter(candidato -> candidato != null && candidato.url() != null
                    && !paginasLidas.contains(candidato.url()))
                .toList();
            if (pendentes.isEmpty()) {
                break;
            }
            for (var candidato : pendentes) {
                if (paginas >= MAXIMO_PAGINAS_VALIDADAS || possuiAmbos(resultado)) break;
                URI url = candidato.url();
                if (!paginasLidas.add(url)) continue;
                paginas++;
                var pagina = leitor.lerPagina(url);
                if (pagina.isEmpty()) continue;
                adicionarEvidenciasDaPagina(lead, candidatos, url, candidato.titulo(), pagina.orElseThrow());
                resultado = classificar(lead, candidatos);
            }
        }
        return paginas;
    }

    private void adicionarEvidenciasDaPagina(
        PesquisaLeadDados lead,
        ArrayList<GoogleResultadoWeb> candidatos,
        URI url,
        String titulo,
        PaginaLida pagina
    ) {
        if (!pagina.texto().isBlank()) {
            candidatos.add(new GoogleResultadoWeb(url, titulo, pagina.texto()));
        }
        for (URI instagram : pagina.links()) {
            candidatos.add(new GoogleResultadoWeb(instagram, lead.nome(), pagina.texto()));
        }
    }

    private boolean possuiResultado(PesquisaInformacoesWebResultado resultado) {
        return resultado != null && (resultado.instagram().isPresent() || resultado.siteProprio().isPresent());
    }

    private boolean possuiAmbos(PesquisaInformacoesWebResultado resultado) {
        return resultado != null && resultado.instagram().isPresent() && resultado.siteProprio().isPresent();
    }

    private Optional<URI> siteOficial(PesquisaLeadDados lead) {
        if (lead.website() == null || lead.website().isBlank()) {
            return Optional.empty();
        }
        try {
            return canonicalizer.canonicalizar(URI.create(lead.website().strip()), TipoPesquisaWeb.SITE_PROPRIO);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
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
