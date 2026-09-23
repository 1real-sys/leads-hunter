package dev.jlm.leadshunter.cnpj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jlm.leadshunter.lead.Lead;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

@ExtendWith(MockitoExtension.class)
class CnpjServiceTest {

    @Mock
    private CnpjEstabelecimentoRepository repository;

    @Test
    void deveResolverUnidadeDeVitoriaPeloMunicipioEEndereco() {
        Lead lead = criarLead(
            "Coco Bambu Vitória", "3205309", "29055-620",
            "R. João da Cruz", "10", "Praia do Canto"
        );
        when(repository.findByMunicipioCodigoIbgeAndSituacaoCadastralAndCepAndNumeroNormalizado(
            eq("3205309"), eq("02"), eq("29055620"), eq("10"), any(PageRequest.class)
        )).thenReturn(slice(candidato(
            "43869215000156", "CB VITORIA COMERCIO DE ALIMENTOS LTDA", null,
            "RUA JOAO DA CRUZ", "10", "PRAIA DO CANTO", "29055620", "3205309"
        )));

        assertThat(new CnpjService(repository).corresponder(lead))
            .contains(new CnpjService.Correspondencia(
                "43869215000156",
                "CB VITORIA COMERCIO DE ALIMENTOS LTDA",
                LocalDate.of(2026, 9, 8),
                new BigDecimal("1.0000")
            ));
    }

    @Test
    void deveResolverCnpjDistintoDaMesmaRedeEmVilaVelha() {
        Lead lead = criarLead(
            "Coco Bambu Vila Velha", "3205200", "29101-950",
            "Av. Doutor Olívio Lira", "353", "Praia da Costa"
        );
        when(repository.findByMunicipioCodigoIbgeAndSituacaoCadastralAndCepAndNumeroNormalizado(
            eq("3205200"), eq("02"), eq("29101950"), eq("353"), any(PageRequest.class)
        )).thenReturn(slice(candidato(
            "23681920000118", "CB VILA VELHA COMERCIO DE ALIMENTOS LTDA",
            "COCO BAMBU VILA VELHA", "AVENIDA DOUTOR OLIVIO LIRA", "353",
            "PRAIA DA COSTA", "29101950", "3205200"
        )));

        assertThat(new CnpjService(repository).corresponder(lead))
            .get()
            .extracting(CnpjService.Correspondencia::cnpj)
            .isEqualTo("23681920000118");
    }

    @Test
    void deveRejeitarAmbiguidadeMesmoComDoisCandidatosAcimaDoLimiar() {
        Lead lead = criarLead(
            "Padaria Central", "4106902", "80000-000",
            "Rua Central", "100", "Centro"
        );
        CnpjEstabelecimento primeiro = candidato(
            "11222333000181", "PADARIA CENTRAL LTDA", "PADARIA CENTRAL",
            "RUA CENTRAL", "100", "CENTRO", "80000000", "4106902"
        );
        CnpjEstabelecimento segundo = candidato(
            "11444777000161", "PADARIA CENTRAL DO PARANA LTDA", "PADARIA CENTRAL",
            "RUA CENTRAL", "100", "CENTRO", "80000000", "4106902"
        );
        when(repository.findByMunicipioCodigoIbgeAndSituacaoCadastralAndCepAndNumeroNormalizado(
            eq("4106902"), eq("02"), eq("80000000"), eq("100"), any(PageRequest.class)
        )).thenReturn(slice(primeiro, segundo));

        assertThat(new CnpjService(repository).corresponder(lead)).isEmpty();
    }

    @Test
    void deveRejeitarCandidatoAbaixoDoLimiar() {
        Lead lead = criarLead(
            "Farmácia Saúde", "4106902", "80000-000",
            "Rua Central", "100", "Centro"
        );
        when(repository.findByMunicipioCodigoIbgeAndSituacaoCadastralAndCepAndNumeroNormalizado(
            eq("4106902"), eq("02"), eq("80000000"), eq("100"), any(PageRequest.class)
        )).thenReturn(slice(candidato(
            "11222333000181", "PADARIA CENTRAL LTDA", "PADARIA CENTRAL",
            "RUA CENTRAL", "100", "CENTRO", "80000000", "4106902"
        )));

        assertThat(new CnpjService(repository).corresponder(lead)).isEmpty();
    }

