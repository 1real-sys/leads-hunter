package dev.jlm.leadshunter.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MunicipioBboxParserTest {

    @Test
    void deveAceitarEspacosECoordenadasNosLimitesGeograficos() {
        MunicipioDataset.Envelope envelope = MunicipioBboxParser.parse(
            " -180 , -90 , 180 , 90 "
        );

        assertThat(envelope.minLongitude()).isEqualTo(-180);
        assertThat(envelope.minLatitude()).isEqualTo(-90);
        assertThat(envelope.maxLongitude()).isEqualTo(180);
        assertThat(envelope.maxLatitude()).isEqualTo(90);
    }

    @Test
    void deveRejeitarFormatosValoresEOrdensInvalidas() {
        for (String bbox : new String[] {
            "",
            "-40,-20,-39",
            "-40,-20,-39,-19,0",
            "-40,-20,-39,-19,",
            "-40,-20,-39,",
            "texto,-20,-39,-19",
            "NaN,-20,-39,-19",
            "Infinity,-20,-39,-19",
            "-Infinity,-20,-39,-19",
            "-39,-20,-40,-19",
            "-40,-19,-39,-20",
            "-40,-20,-40,-19",
            "-40,-20,-39,-20",
            "-181,-20,-39,-19",
            "-40,-91,-39,-19",
            "-40,-20,181,-19",
            "-40,-20,-39,91"
        }) {
            assertThatThrownBy(() -> MunicipioBboxParser.parse(bbox))
                .as("bbox inválido: %s", bbox)
                .isInstanceOf(BboxInvalidoException.class);
        }

        assertThatThrownBy(() -> MunicipioBboxParser.parse(null))
            .isInstanceOf(BboxInvalidoException.class);
        assertThatThrownBy(() -> MunicipioBboxParser.parse("1".repeat(161)))
            .isInstanceOf(BboxInvalidoException.class);
    }
}
