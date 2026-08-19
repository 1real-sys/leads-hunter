package dev.jlm.leadshunter.busca;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class BuscaNaoEncontradaException extends RuntimeException {

    public BuscaNaoEncontradaException(Long id) {
        super("Busca não encontrada: " + id);
    }
}