    @Test
    void deveUsarMunicipioLogradouroENumeroQuandoCepEstiverAusente() {
        Lead lead = criarLead(
            "Coco Bambu Vitória", "3205309", null,
            "Rua João da Cruz", "10", "Praia do Canto"
        );
        when(repository.findByMunicipioCodigoIbgeAndSituacaoCadastralAndNumeroNormalizado(
            eq("3205309"), eq("02"), eq("10"), any(PageRequest.class)
        )).thenReturn(slice(candidato(
            "43869215000156", "CB VITORIA COMERCIO DE ALIMENTOS LTDA", null,
            "RUA JOAO DA CRUZ", "10", "PRAIA DO CANTO", "29055620", "3205309"
        )));

        assertThat(new CnpjService(repository).corresponder(lead)).isPresent();
        verify(repository, never()).findByMunicipioCodigoIbgeAndSituacaoCadastralAndCep(
            any(), any(), any(), any()
        );
    }

    @Test
    void deveManterSemCnpjQuandoEnderecoOuCandidatoNaoExistir() {
        Lead semEndereco = criarLead(
            "Coco Bambu Vitória", "3205309", "29055-620",
            null, null, null
        );

        assertThat(new CnpjService(repository).corresponder(semEndereco)).isEmpty();
        verifyNoInteractions(repository);

        Lead semCandidato = criarLead(
            "Coco Bambu Vitória", "3205309", "29055-620",
            "Rua João da Cruz", "10", "Praia do Canto"
        );
        when(repository.findByMunicipioCodigoIbgeAndSituacaoCadastralAndCepAndNumeroNormalizado(
            eq("3205309"), eq("02"), eq("29055620"), eq("10"), any(PageRequest.class)
        )).thenReturn(slice());
        assertThat(new CnpjService(repository).corresponder(semCandidato)).isEmpty();
    }

    @Test
    void deveRejeitarConsultaTruncadaParaNaoAssumirUnicidade() {
        Lead lead = criarLead(
            "Padaria Central", "4106902", "80000-000",
            "Rua Central", "100", "Centro"
        );
        when(repository.findByMunicipioCodigoIbgeAndSituacaoCadastralAndCepAndNumeroNormalizado(
            eq("4106902"), eq("02"), eq("80000000"), eq("100"), any(PageRequest.class)
        )).thenReturn(new SliceImpl<>(List.of(candidato(
            "11222333000181", "PADARIA CENTRAL LTDA", "PADARIA CENTRAL",
            "RUA CENTRAL", "100", "CENTRO", "80000000", "4106902"
        )), PageRequest.of(0, 200), true));

        assertThat(new CnpjService(repository).corresponder(lead)).isEmpty();
    }

    @Test
    void deveConsultarCompetenciaMaisRecenteDoMunicipioAtivo() {
        when(repository.findDataBaseAtual("3205309", "02"))
            .thenReturn(java.util.Optional.of(LocalDate.of(2026, 9, 8)));

        CnpjService service = new CnpjService(repository);

        assertThat(service.buscarDataBaseAtual("3205309"))
            .contains(LocalDate.of(2026, 9, 8));
        assertThat(service.buscarDataBaseAtual("codigo-invalido")).isEmpty();
        verify(repository).findDataBaseAtual("3205309", "02");
    }

    @Test
    void deveResolverCaso653PorEnderecoExatoMesmoComNomeDivergente() {
        Lead lead = criarLead(
            "Farmácia São Miguel", "3204708", "29780-000",
            "Rua Padre Simão Civalero", "48", "Centro"
        );
        CnpjEstabelecimento candidato = candidato(
            "51526147000150", "DROGARIA DE SOUSA ALVES LTDA", null,
            "RUA PADRE SIMAO CIVALERO", "48", "CENTRO", "29780000", "3204708"
        );
        when(repository.findByMunicipioCodigoIbgeAndSituacaoCadastralAndCepAndNumeroNormalizado(
            eq("3204708"), eq("02"), eq("29780000"), eq("48"), any(PageRequest.class)
        )).thenReturn(slice(candidato));

        CnpjService service = new CnpjService(
            repository,
            CnpjMatchPolicy.habilitadaParaMunicipios("3204708")
        );

        assertThat(service.corresponder(lead)).get()
            .satisfies(correspondencia -> {
                assertThat(correspondencia.cnpj()).isEqualTo("51526147000150");
                assertThat(correspondencia.origem()).isEqualTo(CnpjOrigem.ENDERECO_EXATO);
                assertThat(correspondencia.confianca()).isLessThan(new BigDecimal("0.8200"));
            });
        assertThat(service.avaliarParaDiagnostico(lead).classificacao())
            .isEqualTo(CnpjMatchClassificacao.ENDERECO_UNICO);
    }

