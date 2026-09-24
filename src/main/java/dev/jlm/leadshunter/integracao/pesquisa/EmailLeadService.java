package dev.jlm.leadshunter.integracao.pesquisa;

import dev.jlm.leadshunter.lead.EmailSiteHost;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Seleciona contato do site próprio depois de confirmar o estabelecimento no conteúdo público. */
@Service
public class EmailLeadService {
    private final LeitorPaginaCandidata leitor;
    private final UrlCandidatoCanonicalizer canonicalizer;
    private final ClassificadorUrlService classificador;

    public EmailLeadService(LeitorPaginaCandidata leitor, UrlCandidatoCanonicalizer canonicalizer,
                            ClassificadorUrlService classificador) {
        this.leitor = leitor;
        this.canonicalizer = canonicalizer;
        this.classificador = classificador;
    }

    public Resultado extrair(PesquisaLeadDados lead) {
        Optional<URI> site = site(lead.website());
        if (site.isEmpty()) return new Resultado(Estado.SEM_SITE, null, null, false);
        URI origem = site.orElseThrow();
        String host = EmailSiteHost.de(origem.toString());
        Optional<PaginaLida> primeira = leitor.lerPagina(origem);
        if (primeira.isEmpty()) return new Resultado(Estado.FALHA, null, host, false);

        List<PaginaLida> paginas = new ArrayList<>();
        paginas.add(primeira.orElseThrow());
        Selecao selecao = selecionar(host, paginas);
        var identidade = classificador.avaliarIdentidadeParaEmail(lead, paginas);
        if (identidade == ClassificadorUrlService.EstadoIdentidadeEmail.CONFLITO) {
            return new Resultado(Estado.SEM_EMAIL_ELEGIVEL, null, host, selecao.externos());
        }
        if (selecao.email() != null
            && identidade == ClassificadorUrlService.EstadoIdentidadeEmail.CONFIRMADA) {
            return new Resultado(Estado.ENCONTRADO, selecao.email(), host, selecao.externos());
        }

        Optional<URI> contato = primeira.orElseThrow().linksContato().stream().findFirst();
        if (contato.isPresent()) {
            Optional<PaginaLida> segunda = leitor.lerPagina(contato.orElseThrow());
            if (segunda.isEmpty()) return new Resultado(Estado.FALHA, null, host, selecao.externos());
            paginas.add(segunda.orElseThrow());
            selecao = selecionar(host, paginas);
        }
        if (selecao.email() != null && classificador.avaliarIdentidadeParaEmail(lead, paginas)
            == ClassificadorUrlService.EstadoIdentidadeEmail.CONFIRMADA) {
            return new Resultado(Estado.ENCONTRADO, selecao.email(), host, selecao.externos());
        }
        return new Resultado(Estado.SEM_EMAIL_ELEGIVEL, null, host, selecao.externos());
    }

    private Optional<URI> site(String website) {
        if (website == null || website.isBlank()) return Optional.empty();
        try {
            return canonicalizer.canonicalizar(URI.create(website.strip()), TipoPesquisaWeb.SITE_PROPRIO);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Selecao selecionar(String host, List<PaginaLida> paginas) {
        List<String> proprios = new ArrayList<>();
        boolean externos = false;
        for (PaginaLida pagina : paginas) {
            for (String email : pagina.emails()) {
                if (!valido(email)) continue;
                String dominio = email.substring(email.indexOf('@') + 1);
                if (dominio.equals(host) || dominio.endsWith("." + host)) {
                    proprios.add(email);
                } else {
                    externos = true;
                }
            }
        }
        String escolhido = proprios.stream().distinct()
            .min(Comparator.comparingInt(this::prioridade).thenComparing(Comparator.naturalOrder()))
            .orElse(null);
        return new Selecao(escolhido, externos);
    }

    private boolean valido(String email) {
        if (email == null || email.length() > 320 || email.length() < 6
            || email.indexOf('@') < 1 || email.indexOf('@') != email.lastIndexOf('@')) return false;
        String normalizado = email.toLowerCase(Locale.ROOT);
        String local = normalizado.substring(0, normalizado.indexOf('@'));
        String dominio = normalizado.substring(normalizado.indexOf('@') + 1);
        return !local.equals("noreply") && !local.equals("no-reply")
            && !dominio.equals("example.com") && !dominio.endsWith(".example.com")
            && !dominio.equals("sentry.io") && !dominio.endsWith(".sentry.io")
            && !dominio.contains("wixpress") && !normalizado.endsWith(".png")
            && !normalizado.endsWith(".jpg") && !normalizado.endsWith(".svg")
            && dominio.contains(".") && !dominio.startsWith("-") && !dominio.endsWith("-");
    }

    private int prioridade(String email) {
        String local = email.substring(0, email.indexOf('@'));
        return switch (local) {
            case "contato", "comercial", "vendas", "atendimento" -> 0;
            default -> 1;
        };
    }

    private record Selecao(String email, boolean externos) { }

    public enum Estado { SEM_SITE, ENCONTRADO, SEM_EMAIL_ELEGIVEL, FALHA }

    public record Resultado(Estado estado, String email, String origemHost,
                            boolean descartouDominioExterno) { }
}
