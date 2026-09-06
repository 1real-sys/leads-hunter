package dev.jlm.leadshunter.geo;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
final class MunicipioDataset {

    private static final int QUANTIDADE_MUNICIPIOS = 5_570;
    private static final int TAMANHO_MAXIMO_BYTES = 6 * 1024 * 1024;
    private static final String SHA256_ESPERADO =
        "8c9ce54dff5eec54e7401ba2392e4305145edc4acb02c21388425393c6b56286";

    private final List<Municipio> municipios;

    MunicipioDataset(
        ObjectMapper objectMapper,
        @Value("classpath:geo/municipios-idhm.json") Resource recurso
    ) {
        this.municipios = carregar(objectMapper, recurso);
    }

    List<Municipio> municipios() {
        return municipios;
    }

    private List<Municipio> carregar(ObjectMapper objectMapper, Resource recurso) {
        byte[] conteudo = lerComLimite(recurso);
        validarChecksum(conteudo);

        try {
            JsonNode raiz = objectMapper.readTree(conteudo);
            validarCabecalho(raiz);
            return lerMunicipios(raiz.get("municipios"));
        } catch (RuntimeException erro) {
            throw new IllegalStateException("Dataset municipal de IDHM inválido.", erro);
        }
    }

    private byte[] lerComLimite(Resource recurso) {
        try (InputStream input = recurso.getInputStream()) {
            byte[] conteudo = input.readNBytes(TAMANHO_MAXIMO_BYTES + 1);
            if (conteudo.length > TAMANHO_MAXIMO_BYTES) {
                throw new IllegalStateException("Dataset municipal de IDHM excede o limite permitido.");
            }
            return conteudo;
        } catch (IOException erro) {
            throw new IllegalStateException("Não foi possível ler o dataset municipal de IDHM.", erro);
        }
    }

