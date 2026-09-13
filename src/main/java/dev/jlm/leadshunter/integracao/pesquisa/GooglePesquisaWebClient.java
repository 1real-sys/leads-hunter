package dev.jlm.leadshunter.integracao.pesquisa;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GooglePesquisaWebClient implements GooglePesquisaGateway {

    private static final String GOOGLE_SEARCH_URL = "https://www.google.com/search";
    private static final Pattern CARACTERES_CONTROLE = Pattern.compile("[\\p{Cntrl}\\\"`\\\\]+");
    private static final Pattern ESPACOS = Pattern.compile("\\s+");
    private static final int TAMANHO_MAXIMO_PARCELA = 140;

    private final GooglePesquisaWebNavigator navigator;
    private final GooglePesquisaHtmlParser parser;
    private final int maximoResultados;
    private final long bloqueioCooldownMs;
    private final LongSupplier relogioMs;
    private final AtomicLong bloqueadoAteMs = new AtomicLong(Long.MIN_VALUE);

    @Autowired
    public GooglePesquisaWebClient(
        PlaywrightGooglePesquisaNavigator navigator,
        GooglePesquisaHtmlParser parser,
        @Value("${pesquisa-inteligente.google.max-resultados:10}") int maximoResultados,
        @Value("${pesquisa-inteligente.google.bloqueio-cooldown-ms:3600000}") long bloqueioCooldownMs
    ) {
        this(
            (GooglePesquisaWebNavigator) navigator,
            parser,
            maximoResultados,
            bloqueioCooldownMs,
            System::currentTimeMillis
        );
    }

    GooglePesquisaWebClient(
        GooglePesquisaWebNavigator navigator,
        GooglePesquisaHtmlParser parser,
        int maximoResultados
    ) {
        this(navigator, parser, maximoResultados, 0, System::currentTimeMillis);
    }

    GooglePesquisaWebClient(
        GooglePesquisaWebNavigator navigator,
        GooglePesquisaHtmlParser parser,
        int maximoResultados,
        long bloqueioCooldownMs,
        LongSupplier relogioMs
    ) {
        if (maximoResultados < 1 || maximoResultados > 20
            || bloqueioCooldownMs < 0 || bloqueioCooldownMs > 86_400_000) {
            throw new IllegalArgumentException("Configuração inválida da pesquisa pública");
        }
        this.navigator = navigator;
        this.parser = parser;
        this.maximoResultados = maximoResultados;
        this.bloqueioCooldownMs = bloqueioCooldownMs;
        this.relogioMs = relogioMs;
    }

    @Override
    public GooglePesquisaWebResponse pesquisar(GooglePesquisaWebRequest request) {
        verificarBloqueioTemporario();
        String consulta = montarConsulta(request);
        URI uri = UriComponentsBuilder.fromUriString(GOOGLE_SEARCH_URL)
            .queryParam("q", consulta)
            .queryParam("hl", "pt-BR")
            .queryParam("gl", "br")
            .queryParam("num", maximoResultados)
            .queryParam("filter", 1)
            .build()
            .encode()
            .toUri();

        try {
            GooglePesquisaPagina pagina = navigator.navegar(uri);
            return new GooglePesquisaWebResponse(
                request.googlePlaceId(),
                request.tipo(),
                consulta,
                parser.extrair(pagina, maximoResultados)
            );
        } catch (GooglePesquisaWebBloqueadaException exception) {
            ativarBloqueioTemporario();
            throw exception;
        }
    }

    private void verificarBloqueioTemporario() {
        if (relogioMs.getAsLong() < bloqueadoAteMs.get()) {
            throw new GooglePesquisaWebBloqueadaException();
        }
    }

    private void ativarBloqueioTemporario() {
        if (bloqueioCooldownMs == 0) {
            return;
        }
        long agora = relogioMs.getAsLong();
        long limite = agora > Long.MAX_VALUE - bloqueioCooldownMs
            ? Long.MAX_VALUE
            : agora + bloqueioCooldownMs;
        bloqueadoAteMs.accumulateAndGet(limite, Math::max);
    }

    private String montarConsulta(GooglePesquisaWebRequest request) {
        List<String> partes = new ArrayList<>();
        String nome = limpar(request.nome());
        if (nome.isBlank()) {
            throw new IllegalArgumentException("nome deve conter texto pesquisável");
        }
        partes.add(frase(nome));
        partes.add(frase(rotuloCategoria(request.categoria())));

        String localizacao = montarLocalizacao(request);
        if (!localizacao.isBlank()) {
            partes.add(frase(localizacao));
        }

        if (request.tipo() == TipoPesquisaWeb.INSTAGRAM) {
            partes.add("site:instagram.com");
        } else {
            partes.add("site oficial");
            partes.add("-site:instagram.com");
            partes.add("-site:facebook.com");
            partes.add("-site:ifood.com.br");
            partes.add("-site:tripadvisor.com.br");
            partes.add("-site:linktr.ee");
        }
        return String.join(" ", partes);
    }

    private String montarLocalizacao(GooglePesquisaWebRequest request) {
        String municipio = limpar(request.municipio());
        String uf = limpar(request.uf()).toUpperCase(Locale.ROOT);
        if (!municipio.isBlank()) {
            return uf.isBlank() ? municipio : municipio + " " + uf;
        }
        return limpar(request.enderecoFormatado());
    }

    private String frase(String valor) {
        return "\"" + limpar(valor) + "\"";
    }

    private String limpar(String valor) {
        if (valor == null) {
            return "";
        }
        String normalizado = Normalizer.normalize(valor, Normalizer.Form.NFKC);
        String limpo = ESPACOS.matcher(CARACTERES_CONTROLE.matcher(normalizado).replaceAll(" "))
            .replaceAll(" ")
            .strip();
        return limpo.length() <= TAMANHO_MAXIMO_PARCELA
            ? limpo
            : limpo.substring(0, TAMANHO_MAXIMO_PARCELA).strip();
    }

    private String rotuloCategoria(CategoriaNegocio categoria) {
        return switch (categoria) {
            case MERCADO -> "mercado";
            case PADARIA -> "padaria";
            case DOCERIA -> "doceria";
            case RESTAURANTE -> "restaurante";
            case DISTRIBUIDORA -> "distribuidora";
            case ACOUGUE -> "açougue";
            case FARMACIA -> "farmácia";
            case OUTROS -> "estabelecimento comercial";
        };
    }
}