    @Test
    void deveDesempatarEnderecoExatoSomenteComUmNomeAcimaDoLimiar() {
        Lead lead = criarLead(
            "Padaria Central", "3205309", "29055-620",
            "Rua João da Cruz", "10", "Praia do Canto"
        );
        CnpjEstabelecimento aprovado = candidato(
            "43869215000156", "CB VITORIA COMERCIO DE ALIMENTOS LTDA", "PADARIA CENTRAL",
            "RUA JOAO DA CRUZ", "10", "PRAIA DO CANTO", "29055620", "3205309"
        );
        CnpjEstabelecimento rejeitado = candidato(
            "23681920000118", "OUTRA EMPRESA LTDA", "OUTRA MARCA",
            "RUA JOAO DA CRUZ", "10", "PRAIA DO CANTO", "29055620", "3205309"
        );
        when(repository.findByMunicipioCodigoIbgeAndSituacaoCadastralAndCepAndNumeroNormalizado(
            eq("3205309"), eq("02"), eq("29055620"), eq("10"), any(PageRequest.class)
        )).thenReturn(slice(aprovado, rejeitado));

        CnpjService.AvaliacaoMatch resultado = new CnpjService(
            repository,
            CnpjMatchPolicy.habilitadaParaMunicipios("3205309")
        ).avaliarParaDiagnostico(lead);

        assertThat(resultado.classificacao())
            .isEqualTo(CnpjMatchClassificacao.ENDERECO_DESEMPATADO_POR_NOME);
        assertThat(resultado.correspondencia()).extracting(CnpjService.Correspondencia::cnpj)
            .isEqualTo("43869215000156");
        assertThat(resultado.gapNome()).isNotNull();
    }

    @Test
    void deveRecusarDoisNomesAcimaDoLimiarNoMesmoEndereco() {
        Lead lead = criarLead(
            "Padaria Central", "3205309", "29055-620",
            "Rua João da Cruz", "10", "Praia do Canto"
        );
        CnpjEstabelecimento primeiro = candidato(
            "43869215000156", "PADARIA CENTRAL LTDA", "PADARIA CENTRAL",
            "RUA JOAO DA CRUZ", "10", "PRAIA DO CANTO", "29055620", "3205309"
        );
        CnpjEstabelecimento segundo = candidato(
            "23681920000118", "PADARIA CENTRAL FILIAL LTDA", "PADARIA CENTRAL",
            "RUA JOAO DA CRUZ", "10", "PRAIA DO CANTO", "29055620", "3205309"
        );
        when(repository.findByMunicipioCodigoIbgeAndSituacaoCadastralAndCepAndNumeroNormalizado(
            eq("3205309"), eq("02"), eq("29055620"), eq("10"), any(PageRequest.class)
        )).thenReturn(slice(primeiro, segundo));

        assertThat(new CnpjService(
            repository,
            CnpjMatchPolicy.habilitadaParaMunicipios("3205309")
        ).corresponder(lead)).isEmpty();
    }

    @Test
    void deveRecusarConsultaExataTruncadaMesmoComUmItemNaPagina() {
        Lead lead = criarLead(
            "Padaria Central", "3205309", "29055-620",
            "Rua João da Cruz", "10", "Praia do Canto"
        );
        CnpjEstabelecimento candidato = candidato(
            "43869215000156", "PADARIA CENTRAL LTDA", "PADARIA CENTRAL",
            "RUA JOAO DA CRUZ", "10", "PRAIA DO CANTO", "29055620", "3205309"
        );
        when(repository.findByMunicipioCodigoIbgeAndSituacaoCadastralAndCepAndNumeroNormalizado(
            eq("3205309"), eq("02"), eq("29055620"), eq("10"), any(PageRequest.class)
        )).thenReturn(new org.springframework.data.domain.SliceImpl<>(
            List.of(candidato), PageRequest.of(0, 200), true
        ));

        CnpjService.AvaliacaoMatch resultado = new CnpjService(
            repository,
            CnpjMatchPolicy.habilitadaParaMunicipios("3205309")
        ).avaliarParaDiagnostico(lead);

        assertThat(resultado.classificacao()).isEqualTo(CnpjMatchClassificacao.CONSULTA_TRUNCADA);
        assertThat(resultado.correspondencia()).isNull();
    }