    private void validarChecksum(byte[] conteudo) {
        try {
            String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(conteudo)
            );
            if (!SHA256_ESPERADO.equals(hash)) {
                throw new IllegalStateException("Checksum inválido para o dataset municipal de IDHM.");
            }
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 indisponível no ambiente.", erro);
        }
    }

    private void validarCabecalho(JsonNode raiz) {
        if (raiz == null || !raiz.isObject()) {
            throw new IllegalArgumentException("Raiz JSON ausente ou inválida.");
        }
        JsonNode metadata = raiz.get("metadata");
        JsonNode lista = raiz.get("municipios");
        if (
            metadata == null
                || !metadata.isObject()
                || inteiroObrigatorio(metadata, "schemaVersion") != 1
                || inteiroObrigatorio(metadata, "municipios") != QUANTIDADE_MUNICIPIOS
                || lista == null
                || !lista.isArray()
                || lista.size() != QUANTIDADE_MUNICIPIOS
        ) {
            throw new IllegalArgumentException("Metadados ou quantidade municipal inválidos.");
        }
    }

    private List<Municipio> lerMunicipios(JsonNode registros) {
        List<Municipio> resultado = new ArrayList<>(QUANTIDADE_MUNICIPIOS);
        Set<String> codigos = new HashSet<>(QUANTIDADE_MUNICIPIOS);

        for (JsonNode registro : registros) {
            String codigoIbge = textoObrigatorio(registro, "codigoIbge");
            if (!codigoIbge.matches("\\d{7}") || !codigos.add(codigoIbge)) {
                throw new IllegalArgumentException("Código IBGE inválido ou duplicado.");
            }
            String uf = textoObrigatorio(registro, "uf");
            if (!uf.matches("[A-Z]{2}")) {
                throw new IllegalArgumentException("UF inválida no dataset municipal.");
            }

            BigDecimal idhm = decimalOpcional(registro, "idhm");
            if (idhm != null && (idhm.signum() < 0 || idhm.compareTo(BigDecimal.ONE) > 0)) {
                throw new IllegalArgumentException("IDHM fora do intervalo permitido.");
            }

            short referencia = shortObrigatorio(registro, "idhmReferencia");
            if (referencia != 2010) {
                throw new IllegalArgumentException("Referência de IDHM diferente de 2010.");
            }

            MunicipioInfo info = new MunicipioInfo(
                codigoIbge,
                textoObrigatorio(registro, "nome"),
                uf,
                idhm,
                referencia
            );
            resultado.add(new Municipio(
                info,
                lerEnvelope(registro.get("bbox")),
                lerGeometria(registro.get("geometry"))
            ));
        }
        return List.copyOf(resultado);
    }

    private Envelope lerEnvelope(JsonNode bbox) {
        if (bbox == null || !bbox.isArray() || bbox.size() != 4) {
            throw new IllegalArgumentException("Bbox municipal inválido.");
        }
        double minLongitude = numeroFinito(bbox.get(0));
        double minLatitude = numeroFinito(bbox.get(1));
        double maxLongitude = numeroFinito(bbox.get(2));
        double maxLatitude = numeroFinito(bbox.get(3));
        if (
            minLongitude < -180
                || maxLongitude > 180
                || minLatitude < -90
                || maxLatitude > 90
                || minLongitude > maxLongitude
                || minLatitude > maxLatitude
        ) {
            throw new IllegalArgumentException("Bbox municipal fora dos limites geográficos.");
        }
        return new Envelope(minLongitude, minLatitude, maxLongitude, maxLatitude);
    }

    private Geometria lerGeometria(JsonNode geometria) {
        if (geometria == null || !geometria.isObject()) {
            throw new IllegalArgumentException("Geometria municipal ausente.");
        }
        String tipo = textoObrigatorio(geometria, "type");
        JsonNode coordenadas = geometria.get("coordinates");
        if (coordenadas == null || !coordenadas.isArray()) {
            throw new IllegalArgumentException("Coordenadas municipais ausentes.");
        }

        List<Poligono> poligonos = new ArrayList<>();
        if ("Polygon".equals(tipo)) {
            poligonos.add(lerPoligono(coordenadas));
        } else if ("MultiPolygon".equals(tipo)) {
            for (JsonNode poligono : coordenadas) {
                poligonos.add(lerPoligono(poligono));
            }
        } else {
            throw new IllegalArgumentException("Tipo de geometria municipal não suportado.");
        }
        if (poligonos.isEmpty()) {
            throw new IllegalArgumentException("Geometria municipal vazia.");
        }
        return new Geometria(tipo, List.copyOf(poligonos));
    }

    private Poligono lerPoligono(JsonNode coordenadas) {
        if (coordenadas == null || !coordenadas.isArray() || coordenadas.size() == 0) {
            throw new IllegalArgumentException("Polígono municipal vazio.");
        }
        List<Anel> aneis = new ArrayList<>();
        for (JsonNode anel : coordenadas) {
            aneis.add(lerAnel(anel));
        }
        return new Poligono(List.copyOf(aneis));
    }

    private Anel lerAnel(JsonNode coordenadas) {
        if (coordenadas == null || !coordenadas.isArray() || coordenadas.size() < 4) {
            throw new IllegalArgumentException("Anel municipal inválido.");
        }
        double[] longitudes = new double[coordenadas.size()];
        double[] latitudes = new double[coordenadas.size()];
        for (int indice = 0; indice < coordenadas.size(); indice++) {
            JsonNode ponto = coordenadas.get(indice);
            if (ponto == null || !ponto.isArray() || ponto.size() < 2) {
                throw new IllegalArgumentException("Coordenada municipal inválida.");
            }
            longitudes[indice] = numeroFinito(ponto.get(0));
            latitudes[indice] = numeroFinito(ponto.get(1));
            if (
                longitudes[indice] < -180
                    || longitudes[indice] > 180
                    || latitudes[indice] < -90
                    || latitudes[indice] > 90
            ) {
                throw new IllegalArgumentException("Coordenada municipal fora dos limites.");
            }
        }
        int ultimo = coordenadas.size() - 1;
        if (
            Double.compare(longitudes[0], longitudes[ultimo]) != 0
                || Double.compare(latitudes[0], latitudes[ultimo]) != 0
        ) {
            throw new IllegalArgumentException("Anel municipal não está fechado.");
        }
        return new Anel(longitudes, latitudes);
    }

    private String textoObrigatorio(JsonNode objeto, String campo) {
        JsonNode valor = objeto == null ? null : objeto.get(campo);
        if (valor == null || !valor.isString() || valor.stringValue().isBlank()) {
            throw new IllegalArgumentException("Campo textual obrigatório ausente: " + campo);
        }
        return valor.stringValue();
    }

    private int inteiroObrigatorio(JsonNode objeto, String campo) {
        JsonNode valor = objeto == null ? null : objeto.get(campo);
        if (valor == null || !valor.isIntegralNumber() || !valor.canConvertToInt()) {
            throw new IllegalArgumentException("Campo inteiro obrigatório ausente: " + campo);
        }
        return valor.intValue();
    }

    private short shortObrigatorio(JsonNode objeto, String campo) {
        int valor = inteiroObrigatorio(objeto, campo);
        if (valor < Short.MIN_VALUE || valor > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Campo fora do intervalo SMALLINT: " + campo);
        }
        return (short) valor;
    }

    private BigDecimal decimalOpcional(JsonNode objeto, String campo) {
        JsonNode valor = objeto.get(campo);
        if (valor == null || valor.isNull()) {
            return null;
        }
        if (!valor.isNumber()) {
            throw new IllegalArgumentException("Campo decimal inválido: " + campo);
        }
        return valor.decimalValue();
    }

    private double numeroFinito(JsonNode valor) {
        if (valor == null || !valor.isNumber()) {
            throw new IllegalArgumentException("Coordenada não numérica.");
        }
        double numero = valor.doubleValue();
        if (!Double.isFinite(numero)) {
            throw new IllegalArgumentException("Coordenada não finita.");
        }
        return numero;
    }

    record Municipio(MunicipioInfo info, Envelope bbox, Geometria geometria) {
    }

    record Envelope(
        double minLongitude,
        double minLatitude,
        double maxLongitude,
        double maxLatitude
    ) {
        boolean contem(double longitude, double latitude) {
            return longitude >= minLongitude
                && longitude <= maxLongitude
                && latitude >= minLatitude
                && latitude <= maxLatitude;
        }

        boolean intersecta(Envelope outro) {
            return maxLongitude >= outro.minLongitude
                && minLongitude <= outro.maxLongitude
                && maxLatitude >= outro.minLatitude
                && minLatitude <= outro.maxLatitude;
        }
    }

    record Geometria(String tipo, List<Poligono> poligonos) {
    }

    record Poligono(List<Anel> aneis) {
    }

    record Anel(double[] longitudes, double[] latitudes) {
    }
}
