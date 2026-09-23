package dev.jlm.leadshunter.cnpj;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CnpjMatchEvaluatorTest {

    private final CnpjMatchEvaluator evaluator = new CnpjMatchEvaluator();

    @Test
    void deveAprovarEnderecoExatoSemExigirNome() {
        CnpjMatchEvaluator.Resultado resultado = evaluator.avaliarEnderecoExato(
            lead("Farmácia São Miguel", "48"),
            candidato("DROGARIA DE SOUSA ALVES LTDA", "048")
        );

        assertThat(resultado.aprovado()).isTrue();
        assertThat(resultado.enderecoExato()).isTrue();
        assertThat(resultado.pontuacao()).isLessThan(CnpjMatchEvaluator.LIMIAR_COM_CEP);
        assertThat(resultado.motivo()).isEqualTo(CnpjMatchEvaluator.Motivo.ENDERECO_EXATO);
        assertThat(evaluator.avaliarEnderecoExato(
            lead(null, "48"),
            candidato("DROGARIA DE SOUSA ALVES LTDA", "048")
        ).aprovado()).isTrue();
    }

    @Test
    void deveDistinguirNormalizacaoNovaDaLegada() {
        CnpjMatchEvaluator.DadosLead lead = lead("Coco Bambu", "48");
        CnpjMatchEvaluator.DadosCandidato candidato = candidato("Coco Bambu", "048");

        assertThat(evaluator.avaliarLegado(lead, candidato, true).aprovado()).isFalse();
        assertThat(evaluator.avaliarNovoLegado(lead, candidato, true).aprovado()).isTrue();
    }

    @Test
    void deveRecusarEnderecoDeOutroMunicipioOuSituacao() {
        CnpjMatchEvaluator.DadosLead lead = lead("Padaria Central", "100");

        assertThat(evaluator.avaliarEnderecoExato(
            lead,
            candidato("Padaria Central", "100", "4106903", "02")
        ).motivo()).isEqualTo(CnpjMatchEvaluator.Motivo.MUNICIPIO_DIVERGENTE);
        assertThat(evaluator.avaliarEnderecoExato(
            lead,
            candidato("Padaria Central", "100", "4106902", "01")
        ).motivo()).isEqualTo(CnpjMatchEvaluator.Motivo.SITUACAO_INATIVA);
    }

    private CnpjMatchEvaluator.DadosLead lead(String nome, String numero) {
        return new CnpjMatchEvaluator.DadosLead(
            nome,
            "Rua Central",
            numero,
            "Centro",
            "80000-000",
            "4106902",
            "PR"
        );
    }

    private CnpjMatchEvaluator.DadosCandidato candidato(String razaoSocial, String numero) {
        return candidato(razaoSocial, numero, "4106902", "02");
    }

    private CnpjMatchEvaluator.DadosCandidato candidato(
        String razaoSocial,
        String numero,
        String municipio,
        String situacao
    ) {
        return new CnpjMatchEvaluator.DadosCandidato(
            "12345678000190",
            razaoSocial,
            null,
            "rua central",
            numero,
            CnpjNumeroNormalizer.normalizar(numero),
            "centro",
            "80000000",
            municipio,
            situacao
        );
    }
}
