package dev.jlm.leadshunter.integracao.places;

public class PlacesApiInvalidResponseException extends RuntimeException {

    public PlacesApiInvalidResponseException(Throwable cause) {
        super("A Google Places API retornou uma resposta inválida.", cause);
    }
}
