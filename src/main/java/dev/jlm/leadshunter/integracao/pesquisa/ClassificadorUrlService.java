package dev.jlm.leadshunter.integracao.pesquisa;

import dev.jlm.leadshunter.lead.CategoriaNegocio;
import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private static final int MAXIMO_CONFIRMACOES = 2;
    // Handle de ramo (marca + discriminação) é um identificador forte, porém não exato.
    private static final int PONTOS_RAMO_IDENTIFICADOR = 65;
    private static final Pattern INSTAGRAM_ROTULADO = Pattern.compile(
        "(?i)\\binstagram\\s*:\\s*@?([a-z0-9._]{1,30})(?![a-z0-9._:/])"
    );
    private static final Pattern INSTAGRAM_LINK = Pattern.compile(
        "(?i)https?://(?:www\\.|m\\.)?instagram\\.com/[^\\s<>\"·,;]+"
    );
    private static final Pattern ACENTOS = Pattern.compile("\\p{M}+");
    private static final Pattern NAO_ALFANUMERICO = Pattern.compile("[^a-z0-9]+");
    private static final Pattern CNPJ_NO_TEXTO = Pattern.compile(
        "(?<!\\d)\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}(?!\\d)"
    );
    private static final Pattern TELEFONE_NO_TEXTO = Pattern.compile(
        "(?<![\\p{L}\\d])(?:\\+?55[ .-]?)?(?:\\(0?[1-9]\\d\\)|0?[1-9]\\d)[ .-]?\\d{4,5}[ .-]?\\d{4}(?!\\d)"
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
            selecionar(lead, acrescentarReferencias(lead, resultadosInstagram, resultadosSite), TipoPesquisaWeb.INSTAGRAM),
            selecionar(lead, resultadosSite, TipoPesquisaWeb.SITE_PROPRIO)
        );
    }

    List<URI> perfisParaConfirmar(PesquisaLeadDados lead, List<GoogleResultadoWeb> resultados) {
        Map<String, Avaliacao> perfis = new HashMap<>();
        for (GoogleResultadoWeb resultado : resultados) {
            canonicalizer.canonicalizar(resultado == null ? null : resultado.url(), TipoPesquisaWeb.INSTAGRAM)
                .ifPresent(url -> perfis.merge(url.toString(), avaliar(lead, resultado, url, TipoPesquisaWeb.INSTAGRAM),
                    this::consolidar));
        }
        return perfis.values().stream()
            .filter(a -> a.nomeRelacionado() && !a.elegivel() && !a.conflito())
            .sorted(Comparator.comparingInt(Avaliacao::pontuacao).reversed()
                .thenComparing(a -> a.url().toString()))
            .limit(MAXIMO_CONFIRMACOES)
            .map(Avaliacao::url).toList();
    }

    /** Candidatos com relação de nome ainda não confirmados, para validar lendo a própria página. */
    List<GoogleResultadoWeb> candidatosParaValidar(PesquisaLeadDados lead, List<GoogleResultadoWeb> resultados) {
        Map<String, Avaliacao> melhores = new LinkedHashMap<>();
        Map<String, GoogleResultadoWeb> originais = new HashMap<>();
        for (GoogleResultadoWeb resultado : resultados) {
            if (resultado == null) {
                continue;
            }
            for (TipoPesquisaWeb tipo : TipoPesquisaWeb.values()) {
                var url = canonicalizer.canonicalizar(resultado.url(), tipo);
                if (url.isEmpty()) {
                    continue;
                }
                String chave = tipo.name() + "|" + canonicalizer.chaveDeduplicacao(url.get(), tipo);
                melhores.merge(chave, avaliar(lead, resultado, url.get(), tipo), this::consolidar);
                originais.putIfAbsent(chave, resultado);
            }
        }
        Map<URI, GoogleResultadoWeb> unicos = new LinkedHashMap<>();
        Comparator<Map.Entry<String, Avaliacao>> ordem = Comparator
            // Perfil de Instagram primeiro: é onde a bio costuma confirmar telefone/endereço.
            .comparingInt((Map.Entry<String, Avaliacao> entrada) ->
                entrada.getKey().startsWith(TipoPesquisaWeb.INSTAGRAM.name() + "|") ? 0 : 1)
            .thenComparing(Comparator.comparingInt(
                (Map.Entry<String, Avaliacao> entrada) -> entrada.getValue().pontuacao()).reversed())
            .thenComparing(entrada -> entrada.getValue().url().toString());
        melhores.entrySet().stream()
            .filter(entrada -> entrada.getValue().nomeRelacionado()
                && !entrada.getValue().elegivel() && !entrada.getValue().conflito())
            .sorted(ordem)
            .forEach(entrada -> {
                GoogleResultadoWeb original = originais.get(entrada.getKey());
                unicos.putIfAbsent(entrada.getValue().url(),
                    new GoogleResultadoWeb(entrada.getValue().url(), original.titulo(), original.resumo()));
            });
        return List.copyOf(unicos.values());
    }

    private List<GoogleResultadoWeb> acrescentarReferencias(
        PesquisaLeadDados lead, List<GoogleResultadoWeb> instagram, List<GoogleResultadoWeb> sites
    ) {
        if (instagram == null || instagram.isEmpty() || sites == null || sites.isEmpty()) return instagram;
        Map<URI, GoogleResultadoWeb> perfis = new LinkedHashMap<>();
        for (GoogleResultadoWeb candidato : instagram) {
            canonicalizer.canonicalizar(candidato == null ? null : candidato.url(), TipoPesquisaWeb.INSTAGRAM)
                .ifPresent(url -> perfis.putIfAbsent(url, candidato));
        }
        if (perfis.isEmpty()) return instagram;
        List<GoogleResultadoWeb> enriquecidos = new ArrayList<>(instagram);
        for (GoogleResultadoWeb fonte : sites) {
            if (fonte == null || fonte.resumo() == null
                || canonicalizer.canonicalizar(fonte.url(), TipoPesquisaWeb.SITE_PROPRIO).isEmpty()) continue;
            // Não juntar snippets, saltos de conteúdo ou blocos de unidades diferentes.
            for (String trecho : fonte.resumo().split("(?:\\R|\\*{3,}|\\.{3,}|…)+")) {
                Set<URI> referencias = referenciasInstagram(trecho);
                if (referencias.size() != 1 || !telefoneCompativel(trecho, lead.telefoneNormalizado())) continue;
                URI perfil = referencias.iterator().next();
                GoogleResultadoWeb candidato = perfis.get(perfil);
                if (candidato == null) continue;
                var endereco = AnalisadorEnderecoPesquisa.analisar(lead, trecho);
                if (!endereco.numeroCompativel() || endereco.conflito()
                    || conflitoLocalizacao(trecho, lead) || possuiConflitoCnpj(trecho, lead.cnpj())) continue;
                // A referência explícita, o endereço e o telefone pertencem à mesma ocorrência pública.
                // O título permanece o do perfil; a fonte não é transformada em site próprio do lead.
                enriquecidos.add(new GoogleResultadoWeb(perfil, candidato.titulo(), trecho));
            }
        }
        return enriquecidos;
    }

    private Set<URI> referenciasInstagram(String trecho) {
        Set<URI> referencias = new LinkedHashSet<>();
        Matcher rotulados = INSTAGRAM_ROTULADO.matcher(trecho);
        while (rotulados.find()) {
            canonicalizer.canonicalizar(URI.create("https://www.instagram.com/" + rotulados.group(1)),
                TipoPesquisaWeb.INSTAGRAM).ifPresent(referencias::add);
        }
        Matcher links = INSTAGRAM_LINK.matcher(trecho);
        while (links.find()) {
            try {
                canonicalizer.canonicalizar(URI.create(links.group()), TipoPesquisaWeb.INSTAGRAM)
                    .ifPresent(referencias::add);
            } catch (IllegalArgumentException ignored) {
                // Texto externo malformado não interrompe a classificação dos demais candidatos.
            }
        }
        return referencias;
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

        // Conflitos pertencem à mesma página/perfil. Uma lista nacional não invalida a página da filial.
        Map<String, Avaliacao> melhoresPorSite = new HashMap<>();
        for (Avaliacao avaliacao : melhoresPorDestino.values()) {
            if (!avaliacao.elegivel()) continue;
            String chave = tipo == TipoPesquisaWeb.SITE_PROPRIO
                ? avaliacao.url().getHost().replaceFirst("^www\\.", "") : avaliacao.url().toString();
            melhoresPorSite.merge(chave, avaliacao, this::melhor);
        }
        List<Avaliacao> ordenadas = melhoresPorSite.values().stream()
            .filter(Avaliacao::elegivel)
            .sorted(Comparator.comparingInt(Avaliacao::pontuacao).reversed()
                .thenComparing(avaliacao -> avaliacao.url().toString()))
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
        Set<String> tokensDominio = tipo == TipoPesquisaWeb.SITE_PROPRIO
            ? tokensMarca(nome, lead.municipio(), tokensDistintivos) : tokensDistintivos;
        Set<String> tokensEvidencia = tokens(evidencia);

        int tokensCorrespondentes = intersecao(tokensNome, tokensEvidencia);
        double proporcaoNome = tokensNome.isEmpty() ? 0 : (double) tokensCorrespondentes / tokensNome.size();
        boolean nomeExato = contemFrase(evidencia, nome);
        int pontosNome = pontosNome(nomeExato, tokensCorrespondentes, proporcaoNome);

        String identificador = identificador(url, tipo);
        String identificadorCompacto = normalizar(identificador).replace(" ", "");
        String nomeCompacto = nome.replace(" ", "");
        boolean identificadorAproximado = tipo == TipoPesquisaWeb.INSTAGRAM
            && nomeCompacto.length() >= 8 && distanciaLimitada(nomeCompacto, identificadorCompacto, 2);
        boolean identificadorForte = !tokensDominio.isEmpty()
            && tokensDominio.stream().allMatch(identificadorCompacto::contains);
        int pontosIdentificador = identificadorForte
            ? 25
            : tokensDominio.stream().anyMatch(identificadorCompacto::contains) ? 10 : 0;
        // Handle de filial costuma combinar a marca com a praça (ex.: marca + município).
        boolean ramoConfirmado = tipo == TipoPesquisaWeb.INSTAGRAM
            && identificadorDeRamo(lead, identificadorCompacto, tokensDistintivos);
        int pontosRamo = ramoConfirmado ? PONTOS_RAMO_IDENTIFICADOR : 0;

        // "Castelo" em "Supermercado Castelo" não é uma confirmação de município.
        String contextoLocal = normalizar(resultado.resumo()).replace(nome, " ");
        String municipio = normalizar(lead.municipio());
        if (!municipio.isBlank()) {
            contextoLocal = contextoLocal.replaceAll(
                "\\b(?:bairro|rua|avenida|rodovia|r|av) " + Pattern.quote(municipio) + "\\b", " ");
        }
        String uf = normalizar(lead.uf());
        boolean municipioCompativel = localizacaoCompativel(contextoLocal, municipio, uf);
        var endereco = AnalisadorEnderecoPesquisa.analisar(lead, evidenciaOriginal);
        int pontosLocalizacao = (municipioCompativel ? 20 : 0) + endereco.pontuacao();
        int pontosCategoria = contemAlgum(evidencia, termosCategoria(lead.categoria())) ? 10 : 0;
        int pontosTelefone = telefoneCompativel(evidenciaOriginal, lead.telefoneNormalizado()) ? 30 : 0;
        int pontosCnpj = cnpjCompativel(evidenciaOriginal, lead.cnpj()) ? 40 : 0;
        int pontosPlaceId = contemLiteral(evidenciaOriginal, lead.googlePlaceId()) ? 35 : 0;
        int pontosRazaoSocial = contemFrase(evidencia, normalizar(lead.razaoSocial())) ? 15 : 0;

        boolean conflitoCnpj = possuiConflitoCnpj(evidenciaOriginal, lead.cnpj());
        boolean marcaCompativel = !tokensDistintivos.isEmpty()
            && tokensDistintivos.stream().allMatch(token -> tokensEvidencia.contains(token)
                || identificadorCompacto.contains(token));
        boolean nomeForte = (nomeExato || (tokensCorrespondentes >= 2 && proporcaoNome >= 0.75))
            && tokensDistintivos.stream().allMatch(token -> tokensEvidencia.contains(token)
                || identificadorCompacto.contains(token));
        boolean identificadorExternoForte = pontosTelefone > 0 || pontosCnpj > 0 || pontosPlaceId > 0;
        boolean nomeAlternativoConfirmado = tipo == TipoPesquisaWeb.INSTAGRAM
            && marcaCompativel && identificadorExternoForte;
        boolean conflitoLocalizacao = conflitoLocalizacao(evidenciaOriginal, lead);
        boolean corroborado = municipioCompativel || endereco.numeroCompativel() || identificadorExternoForte
            || ramoConfirmado;
        Set<String> tokensIdentidade = tokensDominio.isEmpty() ? tokensNome : tokensDominio;
        boolean identificadorRelacionado = !tokensIdentidade.isEmpty()
            && tokensIdentidade.stream().allMatch(identificadorCompacto::contains);
        // Identificador abreviado só é aceito com confirmação forte (telefone, CNPJ ou Place ID).
        boolean destinoRelacionado = identificadorRelacionado
            || (tipo == TipoPesquisaWeb.INSTAGRAM && (identificadorExternoForte || ramoConfirmado
                || (nomeExato && identificadorAproximado && municipioCompativel && endereco.numeroProximo())));
        boolean conflito = conflitoCnpj || conflitoLocalizacao || endereco.conflitoLogradouro()
            || (pontosCnpj == 0 && pontosPlaceId == 0 && conflitoDdd(evidenciaOriginal, lead.telefoneNormalizado()));
        boolean enderecoCorroborado = tipo == TipoPesquisaWeb.INSTAGRAM
            && ((pontosTelefone > 0 && endereco.logradouroCompativel() && municipioCompativel
                && enderecoCorroboradoPorTelefone(lead, resultado.resumo()))
                || (nomeExato && identificadorAproximado && municipioCompativel && endereco.numeroProximo()));

        int pontuacao = pontosNome
            + pontosIdentificador
            + pontosRamo
            + pontosLocalizacao
            + pontosCategoria
            + pontosTelefone
            + pontosCnpj
            + pontosPlaceId
            + pontosRazaoSocial;
        boolean identidadeCompativel = (nomeForte || nomeAlternativoConfirmado || ramoConfirmado)
            && corroborado
            && destinoRelacionado;
        return new Avaliacao(url, pontuacao, identidadeCompativel, conflito, nomeExato || marcaCompativel,
            endereco.conflitoNumero(), enderecoCorroborado);
    }

    /**
     * Handle de filial costuma juntar a marca a um discriminante da praça, como o município
     * (ex.: marca + "Castelo" -> "marcacastelo"). Exige uma sequência contígua de ao menos dois
     * termos distintivos do nome dentro do handle, contendo ao mesmo tempo um termo do município
     * (a praça) e um termo que não venha dele (a marca). Assim, handles que são apenas a própria
     * cidade, ou apenas um nome genérico de lugar, não confirmam o ramo.
     */
    private boolean identificadorDeRamo(
        PesquisaLeadDados lead,
        String identificadorCompacto,
        Set<String> tokensDistintivos
    ) {
        if (tokensDistintivos.size() < 2) {
            return false;
        }
        Set<String> doMunicipio = tokens(normalizar(lead.municipio()));
        if (doMunicipio.isEmpty()) {
            return false;
        }
        List<String> tokens = List.copyOf(tokensDistintivos);
        for (int inicio = 0; inicio < tokens.size(); inicio++) {
            StringBuilder concatenado = new StringBuilder(tokens.get(inicio));
            boolean contemMarca = !doMunicipio.contains(tokens.get(inicio));
            boolean contemPraca = doMunicipio.contains(tokens.get(inicio));
            for (int fim = inicio + 1; fim < tokens.size(); fim++) {
                String token = tokens.get(fim);
                contemMarca |= !doMunicipio.contains(token);
                contemPraca |= doMunicipio.contains(token);
                concatenado.append(token);
                if (contemMarca && contemPraca && identificadorCompacto.contains(concatenado.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean enderecoCorroboradoPorTelefone(PesquisaLeadDados lead, String resumo) {
        if (resumo == null) return false;
        // Uma diferença no número do imóvel exige telefone, logradouro e município no mesmo trecho.
        for (String trecho : resumo.split("(?:\\R|\\*{3,}|\\.{3,}|…)+")) {
            if (!telefoneCompativel(trecho, lead.telefoneNormalizado())
                || !contemFrase(normalizar(trecho), normalizar(lead.municipio()))) continue;
            var endereco = AnalisadorEnderecoPesquisa.analisar(lead, trecho);
            if (endereco.logradouroCompativel() && !endereco.conflitoLogradouro()) return true;
        }
        return false;
    }

    private Avaliacao consolidar(Avaliacao atual, Avaliacao nova) {
        Avaliacao melhor = atual.elegivel() != nova.elegivel() ? (atual.elegivel() ? atual : nova)
            : melhor(atual, nova);
        boolean conflito = atual.conflito() || nova.conflito();
        return new Avaliacao(melhor.url(), melhor.pontuacao(), melhor.identidadeCompativel(), conflito,
            atual.nomeRelacionado() || nova.nomeRelacionado(), atual.numeroDivergente() || nova.numeroDivergente(),
            atual.enderecoCorroborado() || nova.enderecoCorroborado());
    }

    private Avaliacao melhor(Avaliacao atual, Avaliacao nova) {
        if (nova.pontuacao() != atual.pontuacao()) return nova.pontuacao() > atual.pontuacao() ? nova : atual;
        return nova.url().toString().compareTo(atual.url().toString()) < 0 ? nova : atual;
    }

    private Set<String> tokensMarca(String nome, String municipio, Set<String> originais) {
        String local = normalizar(municipio);
        if (!contemFrase(nome, local)) return originais;
        // Retira somente o município completo; a identidade e a confirmação da filial continuam obrigatórias.
        String marca = nome.replaceAll("(?<![a-z0-9])" + Pattern.quote(local) + "(?![a-z0-9])", " ");
        Set<String> distintivos = tokensDistintivos(tokens(marca));
        return distintivos.isEmpty() ? originais : distintivos;
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

    private boolean localizacaoCompativel(String contexto, String municipio, String uf) {
        if (municipio.isBlank()) return false;
        return (!uf.isBlank() && contemFrase(contexto, municipio + " " + uf))
            || contexto.equals(municipio)
            || contemFrase(contexto, "em " + municipio)
            || contemFrase(contexto, "centro de " + municipio);
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
        if ((digitos.length() == 11 || digitos.length() == 12) && digitos.startsWith("0")) {
            return digitos.substring(1);
        }
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
        // "Supermercados", "Farmacias", "Acougues": a flexão de número também é genérica.
        resultado.removeIf(this::termoGenerico);
        resultado.removeIf(token -> token.length() < 3);
        return resultado;
    }

    private boolean termoGenerico(String token) {
        if (TERMOS_GENERICOS.contains(token)) {
            return true;
        }
        if (token.endsWith("ns")) {
            return TERMOS_GENERICOS.contains(token.substring(0, token.length() - 2) + "m");
        }
        if (token.endsWith("s")) {
            return TERMOS_GENERICOS.contains(token.substring(0, token.length() - 1));
        }
        return false;
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

    private boolean distanciaLimitada(String esperado, String encontrado, int limite) {
        if (Math.abs(esperado.length() - encontrado.length()) > limite) return false;
        int[] anterior = java.util.stream.IntStream.rangeClosed(0, encontrado.length()).toArray();
        for (int i = 1; i <= esperado.length(); i++) {
            int[] atual = new int[encontrado.length() + 1];
            atual[0] = i;
            int menor = atual[0];
            for (int j = 1; j <= encontrado.length(); j++) {
                int substituicao = anterior[j - 1]
                    + (esperado.charAt(i - 1) == encontrado.charAt(j - 1) ? 0 : 1);
                atual[j] = Math.min(Math.min(anterior[j] + 1, atual[j - 1] + 1), substituicao);
                menor = Math.min(menor, atual[j]);
            }
            if (menor > limite) return false;
            anterior = atual;
        }
        return anterior[encontrado.length()] <= limite;
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

    private String juntar(String... valores) {
        List<String> presentes = new ArrayList<>();
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                presentes.add(valor);
            }
        }
        return String.join("\n", presentes);
    }

    private record Avaliacao(URI url, int pontuacao, boolean identidadeCompativel, boolean conflito,
                             boolean nomeRelacionado, boolean numeroDivergente, boolean enderecoCorroborado) {
        boolean elegivel() {
            return identidadeCompativel && !conflito && (!numeroDivergente || enderecoCorroborado);
        }
    }
}
