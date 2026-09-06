package dev.jlm.leadshunter.geo;

final class MunicipioBboxParser {

    private static final int TAMANHO_MAXIMO = 160;

    private MunicipioBboxParser() {
    }

    static MunicipioDataset.Envelope parse(String bbox) {
        if (bbox == null || bbox.isBlank() || bbox.length() > TAMANHO_MAXIMO) {
            throw new BboxInvalidoException();
        }

        String[] partes = bbox.split(",", -1);
        if (partes.length != 4) {
            throw new BboxInvalidoException();
        }

        double minLongitude = numeroFinito(partes[0]);
        double minLatitude = numeroFinito(partes[1]);
        double maxLongitude = numeroFinito(partes[2]);
        double maxLatitude = numeroFinito(partes[3]);
        if (
            minLongitude < -180
                || maxLongitude > 180
                || minLatitude < -90
                || maxLatitude > 90
                || minLongitude >= maxLongitude
                || minLatitude >= maxLatitude
        ) {
            throw new BboxInvalidoException();
        }

        return new MunicipioDataset.Envelope(
            minLongitude,
            minLatitude,
            maxLongitude,
            maxLatitude
        );
    }

    private static double numeroFinito(String parte) {
        try {
            double numero = Double.parseDouble(parte.trim());
            if (Double.isFinite(numero)) {
                return numero;
            }
        } catch (NumberFormatException ignored) {
            // A resposta pública é uniforme para qualquer componente inválido do bbox.
        }
        throw new BboxInvalidoException();
    }
}
