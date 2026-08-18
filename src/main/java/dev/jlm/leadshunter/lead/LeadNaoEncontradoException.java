package dev.jlm.leadshunter.lead;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class LeadNaoEncontradoException extends RuntimeException {

    public LeadNaoEncontradoException(Long id) {
        super("Lead não encontrado: " + id);
    }
}
