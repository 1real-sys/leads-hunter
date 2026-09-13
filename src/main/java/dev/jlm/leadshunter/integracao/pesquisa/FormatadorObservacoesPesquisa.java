package dev.jlm.leadshunter.integracao.pesquisa;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class FormatadorObservacoesPesquisa {

    public static final String INICIO_BLOCO = "--- Pesquisa inteligente ---";
    public static final String FIM_BLOCO = "--- Fim da pesquisa inteligente ---";
    public static final String SEM_INFORMACOES =
        "pesquisa inteligente não encontrou mais informações";

    private static final Pattern BLOCO = Pattern.compile(
        "(?m)^" + Pattern.quote(INICIO_BLOCO) + "\\R(?<conteudo>[\\s\\S]*?)^"
            + Pattern.quote(FIM_BLOCO) + "(?=\\R|\\z)"
    );
    private static final Pattern INSTAGRAM = Pattern.compile(
        "(?m)^Instagram:[ \\t]*\\R(?<url>https?://\\S+)[ \\t]*(?=\\R|\\z)"
    );
    private static final Pattern SITE = Pattern.compile(
        "(?m)^Site próprio:[ \\t]*\\R(?<url>https?://\\S+)[ \\t]*(?=\\R|\\z)"
    );

    private final UrlCandidatoCanonicalizer canonicalizer;

    public FormatadorObservacoesPesquisa(UrlCandidatoCanonicalizer canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    public boolean possuiInstagramESiteValidos(String observacoes) {
        PesquisaInformacoesWebResultado links = extrairLinks(observacoes);
        return links.instagram().isPresent() && links.siteProprio().isPresent();
    }

    public PesquisaInformacoesWebResultado extrairLinks(String observacoes) {
        if (observacoes == null || observacoes.isEmpty()) {
            return vazio();
        }

        Matcher blocos = BLOCO.matcher(observacoes);
        Optional<URI> instagram = Optional.empty();
        Optional<URI> site = Optional.empty();
        while (blocos.find()) {
            String conteudo = blocos.group("conteudo");
            if (instagram.isEmpty()) {
                instagram = extrairUrl(conteudo, INSTAGRAM, TipoPesquisaWeb.INSTAGRAM);
            }
            if (site.isEmpty()) {
                site = extrairUrl(conteudo, SITE, TipoPesquisaWeb.SITE_PROPRIO);
            }
        }
        return new PesquisaInformacoesWebResultado(instagram, site);
    }

    public String atualizar(
        String observacoes,
        PesquisaInformacoesWebResultado novoResultado
    ) {
        if (novoResultado == null) {
            throw new IllegalArgumentException("novoResultado é obrigatório");
        }

        PesquisaInformacoesWebResultado consolidado = consolidar(observacoes, novoResultado);
        String novoBloco = montarBloco(consolidado);
        if (observacoes == null || observacoes.isEmpty()) {
            return novoBloco;
        }

        Matcher blocos = BLOCO.matcher(observacoes);
        if (!blocos.find()) {
            String quebra = observacoes.contains("\r\n") ? "\r\n" : "\n";
            String separador = observacoes.endsWith(quebra + quebra)
                ? ""
                : observacoes.endsWith(quebra) ? quebra : quebra + quebra;
            return observacoes + separador + novoBloco;
        }

        StringBuilder atualizado = new StringBuilder(observacoes.length() + novoBloco.length());
        int cursor = 0;
        boolean inserido = false;
        do {
            atualizado.append(observacoes, cursor, blocos.start());
            if (!inserido) {
                atualizado.append(novoBloco);
                inserido = true;
            }
            cursor = blocos.end();
        } while (blocos.find());
        atualizado.append(observacoes, cursor, observacoes.length());
        return atualizado.toString();
    }

    private PesquisaInformacoesWebResultado consolidar(
        String observacoes,
        PesquisaInformacoesWebResultado novoResultado
    ) {
        PesquisaInformacoesWebResultado anterior = extrairLinks(observacoes);
        Optional<URI> novoInstagram = canonicalizar(
            novoResultado.instagram(),
            TipoPesquisaWeb.INSTAGRAM
        );
        Optional<URI> novoSite = canonicalizar(
            novoResultado.siteProprio(),
            TipoPesquisaWeb.SITE_PROPRIO
        );
        return new PesquisaInformacoesWebResultado(
            novoInstagram.or(() -> anterior.instagram()),
            novoSite.or(() -> anterior.siteProprio())
        );
    }

    private String montarBloco(PesquisaInformacoesWebResultado resultado) {
        StringBuilder bloco = new StringBuilder(INICIO_BLOCO).append('\n');
        if (resultado.instagram().isPresent()) {
            bloco.append("Instagram:\n").append(resultado.instagram().get());
        }
        if (resultado.siteProprio().isPresent()) {
            if (resultado.instagram().isPresent()) {
                bloco.append("\n\n");
            }
            bloco.append("Site próprio:\n").append(resultado.siteProprio().get());
        }
        if (resultado.instagram().isEmpty() && resultado.siteProprio().isEmpty()) {
            bloco.append(SEM_INFORMACOES);
        }
        return bloco.append('\n').append(FIM_BLOCO).toString();
    }

    private Optional<URI> extrairUrl(
        String conteudo,
        Pattern rotulo,
        TipoPesquisaWeb tipo
    ) {
        Matcher matcher = rotulo.matcher(conteudo);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return canonicalizer.canonicalizar(URI.create(matcher.group("url")), tipo);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Optional<URI> canonicalizar(Optional<URI> url, TipoPesquisaWeb tipo) {
        return url.flatMap(valor -> canonicalizer.canonicalizar(valor, tipo));
    }

    private PesquisaInformacoesWebResultado vazio() {
        return new PesquisaInformacoesWebResultado(Optional.empty(), Optional.empty());
    }
}
