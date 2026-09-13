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
    private static final Pattern TELEFONE_NO_TEXTO = Pattern.compile(
        "(?<![\\p{L}\\d])(?:\\+?55[ .-]?)?\\(?[1-9]\\d\\)?[ .-]?\\d{4,5}[ .-]?\\d{4}(?!\\d)"
    );
    private static final Pattern LOCAL_COM_UF = Pattern.compile(
        "(?iu)([\\p{L}][\\p{L} .']{1,60})\\s*[-/,|]\\s*"
            + "(AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)\\b"
    );

    private static final Set<String> PALAVRAS_VAZIAS = Set.of(
        "a", "as", "ao", "aos", "da", "das", "de", "do", "dos", "e", "em", "na", "nas",
        "no", "nos", "o", "os", "para", "com", "ltda", "me", "epp", "eireli", "sa"
    );

    private static final Set<String> TERMOS_GENERICOS = Set.of(
        "acougue", "carnes", "central", "comercial", "comercio", "confeitaria", "distribuidora",
        "doceria", "doces", "drogaria", "estabelecimento", "farmacia", "loja", "mercado",
        "mercearia", "nova", "novo", "padaria", "panificadora", "popular", "real", "restaurante",
        "sabor", "supermercado", "atacado", "varejo", "armazem", "super", "rede"
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
                this::consolidar
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
        if (melhor.pontuacao() < PONTUACAO_MINIMA) return Optional.empty();
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
        // Query strings e caminhos de diretórios não provam identidade/localização.
        String evidenciaOriginal = juntar(resultado.titulo(), resultado.resumo());
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
        String identificadorCompacto = normalizar(identificador).replace(" ", "");
        boolean identificadorForte = !tokensDistintivos.isEmpty()
            && tokensDistintivos.stream().allMatch(identificadorCompacto::contains);
        int pontosIdentificador = identificadorForte
            ? 25
            : tokensDistintivos.stream().anyMatch(identificadorCompacto::contains) ? 10 : 0;

        // "Castelo" em "Supermercado Castelo" não é uma confirmação de município.
        String contextoLocal = evidencia.replace(nome, " ");
        String municipio = normalizar(lead.municipio());
        if (!municipio.isBlank()) {
            contextoLocal = contextoLocal.replaceAll(
                "\\b(?:bairro|rua|avenida|rodovia|r|av) " + Pattern.quote(municipio) + "\\b", " ");
        }
        boolean municipioCompativel = contemFrase(contextoLocal, normalizar(lead.municipio()));
        int pontosLocalizacao = municipioCompativel ? 20 : pontosEndereco(lead, contextoLocal);
        int pontosCategoria = contemAlgum(evidencia, termosCategoria(lead.categoria())) ? 10 : 0;
        int pontosTelefone = telefoneCompativel(evidenciaOriginal, lead.telefoneNormalizado()) ? 30 : 0;
        int pontosCnpj = cnpjCompativel(evidenciaOriginal, lead.cnpj()) ? 40 : 0;
        int pontosPlaceId = contemLiteral(evidenciaOriginal, lead.googlePlaceId()) ? 35 : 0;
        int pontosRazaoSocial = contemFrase(evidencia, normalizar(lead.razaoSocial())) ? 15 : 0;

        boolean conflitoCnpj = possuiConflitoCnpj(evidenciaOriginal, lead.cnpj());
        boolean nomeForte = (nomeExato || (tokensCorrespondentes >= 2 && proporcaoNome >= 0.75))
            && tokensDistintivos.stream().allMatch(token -> tokensEvidencia.contains(token) || identificadorForte);
        boolean identificadorExternoForte = pontosTelefone > 0 || pontosCnpj > 0 || pontosPlaceId > 0;
        boolean conflitoLocalizacao = conflitoLocalizacao(evidenciaOriginal, lead);
        boolean corroborado = municipioCompativel || pontosLocalizacao >= 20 || identificadorExternoForte;
        Set<String> tokensIdentidade = tokensDistintivos.isEmpty() ? tokensNome : tokensDistintivos;
        boolean identificadorRelacionado = !tokensIdentidade.isEmpty()
            && tokensIdentidade.stream().allMatch(identificadorCompacto::contains);
        // Identificador abreviado só é aceito com confirmação forte (telefone, CNPJ ou Place ID).
        boolean destinoRelacionado = identificadorRelacionado
            || (tipo == TipoPesquisaWeb.INSTAGRAM && identificadorExternoForte);
        boolean conflito = conflitoCnpj || conflitoLocalizacao
            || (pontosCnpj == 0 && pontosPlaceId == 0 && conflitoDdd(evidenciaOriginal, lead.telefoneNormalizado()));

        int pontuacao = pontosNome
            + pontosIdentificador
            + pontosLocalizacao
            + pontosCategoria
            + pontosTelefone
            + pontosCnpj
            + pontosPlaceId
            + pontosRazaoSocial;
        boolean elegivel = !conflito
            && nomeForte
            && corroborado
            && destinoRelacionado;
        return new Avaliacao(url, pontuacao, elegivel, conflito);
    }

    private Avaliacao consolidar(Avaliacao atual, Avaliacao nova) {
        Avaliacao melhor = atual.elegivel() != nova.elegivel() ? (atual.elegivel() ? atual : nova)
            : nova.pontuacao() > atual.pontuacao() ? nova : atual;
        boolean conflito = atual.conflito() || nova.conflito();
        return new Avaliacao(melhor.url(), melhor.pontuacao(), melhor.elegivel() && !conflito, conflito);
    }

    private boolean conflitoLocalizacao(String evidencia, PesquisaLeadDados lead) {
        String ufLead = lead.uf() == null ? "" : lead.uf().toLowerCase(Locale.ROOT).strip();
        String municipio = normalizar(lead.municipio());
        Matcher matcher = LOCAL_COM_UF.matcher(evidencia);
        while (matcher.find()) {
            // "Ofertas, se precisar..." não identifica o estado de Sergipe.
            if (matcher.group(2).equals("se")
                && evidencia.substring(matcher.start(), matcher.start(2)).stripTrailing().endsWith(",")
                && Pattern.compile("^\\s+\\p{Ll}").matcher(evidencia.substring(matcher.end())).find()) continue;
            String uf = matcher.group(2).toLowerCase(Locale.ROOT);
            if (!ufLead.isBlank() && !uf.equals(ufLead)) return true;
            String cidade = normalizar(matcher.group(1));
            // Prefixos descritivos comuns não fazem parte do nome do município.
            cidade = cidade.replaceFirst("^.*\\b(?:em|cidade de|municipio de|centro de) +", "").strip();
            if (!municipio.isBlank() && !cidade.equals(municipio)) return true;
        }
        return false;
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
        if (ruaCompativel && numeroCompativel) {
            return 20;
        }
        if (ruaCompativel) {
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

    private boolean telefoneCompativel(String evidencia, String valor) {
        String esperado = telefoneNacional(valor);
        if (esperado.length() != 10 && esperado.length() != 11) return false;
        Matcher matcher = TELEFONE_NO_TEXTO.matcher(evidencia);
        while (matcher.find()) {
            if (telefoneNacional(matcher.group()).equals(esperado)) return true;
        }
        return false;
    }

    private String telefoneNacional(String valor) {
        String digitos = somenteDigitos(valor);
        return (digitos.length() == 12 || digitos.length() == 13) && digitos.startsWith("55")
            ? digitos.substring(2) : digitos;
    }

    private boolean conflitoDdd(String evidencia, String valor) {
        String esperado = telefoneNacional(valor);
        if (esperado.length() != 10 && esperado.length() != 11) return false;
        Matcher matcher = TELEFONE_NO_TEXTO.matcher(evidencia);
        boolean encontrou = false;
        while (matcher.find()) {
            encontrou = true;
            if (telefoneNacional(matcher.group()).startsWith(esperado.substring(0, 2))) return false;
        }
        return encontrou;
    }

    private boolean cnpjCompativel(String evidencia, String valor) {
        String esperado = somenteDigitos(valor);
        if (esperado.length() != 14) return false;
        Matcher matcher = CNPJ_NO_TEXTO.matcher(evidencia);
        while (matcher.find()) {
            if (somenteDigitos(matcher.group()).equals(esperado)) return true;
        }
        return false;
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
        String[] partes = host.split("\\.");
        int indice = partes.length - 2;
        if (partes.length >= 3 && partes[partes.length - 1].length() == 2
            && Set.of("com", "net", "org", "co", "gov", "edu", "ac").contains(partes[indice])) indice--;
        return partes[indice];
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
        return String.join("\n", presentes);
    }

    private record Avaliacao(URI url, int pontuacao, boolean elegivel, boolean conflito) {
    }
}
