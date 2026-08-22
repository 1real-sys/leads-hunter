package dev.jlm.leadshunter.integracao.places;

public class PlacesApiConfigurationException extends RuntimeException {

    public PlacesApiConfigurationException(String message) {
        super(message);
    }

    public PlacesApiConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
