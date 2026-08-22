package dev.jlm.leadshunter.integracao.places;

public class PlacesApiUnavailableException extends RuntimeException {

    public PlacesApiUnavailableException() {
        super("A Google Places API está indisponível no momento. Tente novamente mais tarde.");
    }

    public PlacesApiUnavailableException(Throwable cause) {
        super("A Google Places API está indisponível no momento. Tente novamente mais tarde.", cause);
    }
}
