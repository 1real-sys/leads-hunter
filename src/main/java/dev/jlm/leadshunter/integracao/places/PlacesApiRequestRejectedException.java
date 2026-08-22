package dev.jlm.leadshunter.integracao.places;

public class PlacesApiRequestRejectedException extends RuntimeException {

    public PlacesApiRequestRejectedException(Throwable cause) {
        super("A Google Places API rejeitou a consulta enviada.", cause);
    }
}
