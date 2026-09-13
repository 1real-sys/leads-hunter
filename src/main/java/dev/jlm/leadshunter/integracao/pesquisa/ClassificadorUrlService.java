package dev.jlm.leadshunter.integracao.pesquisa;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ClassificadorUrlService {

    private static final int PONTUACAO_MINIMA = 70;
    private static final int MARGEM_UNICIDADE = 15;
    private static final Pattern ACENTOS = Pattern.compile("\\p{M}+");
    private static final Pattern NAO_ALFANUMERICO = Pattern.compile("[^a-z0-9]+");
    private static final Pattern CNPJ_NO_TEXTO = Pattern.compile(
        "(?<!\\d)\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}(?!\\d)"
    );

    private static final Set<String> PALAVRAS_VAZIAS = Set.of(
        "a", "as", "ao", "aos", "da", "das", "de", "do", "dos", "e", "em", "na", "nas",
        "no", "nos", "o", "os", "para", "com", "ltda", "me", "epp", "eireli", "sa"
    );

    private static final Set<String> TERMOS_GENERICOS = Set.of(
        "acougue", "carnes", "central", "comercial", "comercio", "confeitaria", "distribuidora",
        "doceria", "doces", "drogaria", "estabelecimento", "farmacia", "loja", "mercado",
        "mercearia", "nova", "novo", "padaria", "panificadora", "popular", "real", "restaurante",
        "sabor", "supermercado"
    );

    private final UrlCandidatoCanonicalizer canonicalizer;

    public ClassificadorUrlService(UrlCandidatoCanonicalizer canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    public PesquisaInformacoesWebResultado classificar(
        PesquisaLeadDados lead,
        List<GoogleResultadoWeb> resultadosInstagram,
        List<GoogleResultadoWeb> resultadosSite
    ) {
        if (lead == null) {
            throw new IllegalArgumentException("lead é obrigatório");
        }
        return new PesquisaInformacoesWebResultado(
            selecionar(lead, resultadosInstagram, TipoPesquisaWeb.INSTAGRAM),
            selecionar(lead, resultadosSite, TipoPesquisaWeb.SITE_PROPRIO)
        );
    }

    private Optional<URI> selecionar(
        PesquisaLeadDados lead,
        List<GoogleResultadoWeb> resultados,
        TipoPesquisaWeb tipo
    ) {
        if (resultados == null || resultados.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Avaliacao> melhoresPorDestino = new HashMap<>();
        for (GoogleResultadoWeb resultado : resultados) {
            Optional<URI> url = canonicalizer.canonicalizar(resultado == null ? null : resultado.url(), tipo);
            if (url.isEmpty()) {
                continue;
            }
            Avaliacao avaliacao = avaliar(lead, resultado, url.get(), tipo);
            String chave = canonicalizer.chaveDeduplicacao(url.get(), tipo);
            melhoresPorDestino.merge(
                chave,
                avaliacao,
                (atual, nova) -> nova.pontuacao() > atual.pontuacao() ? nova : atual
            );
        }

        List<Avaliacao> ordenadas = melhoresPorDestino.values().stream()
            .filter(Avaliacao::elegivel)
            .sorted(Comparator.comparingInt(Avaliacao::pontuacao).reversed())
            .toList();
        if (ordenadas.isEmpty()) {
            return Optional.empty();
        }

        Avaliacao melhor = ordenadas.getFirst();
        if (ordenadas.size() > 1
            && melhor.pontuacao() - ordenadas.get(1).pontuacao() < MARGEM_UNICIDADE) {
            return Optional.empty();
        }
        return Optional.of(melhor.url());
    }

    private Avaliacao avaliar(
        PesquisaLeadDados lead,
        GoogleResultadoWeb resultado,
        URI url,
        TipoPesquisaWeb tipo
    ) {
        String evidenciaOriginal = juntar(resultado.titulo(), resultado.resumo(), resultado.url().toString());
        String evidencia = normalizar(evidenciaOriginal);
        String nome = normalizar(lead.nome());
        Set<String> tokensNome = tokens(nome);
        Set<String> tokensDistintivos = tokensDistintivos(tokensNome);
        Set<String> tokensEvidencia = tokens(evidencia);

        int tokensCorrespondentes = intersecao(tokensNome, tokensEvidencia);
        double proporcaoNome = tokensNome.isEmpty() ? 0 : (double) tokensCorrespondentes / tokensNome.size();
        boolean nomeExato = contemFrase(evidencia, nome);
        int pontosNome = pontosNome(nomeExato, tokensCorrespondentes, proporcaoNome);

        String identificador = identificador(url, tipo);
        Set<String> tokensIdentificador = tokens(normalizar(identificador));
        boolean identificadorForte = !tokensDistintivos.isEmpty()
            && tokensIdentificador.containsAll(tokensDistintivos);
        int pontosIdentificador = identificadorForte
            ? 25
            : intersecao(tokensDistintivos, tokensIdentificador) > 0 ? 10 : 0;

        boolean municipioCompativel = contemFrase(evidencia, normalizar(lead.municipio()));
        int pontosLocalizacao = municipioCompativel ? 20 : pontosEndereco(lead, evidencia);
        int pontosCategoria = contemAlgum(evidencia, termosCategoria(lead.categoria())) ? 10 : 0;
        int pontosTelefone = contemDigitos(evidenciaOriginal, lead.telefoneNormalizado()) ? 30 : 0;
        int pontosCnpj = contemDigitos(evidenciaOriginal, lead.cnpj()) ? 40 : 0;
        int pontosPlaceId = contemLiteral(evidenciaOriginal, lead.googlePlaceId()) ? 35 : 0;
        int pontosRazaoSocial = contemFrase(evidencia, normalizar(lead.razaoSocial())) ? 15 : 0;

        boolean conflitoCnpj = possuiConflitoCnpj(evidenciaOriginal, lead.cnpj());
        boolean nomeForte = nomeExato || (tokensCorrespondentes >= 2 && proporcaoNome >= 0.75);
        boolean nomeDistintivo = tokensDistintivos.stream().anyMatch(token -> token.length() >= 4);
        boolean identificadorExternoForte = pontosTelefone > 0 || pontosCnpj > 0 || pontosPlaceId > 0;
        boolean corroborado = pontosLocalizacao > 0
            || identificadorExternoForte
            || (nomeDistintivo && identificadorForte && nomeExato);

        int pontuacao = pontosNome
            + pontosIdentificador
            + pontosLocalizacao
            + pontosCategoria
            + pontosTelefone
            + pontosCnpj
            + pontosPlaceId
            + pontosRazaoSocial;
        boolean elegivel = !conflitoCnpj
            && nomeForte
            && corroborado
            && pontuacao >= PONTUACAO_MINIMA;
        return new Avaliacao(url, pontuacao, elegivel);
    }

    private int pontosNome(boolean nomeExato, int correspondentes, double proporcao) {
        if (nomeExato) {
            return 45;
        }
        if (correspondentes >= 2 && proporcao >= 0.8) {
            return 40;
        }
        if (correspondentes >= 2 && proporcao >= 0.6) {
            return 30;
        }
        return correspondentes == 1 ? 15 : 0;
    }

    private int pontosEndereco(PesquisaLeadDados lead, String evidencia) {
        String logradouro = normalizar(primeiroNaoVazio(lead.logradouro(), inicioEndereco(lead.enderecoFormatado())));
        boolean ruaCompativel = contemTokensRelevantes(evidencia, logradouro, 2);
        boolean numeroCompativel = contemFrase(evidencia, normalizar(lead.numero()));
        boolean bairroCompativel = contemFrase(evidencia, normalizar(lead.bairro()));
        if (ruaCompativel && numeroCompativel) {
            return 20;
        }
        if (ruaCompativel || bairroCompativel) {
            return 10;
        }
        return 0;
    }

    private boolean contemTokensRelevantes(String evidencia, String valor, int minimo) {
        Set<String> relevantes = tokens(valor);
        relevantes.removeAll(PALAVRAS_VAZIAS);
        relevantes.removeIf(token -> token.length() < 3 || token.equals("rua") || token.equals("avenida"));
        return relevantes.size() >= minimo && tokens(evidencia).containsAll(relevantes);
    }

    private boolean possuiConflitoCnpj(String evidencia, String cnpjLead) {
        String esperado = somenteDigitos(cnpjLead);
        if (esperado.length() != 14) {
            return false;
        }
        Matcher matcher = CNPJ_NO_TEXTO.matcher(evidencia == null ? "" : evidencia);
        while (matcher.find()) {
            if (!somenteDigitos(matcher.group()).equals(esperado)) {
                return true;
            }
        }
        return false;
    }

    private boolean contemDigitos(String evidencia, String valor) {
        String procurado = somenteDigitos(valor);
        return procurado.length() >= 8 && somenteDigitos(evidencia).contains(procurado);
    }

    private boolean contemLiteral(String evidencia, String valor) {
        return valor != null
            && !valor.isBlank()
            && evidencia != null
            && evidencia.toLowerCase(Locale.ROOT).contains(valor.strip().toLowerCase(Locale.ROOT));
    }

    private boolean contemAlgum(String evidencia, Set<String> termos) {
        for (String termo : termos) {
            if (contemFrase(evidencia, termo)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> termosCategoria(CategoriaNegocio categoria) {
        return switch (categoria) {
            case MERCADO -> Set.of("mercado", "mercearia", "supermercado");
            case PADARIA -> Set.of("padaria", "panificadora", "panificacao");
            case DOCERIA -> Set.of("confeitaria", "doceria", "doces");
            case RESTAURANTE -> Set.of("cozinha", "gastronomia", "restaurante");
            case DISTRIBUIDORA -> Set.of("atacado", "distribuidora");
            case ACOUGUE -> Set.of("acougue", "carnes");
            case FARMACIA -> Set.of("drogaria", "farmacia");
            case OUTROS -> Set.of();
        };
    }

    private Set<String> tokensDistintivos(Set<String> tokensNome) {
        Set<String> resultado = new LinkedHashSet<>(tokensNome);
        resultado.removeAll(PALAVRAS_VAZIAS);
        resultado.removeAll(TERMOS_GENERICOS);
        resultado.removeIf(token -> token.length() < 3);
        return resultado;
    }

    private int intersecao(Set<String> esquerda, Set<String> direita) {
        int quantidade = 0;
        for (String valor : esquerda) {
            if (direita.contains(valor)) {
                quantidade++;
            }
        }
        return quantidade;
    }

    private boolean contemFrase(String texto, String frase) {
        return frase != null
            && !frase.isBlank()
            && (" " + texto + " ").contains(" " + frase + " ");
    }

    private Set<String> tokens(String valor) {
        Set<String> resultado = new LinkedHashSet<>();
        if (valor == null || valor.isBlank()) {
            return resultado;
        }
        for (String token : valor.split(" ")) {
            if (!token.isBlank() && !PALAVRAS_VAZIAS.contains(token)) {
                resultado.add(token);
            }
        }
        return resultado;
    }

    private String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        String semAcentos = ACENTOS.matcher(
            Normalizer.normalize(valor, Normalizer.Form.NFD)
        ).replaceAll("");
        return NAO_ALFANUMERICO.matcher(semAcentos.toLowerCase(Locale.ROOT))
            .replaceAll(" ")
            .strip();
    }

    private String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    private String identificador(URI url, TipoPesquisaWeb tipo) {
        if (tipo == TipoPesquisaWeb.INSTAGRAM) {
            return url.getPath();
        }
        String host = url.getHost();
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private String inicioEndereco(String endereco) {
        if (endereco == null) {
            return null;
        }
        int virgula = endereco.indexOf(',');
        return virgula < 0 ? endereco : endereco.substring(0, virgula);
    }

    private String primeiroNaoVazio(String primeiro, String segundo) {
        return primeiro != null && !primeiro.isBlank() ? primeiro : segundo;
    }

    private String juntar(String... valores) {
        List<String> presentes = new ArrayList<>();
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                presentes.add(valor);
            }
        }
        return String.join(" ", presentes);
    }

    private record Avaliacao(URI url, int pontuacao, boolean elegivel) {
    }
}
