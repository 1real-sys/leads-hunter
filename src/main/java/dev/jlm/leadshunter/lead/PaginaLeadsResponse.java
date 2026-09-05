package dev.jlm.leadshunter.lead;

import java.util.List;
import org.springframework.data.domain.Page;

public record PaginaLeadsResponse(
    List<LeadResponse> leads,
    int pagina,
    int tamanho,
    long totalElementos,
    int totalPaginas
) {

    public static PaginaLeadsResponse from(Page<LeadResponse> pagina) {
        return new PaginaLeadsResponse(
            List.copyOf(pagina.getContent()),
            pagina.getNumber(),
            pagina.getSize(),
            pagina.getTotalElements(),
            pagina.getTotalPages()
        );
    }
}
