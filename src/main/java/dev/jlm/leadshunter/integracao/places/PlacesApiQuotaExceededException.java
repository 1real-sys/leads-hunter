package dev.jlm.leadshunter.integracao.places;

public class PlacesApiQuotaExceededException extends RuntimeException {

    public PlacesApiQuotaExceededException() {
        super("A cota da Google Places API foi excedida. Tente novamente mais tarde.");
    }
}
