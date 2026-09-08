package dev.jlm.leadshunter.cnpj;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@Sql("/cnpj/fixtures.sql")
class CnpjRepositoryTest {

    @Autowired
    private CnpjEmpresaRepository empresaRepository;

    @Autowired
    private CnpjEstabelecimentoRepository estabelecimentoRepository;

    @Test
    void deveCarregarSubsetInicialDosTresMunicipios() {
        assertThat(empresaRepository.count()).isGreaterThanOrEqualTo(3);

        Slice<CnpjEstabelecimento> vitoria = estabelecimentoRepository
            .findByMunicipioCodigoIbgeAndSituacaoCadastralAndCep(
                "3205309",
                "02",
                "29055620",
                PageRequest.of(0, 200)
            );
        Slice<CnpjEstabelecimento> vilaVelha = estabelecimentoRepository
            .findByMunicipioCodigoIbgeAndSituacaoCadastralAndCep(
                "3205200",
                "02",
                "29101950",
                PageRequest.of(0, 200)
            );
        Slice<CnpjEstabelecimento> curitiba = estabelecimentoRepository
            .findByMunicipioCodigoIbgeAndSituacaoCadastralAndCep(
                "4106902",
                "02",
                "80420063",
                PageRequest.of(0, 200)
            );

        assertThat(vitoria.getContent()).singleElement().satisfies(item -> {
            assertThat(item.getCnpj()).isEqualTo("43869215000156");
            assertThat(item.getLogradouroNormalizado()).isEqualTo("rua joao da cruz");
            assertThat(item.getEmpresa().getRazaoSocial())
                .isEqualTo("CB VITORIA COMERCIO DE ALIMENTOS LTDA");
            assertThat(item.getDataBase()).isEqualTo(LocalDate.of(2026, 9, 8));
        });
        assertThat(vilaVelha.getContent()).singleElement()
            .extracting(CnpjEstabelecimento::getCnpj)
            .isEqualTo("23681920000118");
        assertThat(curitiba.getContent()).singleElement()
            .extracting(CnpjEstabelecimento::getCnpj)
            .isEqualTo("23502037000113");
        assertThat(estabelecimentoRepository.findDataBaseAtual("3205309", "02"))
            .contains(LocalDate.of(2026, 9, 8));
    }
}
