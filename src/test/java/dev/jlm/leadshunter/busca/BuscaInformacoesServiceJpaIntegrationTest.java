package dev.jlm.leadshunter.busca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jlm.leadshunter.integracao.pesquisa.FormatadorObservacoesPesquisa;
import dev.jlm.leadshunter.integracao.pesquisa.PesquisaInformacoesGateway;
import dev.jlm.leadshunter.integracao.pesquisa.PesquisaInformacoesWebResultado;
import dev.jlm.leadshunter.integracao.pesquisa.PesquisaLeadDados;
import dev.jlm.leadshunter.lead.CategoriaNegocio;
import dev.jlm.leadshunter.lead.Lead;
import dev.jlm.leadshunter.lead.LeadRepository;
import dev.jlm.leadshunter.lead.StatusFunil;
import dev.jlm.leadshunter.lead.Temperatura;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BuscaInformacoesServiceJpaIntegrationTest {

    @Autowired private BuscaInformacoesPersistencia persistencia;
    @Autowired private FormatadorObservacoesPesquisa formatador;
    @Autowired private BuscaRepository buscaRepository;
    @Autowired private BuscaLeadRepository buscaLeadRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void devePersistirResultadosPreservarDadosELimitarLeadsABusca() {
        Busca alvo = salvarBusca("PADARIA");
        Busca outra = salvarBusca("RESTAURANTE");
        Lead ambos = salvarLead("Observação manual: ambos");
        Lead instagram = salvarLead(null);
        Lead site = salvarLead("Observação manual: site");
        Lead vazio = salvarLead(null);
        Lead fora = salvarLead("Fora da busca");
        vincular(alvo, ambos, 90);
        vincular(alvo, instagram, 80);
        vincular(alvo, site, 70);
        vincular(alvo, vazio, 60);
        vincular(outra, fora, 99);
        entityManager.flush();
        entityManager.clear();

        PesquisaSequencial pesquisa = new PesquisaSequencial(
            resultado("https://www.instagram.com/ambos", "https://ambos.example/"),
            resultado("https://www.instagram.com/instagram", null),
            resultado(null, "https://site.example/"),
            resultado(null, null)
        );
        BuscaInformacoesService service = new BuscaInformacoesService(
            persistencia,
            pesquisa,
            formatador
        );

        BuscaInformacoesResponse resposta = service.buscarInformacoes(alvo.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(resposta).isEqualTo(new BuscaInformacoesResponse(4, 4, 0, 2, 2, 1, 1, 0));
        assertThat(formatador.extrairLinks(observacoes(ambos)).instagram())
            .contains(URI.create("https://www.instagram.com/ambos"));
        assertThat(formatador.extrairLinks(observacoes(ambos)).siteProprio())
            .contains(URI.create("https://ambos.example/"));
        assertThat(observacoes(ambos)).startsWith("Observação manual: ambos\n\n");
        assertThat(formatador.extrairLinks(observacoes(instagram)).instagram()).isPresent();
        assertThat(formatador.extrairLinks(observacoes(instagram)).siteProprio()).isEmpty();
        assertThat(formatador.extrairLinks(observacoes(site)).instagram()).isEmpty();
        assertThat(formatador.extrairLinks(observacoes(site)).siteProprio()).isPresent();
        assertThat(observacoes(vazio)).contains(FormatadorObservacoesPesquisa.SEM_INFORMACOES);
        assertThat(observacoes(fora)).isEqualTo("Fora da busca");

        Lead preservado = leadRepository.findById(ambos.getId()).orElseThrow();
        assertThat(preservado.getStatus()).isEqualTo(StatusFunil.CONTATADO);
        assertThat(preservado.getUltimoContatoEm()).isEqualTo(LocalDateTime.of(2026, 9, 10, 14, 30));
        assertThat(preservado.getCnpj()).isEqualTo("12345678000190");
        assertThat(preservado.getScore()).isEqualTo(84);
        assertThat(preservado.getTemperatura()).isEqualTo(Temperatura.QUENTE);
        BuscaLead vinculo = buscaLeadRepository
            .findByBuscaIdOrderByScoreNaBuscaDesc(alvo.getId()).getFirst();
        assertThat(vinculo.getScoreNaBusca()).isEqualTo(90);
        assertThat(vinculo.getTemperaturaNaBusca()).isEqualTo("QUENTE");
        assertThat(pesquisa.placeIds).hasSize(4).doesNotContain(fora.getGooglePlaceId());
    }

    @Test
    void deveFalharCom404LogicoParaBuscaInexistente() {
        BuscaInformacoesService service = new BuscaInformacoesService(
            persistencia,
            (lead, usarBrave) -> resultado(null, null),
            formatador
        );

        assertThatThrownBy(() -> service.buscarInformacoes(Long.MAX_VALUE))
            .isInstanceOf(BuscaNaoEncontradaException.class);
    }

    private Busca salvarBusca(String categorias) {
        Busca busca = new Busca();
        busca.setCategoriasBuscadas(categorias);
        busca.setTotalEncontrados(0);
        return buscaRepository.save(busca);
    }

    private Lead salvarLead(String observacoes) {
        Lead lead = new Lead();
        lead.setGooglePlaceId("info-013-" + UUID.randomUUID());
        lead.setNome("Padaria Integração " + UUID.randomUUID());
        lead.setCategoria(CategoriaNegocio.PADARIA);
        lead.setEnderecoFormatado("Rua Teste, 10, Vitória - ES");
        lead.setMunicipioNome("Vitória");
        lead.setUf("ES");
        lead.setStatus(StatusFunil.CONTATADO);
        lead.setUltimoContatoEm(LocalDateTime.of(2026, 9, 10, 14, 30));
        lead.setCnpj("12345678000190");
        lead.setScore(84);
        lead.setTemperatura(Temperatura.QUENTE);
        lead.setRatingGoogle(new BigDecimal("4.80"));
        lead.setObservacoes(observacoes);
        return leadRepository.save(lead);
    }

    private void vincular(Busca busca, Lead lead, int score) {
        BuscaLead vinculo = new BuscaLead();
        vinculo.setBusca(busca);
        vinculo.setLead(lead);
        vinculo.setScoreNaBusca(score);
        vinculo.setTemperaturaNaBusca(score >= 70 ? "QUENTE" : "MORNO");
        buscaLeadRepository.save(vinculo);
    }

    private String observacoes(Lead lead) {
        return leadRepository.findById(lead.getId()).orElseThrow().getObservacoes();
    }

    private PesquisaInformacoesWebResultado resultado(String instagram, String site) {
        return new PesquisaInformacoesWebResultado(uri(instagram), uri(site));
    }

    private Optional<URI> uri(String valor) {
        return valor == null ? Optional.empty() : Optional.of(URI.create(valor));
    }

    private static final class PesquisaSequencial implements PesquisaInformacoesGateway {

        private final ArrayDeque<PesquisaInformacoesWebResultado> resultados = new ArrayDeque<>();
        private final List<String> placeIds = new ArrayList<>();

        private PesquisaSequencial(PesquisaInformacoesWebResultado... resultados) {
            this.resultados.addAll(List.of(resultados));
        }

        @Override
        public PesquisaInformacoesWebResultado pesquisar(PesquisaLeadDados lead, boolean usarBrave) {
            placeIds.add(lead.googlePlaceId());
            return resultados.removeFirst();
        }
    }
}