    @Test
    void deveAplicarZerosCanonicosTambemNoCaminhoLegado() {
        Lead lead = criarLead(
            "Coco Bambu Vitória", "3205309", "29055-620",
            "Rua João da Cruz", "48", "Praia do Canto"
        );
        when(repository.findByMunicipioCodigoIbgeAndSituacaoCadastralAndCepAndNumeroNormalizado(
            eq("3205309"), eq("02"), eq("29055620"), eq("48"), any(PageRequest.class)
        )).thenReturn(slice(candidato(
            "43869215000156", "CB VITORIA COMERCIO DE ALIMENTOS LTDA", null,
            "RUA JOAO DA CRUZ", "048", "PRAIA DO CANTO", "29055620", "3205309"
        )));

        CnpjService service = new CnpjService(repository);

        assertThat(service.corresponder(lead)).isPresent();
        assertThat(service.avaliarParaDiagnostico(lead).normalizacaoNumeroAlterada()).isTrue();
    }

    @Test
    void deveClassificarNumerosDescartadosDaBaseParaAuditoria() {
        when(repository.listarNumerosDescartados()).thenReturn(List.of(
            numeroDescartado("S/N", 20),
            numeroDescartado("O", 30)
        ));

        assertThat(new CnpjService(repository).listarNumerosDescartados())
            .containsExactly(
                new CnpjNumeroNormalizer.NumeroDescartado(
                    "S/N",
                    "SN",
                    CnpjNumeroNormalizer.Classificacao.SENTINELA_SEM_NUMERO,
                    20
                ),
                new CnpjNumeroNormalizer.NumeroDescartado(
                    "O",
                    "O",
                    CnpjNumeroNormalizer.Classificacao.NUMERO_DESCONHECIDO,
                    30
                )
            );
    }

    private Lead criarLead(
        String nome,
        String municipio,
        String cep,
        String logradouro,
        String numero,
        String bairro
    ) {
        Lead lead = new Lead();
        lead.setNome(nome);
        lead.setMunicipioCodigoIbge(municipio);
        lead.setCep(cep);
        lead.setLogradouro(logradouro);
        lead.setNumero(numero);
        lead.setBairro(bairro);
        return lead;
    }

    private CnpjEstabelecimento candidato(
        String cnpj,
        String razaoSocial,
        String fantasia,
        String logradouro,
        String numero,
        String bairro,
        String cep,
        String municipio
    ) {
        CnpjEmpresa empresa = new CnpjEmpresa();
        empresa.setCnpjBase(cnpj.substring(0, 8));
        empresa.setRazaoSocial(razaoSocial);
        empresa.setRazaoSocialNormalizada(CnpjService.normalizarTexto(razaoSocial));
        empresa.setDataBase(LocalDate.of(2026, 9, 8));

        CnpjEstabelecimento candidato = new CnpjEstabelecimento();
        candidato.setCnpj(cnpj);
        candidato.setCnpjBase(cnpj.substring(0, 8));
        candidato.setEmpresa(empresa);
        candidato.setNomeFantasia(fantasia);
        candidato.setNomeFantasiaNormalizado(CnpjService.normalizarTexto(fantasia));
        candidato.setLogradouro(logradouro);
        candidato.setLogradouroNormalizado(CnpjService.normalizarTexto(logradouro));
        candidato.setNumero(numero);
        candidato.setNumeroNormalizado(CnpjNumeroNormalizer.normalizar(numero));
        candidato.setBairro(bairro);
        candidato.setBairroNormalizado(CnpjService.normalizarTexto(bairro));
        candidato.setCep(cep);
        candidato.setMunicipioCodigoIbge(municipio);
        candidato.setSituacaoCadastral("02");
        candidato.setDataBase(LocalDate.of(2026, 9, 8));
        return candidato;
    }

    private SliceImpl<CnpjEstabelecimento> slice(CnpjEstabelecimento... candidatos) {
        return new SliceImpl<>(List.of(candidatos), PageRequest.of(0, 200), false);
    }

    private CnpjEstabelecimentoRepository.NumeroDescartadoContagem numeroDescartado(
        String numero,
        long quantidade
    ) {
        return new CnpjEstabelecimentoRepository.NumeroDescartadoContagem() {
            @Override
            public String getNumero() {
                return numero;
            }

            @Override
            public long getQuantidade() {
                return quantidade;
            }
        };
    }
}
