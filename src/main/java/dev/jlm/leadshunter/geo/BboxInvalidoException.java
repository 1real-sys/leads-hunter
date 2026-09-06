package dev.jlm.leadshunter.geo;

public class BboxInvalidoException extends IllegalArgumentException {

    public BboxInvalidoException() {
        this(
            "O parâmetro de consulta 'bbox' deve conter minLng,minLat,maxLng,maxLat "
                + "com coordenadas finitas e em ordem válida."
        );
    }

    public BboxInvalidoException(String mensagem) {
        super(mensagem);
    }
}
