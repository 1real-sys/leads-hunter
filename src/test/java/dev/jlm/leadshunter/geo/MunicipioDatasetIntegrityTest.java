package dev.jlm.leadshunter.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

class MunicipioDatasetIntegrityTest {

    private static final String SHA256_ARTEFATO =
        "8c9ce54dff5eec54e7401ba2392e4305145edc4acb02c21388425393c6b56286";

    @Test
    void recursoDistribuidoDevePreservarBytesDoDatasetCongelado() throws Exception {
        byte[] conteudo = new ClassPathResource("geo/municipios-idhm.json")
            .getContentAsByteArray();

        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo)))
            .isEqualTo(SHA256_ARTEFATO);
        assertThat(new MunicipioDataset(new ObjectMapper(), new ByteArrayResource(conteudo)).municipios())
            .hasSize(5_570);
    }

    @Test
    void datasetComFimDeLinhaConvertidoParaCrlfDeveSerRejeitado() throws Exception {
        byte[] conteudo = new ClassPathResource("geo/municipios-idhm.json")
            .getContentAsByteArray();
        byte[] convertido = new String(conteudo, java.nio.charset.StandardCharsets.UTF_8)
            .replace("\n", "\r\n")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new MunicipioDataset(
            new ObjectMapper(), new ByteArrayResource(convertido)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Checksum inválido");
    }
}
