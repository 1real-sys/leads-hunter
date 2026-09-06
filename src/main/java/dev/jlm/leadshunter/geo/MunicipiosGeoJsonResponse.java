package dev.jlm.leadshunter.geo;

import java.math.BigDecimal;
import java.util.List;

public record MunicipiosGeoJsonResponse(
    String type,
    List<Feature> features
) {

    static MunicipiosGeoJsonResponse from(List<MunicipioDataset.Municipio> municipios) {
        return new MunicipiosGeoJsonResponse(
            "FeatureCollection",
            municipios.stream().map(Feature::from).toList()
        );
    }

    public record Feature(
        String type,
        Properties properties,
        Geometry geometry
    ) {

        private static Feature from(MunicipioDataset.Municipio municipio) {
            MunicipioInfo info = municipio.info();
            return new Feature(
                "Feature",
                new Properties(
                    info.codigoIbge(),
                    info.nome(),
                    info.uf(),
                    info.idhm(),
                    info.idhmReferencia()
                ),
                Geometry.from(municipio.geometria())
            );
        }
    }

    public record Properties(
        String codigoIbge,
        String nome,
        String uf,
        BigDecimal idhm,
        Short idhmReferencia
    ) {
    }

    public sealed interface Geometry permits PolygonGeometry, MultiPolygonGeometry {

        String type();

        static Geometry from(MunicipioDataset.Geometria geometria) {
            List<List<List<List<Double>>>> poligonos = geometria.poligonos().stream()
                .map(Geometry::coordenadas)
                .toList();
            return switch (geometria.tipo()) {
                case "Polygon" -> new PolygonGeometry(poligonos.getFirst());
                case "MultiPolygon" -> new MultiPolygonGeometry(poligonos);
                default -> throw new IllegalStateException("Tipo de geometria não suportado.");
            };
        }

        private static List<List<List<Double>>> coordenadas(
            MunicipioDataset.Poligono poligono
        ) {
            return poligono.aneis().stream()
                .map(Geometry::coordenadas)
                .toList();
        }

        private static List<List<Double>> coordenadas(MunicipioDataset.Anel anel) {
            double[] longitudes = anel.longitudes();
            double[] latitudes = anel.latitudes();
            return java.util.stream.IntStream.range(0, longitudes.length)
                .mapToObj(indice -> List.of(longitudes[indice], latitudes[indice]))
                .toList();
        }
    }

    public record PolygonGeometry(
        String type,
        List<List<List<Double>>> coordinates
    ) implements Geometry {

        private PolygonGeometry(List<List<List<Double>>> coordinates) {
            this("Polygon", coordinates);
        }
    }

    public record MultiPolygonGeometry(
        String type,
        List<List<List<List<Double>>>> coordinates
    ) implements Geometry {

        private MultiPolygonGeometry(List<List<List<List<Double>>>> coordinates) {
            this("MultiPolygon", coordinates);
        }
    }
}
