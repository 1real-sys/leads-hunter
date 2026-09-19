package dev.jlm.leadshunter.busca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jlm.leadshunter.integracao.pesquisa.FormatadorObservacoesPesquisa;
import dev.jlm.leadshunter.integracao.pesquisa.GooglePesquisaWebBloqueadaException;
import dev.jlm.leadshunter.integracao.pesquisa.GooglePesquisaWebException;
import dev.jlm.leadshunter.integracao.pesquisa.GooglePesquisaWebTimeoutException;
import dev.jlm.leadshunter.integracao.pesquisa.PesquisaInformacoesGateway;
import dev.jlm.leadshunter.integracao.pesquisa.PesquisaInformacoesWebResultado;
import dev.jlm.leadshunter.integracao.pesquisa.PesquisaLeadDados;
import dev.jlm.leadshunter.integracao.pesquisa.UrlCandidatoCanonicalizer;
import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BuscaInformacoesServiceTest {

    private final FormatadorObservacoesPesquisa formatador =
        new FormatadorObservacoesPesquisa(new UrlCandidatoCanonicalizer());

    @Test
    void deveContabilizarTodosOsTiposDeResultado() {
        FakePersistencia persistencia = persistencia(
            lead(1, null), lead(2, null), lead(3, null), lead(4, null)
        );
        FakePesquisa pesquisa = new FakePesquisa(
            resultado("https://www.instagram.com/um", "https://um.example/"),
            resultado("https://www.instagram.com/dois", null),
            resultado(null, "https://tres.example/"),
            resultado(null, null)
        );

        BuscaInformacoesResponse resposta = service(persistencia, pesquisa).buscarInformacoes(42L);

        assertThat(resposta).isEqualTo(new BuscaInformacoesResponse(4, 4, 0, 2, 2, 1, 1, 0));
        assertThat(persistencia.observacoes.get(4L))
            .contains(FormatadorObservacoesPesquisa.SEM_INFORMACOES);
    }

    @Test
    void deveIgnorarCompletoESomarLinkNovoAoResultadoParcial() {
        String completo = formatador.atualizar(null, resultado(
            "https://www.instagram.com/completo", "https://completo.example/"
        ));
        String parcial = formatador.atualizar(null, resultado(
            "https://www.instagram.com/parcial", null
        ));
        FakePersistencia persistencia = persistencia(lead(1, completo), lead(2, parcial));
        FakePesquisa pesquisa = new FakePesquisa(resultado(null, "https://parcial.example/"));

        BuscaInformacoesResponse resposta = service(persistencia, pesquisa).buscarInformacoes(42L);

        assertThat(resposta).isEqualTo(new BuscaInformacoesResponse(2, 1, 1, 1, 1, 1, 0, 0));
        assertThat(pesquisa.placeIdsConsultados).containsExactly("place-2");
        assertThat(persistencia.observacoes.get(2L))
            .contains("https://www.instagram.com/parcial", "https://parcial.example/");
    }

    @Test
    void devePreservarObservacaoNaFalhaEContinuarProximoLead() {
        String manual = "Não apagar";
        FakePersistencia persistencia = persistencia(lead(1, manual), lead(2, null));
        FakePesquisa pesquisa = new FakePesquisa(
            new GooglePesquisaWebTimeoutException(),
            resultado(null, "https://segundo.example/")
        );

        BuscaInformacoesResponse resposta = service(persistencia, pesquisa).buscarInformacoes(42L);

        assertThat(resposta).isEqualTo(new BuscaInformacoesResponse(2, 1, 0, 0, 1, 0, 0, 1));
        assertThat(persistencia.observacoes.get(1L)).isEqualTo(manual);
        assertThat(persistencia.idsAtualizados).containsExactly(2L);
    }

    @Test
    void devePararImediatamenteNoBloqueioESemConsultarRestantes() {
        FakePersistencia persistencia = persistencia(lead(1, null), lead(2, null), lead(3, null));
        FakePesquisa pesquisa = new FakePesquisa(
            resultado(null, "https://primeiro.example/"),
            new GooglePesquisaWebBloqueadaException(),
            resultado(null, "https://nao-consultar.example/")
        );

        BuscaInformacoesResponse resposta = service(persistencia, pesquisa).buscarInformacoes(42L);

        assertThat(resposta).isEqualTo(new BuscaInformacoesResponse(3, 1, 0, 0, 1, 0, 0, 2));
        assertThat(pesquisa.placeIdsConsultados).containsExactly("place-1", "place-2");
        assertThat(persistencia.idsAtualizados).containsExactly(1L);
    }

    @Test
    void deveInterromperAposTresFalhasTecnicasConsecutivas() {
        FakePersistencia persistencia = persistencia(
            lead(1, null), lead(2, null), lead(3, null), lead(4, null)
        );
        FakePesquisa pesquisa = new FakePesquisa(
            new GooglePesquisaWebTimeoutException(),
            new GooglePesquisaWebTimeoutException(),
            new GooglePesquisaWebTimeoutException(),
            resultado(null, "https://nao-consultar.example/")
        );

        BuscaInformacoesResponse resposta = service(persistencia, pesquisa).buscarInformacoes(42L);

        assertThat(resposta).isEqualTo(new BuscaInformacoesResponse(4, 0, 0, 0, 0, 0, 0, 4));
        assertThat(pesquisa.placeIdsConsultados).containsExactly("place-1", "place-2", "place-3");
        assertThat(persistencia.idsAtualizados).isEmpty();
    }

    @Test
    void devePropagarBuscaInexistenteSemIniciarPesquisa() {
        FakePersistencia persistencia = persistencia();
        persistencia.buscaExiste = false;
        FakePesquisa pesquisa = new FakePesquisa();

        assertThatThrownBy(() -> service(persistencia, pesquisa).buscarInformacoes(999L))
            .isInstanceOf(BuscaNaoEncontradaException.class);
        assertThat(pesquisa.placeIdsConsultados).isEmpty();
    }

    @Test
    void deveRepassarPreferenciaDeNaoUsarBrave() {
        FakePersistencia persistencia = persistencia(lead(1, null));
        FakePesquisa pesquisa = new FakePesquisa(resultado(null, null));

        service(persistencia, pesquisa).buscarInformacoes(42L, false);

        assertThat(pesquisa.usosBrave).containsExactly(false);
    }

    private BuscaInformacoesService service(
        FakePersistencia persistencia,
        PesquisaInformacoesGateway pesquisa
    ) {
        return new BuscaInformacoesService(persistencia, pesquisa, formatador);
    }

    private FakePersistencia persistencia(BuscaInformacoesLead... leads) {
        return new FakePersistencia(List.of(leads), formatador);
    }

    private BuscaInformacoesLead lead(long id, String observacoes) {
        return new BuscaInformacoesLead(id, new PesquisaLeadDados(
            "place-" + id,
            "Lead " + id,
            CategoriaNegocio.PADARIA,
            "Rua Teste, " + id,
            "Rua Teste",
            Long.toString(id),
            "Centro",
            "Vitória",
            "ES",
            null,
            null,
            null
        ), observacoes);
    }

    private PesquisaInformacoesWebResultado resultado(String instagram, String site) {
        return new PesquisaInformacoesWebResultado(uri(instagram), uri(site));
    }

    private Optional<URI> uri(String valor) {
        return valor == null ? Optional.empty() : Optional.of(URI.create(valor));
    }

    private static final class FakePesquisa implements PesquisaInformacoesGateway {

        private final ArrayDeque<Object> respostas = new ArrayDeque<>();
        private final List<String> placeIdsConsultados = new ArrayList<>();
        private final List<Boolean> usosBrave = new ArrayList<>();

        private FakePesquisa(Object... respostas) {
            this.respostas.addAll(List.of(respostas));
        }

        @Override
        public PesquisaInformacoesWebResultado pesquisar(PesquisaLeadDados lead, boolean usarBrave) {
            placeIdsConsultados.add(lead.googlePlaceId());
            usosBrave.add(usarBrave);
            Object resposta = respostas.removeFirst();
            if (resposta instanceof GooglePesquisaWebException exception) {
                throw exception;
            }
            return (PesquisaInformacoesWebResultado) resposta;
        }
    }

    private static final class FakePersistencia implements BuscaInformacoesPersistencia {

        private final List<BuscaInformacoesLead> leads;
        private final FormatadorObservacoesPesquisa formatador;
        private final Map<Long, String> observacoes = new LinkedHashMap<>();
        private final List<Long> idsAtualizados = new ArrayList<>();
        private boolean buscaExiste = true;

        private FakePersistencia(
            List<BuscaInformacoesLead> leads,
            FormatadorObservacoesPesquisa formatador
        ) {
            this.leads = leads;
            this.formatador = formatador;
            leads.forEach(lead -> observacoes.put(lead.leadId(), lead.observacoes()));
        }

        @Override
        public List<BuscaInformacoesLead> carregarLeads(Long buscaId) {
            if (!buscaExiste) {
                throw new BuscaNaoEncontradaException(buscaId);
            }
            return leads;
        }

        @Override
        public PesquisaInformacoesWebResultado atualizarObservacoes(
            Long leadId,
            PesquisaInformacoesWebResultado resultado
        ) {
            String atualizadas = formatador.atualizar(observacoes.get(leadId), resultado);
            observacoes.put(leadId, atualizadas);
            idsAtualizados.add(leadId);
            return formatador.extrairLinks(atualizadas);
        }
    }
}
