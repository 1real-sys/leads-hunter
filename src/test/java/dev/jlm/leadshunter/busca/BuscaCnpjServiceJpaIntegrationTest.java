package dev.jlm.leadshunter.busca;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.lead.Lead;
import dev.jlm.leadshunter.lead.LeadRepository;
import dev.jlm.leadshunter.lead.StatusFunil;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BuscaCnpjServiceJpaIntegrationTest {
    @Autowired private BuscaCnpjService service;
    @Autowired private BuscaService buscaService;
    @Autowired private BuscaRepository buscaRepository;
    @Autowired private BuscaLeadRepository vinculoRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void devePersistirCorrespondenciaLocalPreservarPreenchidoELimitarABusca() {
        // Município e empresa sintéticos exclusivos desta transação, sem depender da carga mensal.
        jdbc.update("""
            INSERT INTO cnpj_empresa VALUES ('99999997', 'Empresa Teste CNPJ07',
                'empresa teste cnpj07', '2026-09-08')
            """);
        jdbc.update("""
            INSERT INTO cnpj_estabelecimento
                (cnpj, cnpj_base, nome_fantasia_normalizado, logradouro_normalizado,
                 numero, bairro_normalizado, cep, municipio_codigo_ibge, uf, situacao_cadastral, data_base)
            VALUES ('99999997000100', '99999997', 'empresa teste cnpj07', 'rua teste',
                '7', '', '99999997', '9999997', 'ES', '02', '2026-09-08')
            """);
        Busca busca = new Busca();
        busca.setCategoriasBuscadas("OUTROS");
        buscaRepository.save(busca);
        Lead encontrado = criarLead();
        encontrado.setStatus(StatusFunil.CONTATADO);
        encontrado.setObservacoes("Preservar contato");
        Lead preenchido = criarLead();
        preenchido.setCnpj("12345678000190");
        preenchido.setRazaoSocial("Razão preservada");
        preenchido.setCnpjDataBase(LocalDate.of(2020, 1, 1));
        Lead semEndereco = criarLead();
        semEndereco.setLogradouro(null);
        Lead foraDaBusca = criarLead();
        for (Lead lead : new Lead[]{encontrado, preenchido, semEndereco, foraDaBusca}) {
            leadRepository.save(lead);
        }
        for (Lead lead : new Lead[]{encontrado, preenchido, semEndereco}) {
            BuscaLead vinculo = new BuscaLead();
            vinculo.setBusca(busca);
            vinculo.setLead(lead);
            vinculo.setScoreNaBusca(71);
            vinculoRepository.save(vinculo);
        }
        entityManager.flush();
        entityManager.clear();
        var atualizadoAntes = leadRepository.findById(preenchido.getId()).orElseThrow().getAtualizadoEm();

        assertThat(service.buscarCnpj(busca.getId())).isEqualTo(new BuscaCnpjResponse(3, 1, 1, 1));
        entityManager.flush();
        entityManager.clear();
        Lead persistido = leadRepository.findById(encontrado.getId()).orElseThrow();
        assertThat(persistido.getCnpj()).isEqualTo("99999997000100");
        assertThat(persistido.getRazaoSocial()).isEqualTo("Empresa Teste CNPJ07");
        assertThat(persistido.getCnpjCorrespondidoEm()).isNotNull();
        assertThat(persistido.getCnpjDataBase()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(persistido.getCnpjConfianca()).isNotNull();
        assertThat(persistido.getStatus()).isEqualTo(StatusFunil.CONTATADO);
        assertThat(persistido.getObservacoes()).isEqualTo("Preservar contato");
        Lead ignorado = leadRepository.findById(preenchido.getId()).orElseThrow();
        assertThat(ignorado.getCnpj()).isEqualTo("12345678000190");
        assertThat(ignorado.getRazaoSocial()).isEqualTo("Razão preservada");
        assertThat(ignorado.getCnpjDataBase()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(ignorado.getAtualizadoEm()).isEqualTo(atualizadoAntes);
        assertThat(leadRepository.findById(foraDaBusca.getId()).orElseThrow().getCnpj()).isNull();
        assertThat(leadRepository.findById(semEndereco.getId()).orElseThrow().getCnpj()).isNull();
        assertThat(buscaService.buscarHistoricoPorId(busca.getId()).leads())
            .anySatisfy(lead -> {
                assertThat(lead.id()).isEqualTo(encontrado.getId());
                assertThat(lead.cnpj()).isEqualTo("99999997000100");
                assertThat(lead.razaoSocial()).isEqualTo("Empresa Teste CNPJ07");
                assertThat(lead.scoreNaBusca()).isEqualTo(71);
            });
        assertThat(service.buscarCnpj(busca.getId())).isEqualTo(new BuscaCnpjResponse(3, 2, 0, 1));
    }

    private Lead criarLead() {
        Lead lead = new Lead();
        lead.setGooglePlaceId("cnpj07-" + UUID.randomUUID());
        lead.setNome("Empresa Teste CNPJ07");
        lead.setMunicipioCodigoIbge("9999997");
        lead.setCep("99999997");
        lead.setLogradouro("Rua Teste");
        lead.setNumero("7");
        return lead;
    }
}
