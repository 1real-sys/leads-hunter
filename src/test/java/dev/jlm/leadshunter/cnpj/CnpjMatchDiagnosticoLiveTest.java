package dev.jlm.leadshunter.cnpj;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.busca.BuscaInformacoesWorker;
import dev.jlm.leadshunter.lead.LeadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Explicit, read-only diagnostic. It never writes or calls an external API. */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.datasource.hikari.read-only=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@EnabledIfSystemProperty(named = "cnpjLive", matches = "true")
class CnpjMatchDiagnosticoLiveTest {

    @MockitoBean
    private BuscaInformacoesWorker buscaInformacoesWorker;

    @Autowired
    private CnpjMatchDiagnosticoService diagnosticoService;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private CnpjService cnpjService;

    @Test
    void deveAvaliarTodosOsLeadsSemCnpjSemEscrever() {
        assertThat(diagnosticoService.avaliarTodos()).isNotEmpty();
    }

    @Test
    void caso653DeveSerEnderecoUnicoNaBaseLocal() {
        var lead = leadRepository.findById(653L).orElseThrow(
            () -> new AssertionError("lead 653 não está no catálogo local do diagnóstico")
        );
        CnpjService.AvaliacaoMatch resultado = cnpjService.avaliarParaDiagnostico(lead);
        assertThat(resultado.classificacao()).isEqualTo(CnpjMatchClassificacao.ENDERECO_UNICO);
        assertThat(resultado.correspondencia()).isNotNull();
        assertThat(resultado.correspondencia().cnpj()).isEqualTo("51526147000150");
    }
}
