package dev.jlm.leadshunter.geo;

import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MunicipioService {

    private static final double EPSILON = 1e-10;

    private final MunicipioDataset dataset;

    public Optional<MunicipioInfo> localizar(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return Optional.empty();
        }
        double latitudeDouble = latitude.doubleValue();
        double longitudeDouble = longitude.doubleValue();
        if (
            !Double.isFinite(latitudeDouble)
                || !Double.isFinite(longitudeDouble)
                || latitudeDouble < -90
                || latitudeDouble > 90
                || longitudeDouble < -180
                || longitudeDouble > 180
        ) {
            return Optional.empty();
        }

        return dataset.municipios().stream()
            .filter(municipio -> municipio.bbox().contem(longitudeDouble, latitudeDouble))
            .filter(municipio -> contem(municipio.geometria(), longitudeDouble, latitudeDouble))
            .map(MunicipioDataset.Municipio::info)
            .findFirst();
    }

    private boolean contem(
        MunicipioDataset.Geometria geometria,
        double longitude,
        double latitude
    ) {
        return geometria.poligonos().stream()
            .anyMatch(poligono -> contem(poligono, longitude, latitude));
    }

    private boolean contem(
        MunicipioDataset.Poligono poligono,
        double longitude,
        double latitude
    ) {
        if (poligono.aneis().isEmpty()) {
            return false;
        }
        if (!contem(poligono.aneis().getFirst(), longitude, latitude)) {
            return false;
        }
        return poligono.aneis().stream()
            .skip(1)
            .noneMatch(anel -> contem(anel, longitude, latitude));
    }

    private boolean contem(
        MunicipioDataset.Anel anel,
        double longitude,
        double latitude
    ) {
        boolean dentro = false;
        double[] longitudes = anel.longitudes();
        double[] latitudes = anel.latitudes();
        int anterior = longitudes.length - 1;

        for (int atual = 0; atual < longitudes.length; atual++) {
            if (
                pontoNoSegmento(
                    longitude,
                    latitude,
                    longitudes[anterior],
                    latitudes[anterior],
                    longitudes[atual],
                    latitudes[atual]
                )
            ) {
                return true;
            }
            boolean cruzaLatitude = (latitudes[atual] > latitude)
                != (latitudes[anterior] > latitude);
            if (cruzaLatitude) {
                double longitudeIntersecao = (longitudes[anterior] - longitudes[atual])
                    * (latitude - latitudes[atual])
                    / (latitudes[anterior] - latitudes[atual])
                    + longitudes[atual];
                if (longitude < longitudeIntersecao) {
                    dentro = !dentro;
                }
            }
            anterior = atual;
        }
        return dentro;
    }

    private boolean pontoNoSegmento(
        double longitude,
        double latitude,
        double longitudeInicio,
        double latitudeInicio,
        double longitudeFim,
        double latitudeFim
    ) {
        double produto = (longitude - longitudeInicio) * (latitudeFim - latitudeInicio)
            - (latitude - latitudeInicio) * (longitudeFim - longitudeInicio);
        if (Math.abs(produto) > EPSILON) {
            return false;
        }
        return longitude >= Math.min(longitudeInicio, longitudeFim) - EPSILON
            && longitude <= Math.max(longitudeInicio, longitudeFim) + EPSILON
            && latitude >= Math.min(latitudeInicio, latitudeFim) - EPSILON
            && latitude <= Math.max(latitudeInicio, latitudeFim) + EPSILON;
    }
}
