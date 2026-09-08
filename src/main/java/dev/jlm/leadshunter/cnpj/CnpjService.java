package dev.jlm.leadshunter.cnpj;

import dev.jlm.leadshunter.lead.Lead;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CnpjService {

    static final String SITUACAO_ATIVA = "02";
    static final int MAXIMO_CANDIDATOS = 200;
    static final double LIMIAR_COM_CEP = 0.82;
    static final double LIMIAR_SEM_CEP = 0.90;
    static final double MINIMO_LOGRADOURO = 0.78;
    static final double MINIMO_NOME = 0.45;

    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}+");
    private static final Pattern NAO_ALFANUMERICO = Pattern.compile("[^0-9a-z]+");
    private static final Pattern NAO_ALFANUMERICO_MAIUSCULO = Pattern.compile("[^0-9A-Z]+");
    private static final Set<String> TERMOS_JURIDICOS = Set.of(
        "comercio", "comercial", "alimento", "alimentos", "alimentacao",
        "restaurante", "restaurantes", "servico", "servicos", "ltda", "limitada",
        "sa", "s", "a", "e", "de", "da", "do", "das", "dos"
    );

    private final CnpjEstabelecimentoRepository estabelecimentoRepository;

    public CnpjService(CnpjEstabelecimentoRepository estabelecimentoRepository) {
        this.estabelecimentoRepository = estabelecimentoRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Correspondencia> corresponder(Lead lead) {
        if (lead == null || !codigoMunicipioValido(lead.getMunicipioCodigoIbge())) {
            return Optional.empty();
        }

        String logradouro = normalizarLogradouro(lead.getLogradouro());
        String numero = normalizarNumero(lead.getNumero());
        String nome = normalizarNome(lead.getNome());
        if (logradouro.isEmpty() || numero == null || nome.isEmpty()) {
            return Optional.empty();
        }

        String cep = normalizarCep(lead.getCep());
        boolean comCep = cep != null;
        PageRequest limite = PageRequest.of(0, MAXIMO_CANDIDATOS);
        Slice<CnpjEstabelecimento> candidatos = comCep
            ? estabelecimentoRepository
                .findByMunicipioCodigoIbgeAndSituacaoCadastralAndCep(
                    lead.getMunicipioCodigoIbge(),
                    SITUACAO_ATIVA,
                    cep,
                    limite
                )
            : estabelecimentoRepository
                .findByMunicipioCodigoIbgeAndSituacaoCadastralAndNumero(
                    lead.getMunicipioCodigoIbge(),
                    SITUACAO_ATIVA,
                    numero,
                    limite
                );

        if (candidatos.hasNext()) {
            return Optional.empty();
        }

        List<CandidatoPontuado> aprovados = candidatos.getContent().stream()
            .map(candidato -> pontuar(candidato, nome, logradouro, numero, lead.getBairro(), comCep))
            .flatMap(Optional::stream)
            .toList();
        if (aprovados.size() != 1) {
            return Optional.empty();
        }

        CnpjEstabelecimento escolhido = aprovados.getFirst().estabelecimento();
        return Optional.of(new Correspondencia(
            escolhido.getCnpj(),
            escolhido.getEmpresa().getRazaoSocial()
        ));
    }

    private Optional<CandidatoPontuado> pontuar(
        CnpjEstabelecimento candidato,
        String nomeLead,
        String logradouroLead,
        String numeroLead,
        String bairroLead,
        boolean comCep
    ) {
        String numeroCandidato = normalizarNumero(candidato.getNumero());
        if (!numeroLead.equals(numeroCandidato)) {
            return Optional.empty();
        }

        double logradouro = similaridade(
            logradouroLead,
            normalizarLogradouro(candidato.getLogradouroNormalizado())
        );
        double nome = Math.max(
            similaridadeNome(nomeLead, candidato.getNomeFantasiaNormalizado()),
            similaridadeNome(nomeLead, candidato.getEmpresa().getRazaoSocialNormalizada())
        );
        if (logradouro < MINIMO_LOGRADOURO || nome < MINIMO_NOME) {
            return Optional.empty();
        }

        double bairro = similaridade(
            normalizarTexto(bairroLead),
            normalizarTexto(candidato.getBairroNormalizado())
        );
        double pontuacao = comCep
            ? 0.40 * logradouro + 0.20 + 0.30 * nome + 0.10 * bairro
            : 0.45 * logradouro + 0.25 + 0.25 * nome + 0.05 * bairro;
        double limiar = comCep ? LIMIAR_COM_CEP : LIMIAR_SEM_CEP;
        return pontuacao >= limiar
            ? Optional.of(new CandidatoPontuado(candidato, pontuacao))
            : Optional.empty();
    }

    static String normalizarTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        String decomposto = Normalizer.normalize(valor, Normalizer.Form.NFKD);
        String semAcentos = MARCAS_DIACRITICAS.matcher(decomposto).replaceAll("");
        String minusculo = semAcentos.toLowerCase(Locale.ROOT);
        return NAO_ALFANUMERICO.matcher(minusculo).replaceAll(" ").trim()
            .replaceAll("\\s+", " ");
    }

    static String normalizarLogradouro(String valor) {
        List<String> tokens = new ArrayList<>(Arrays.asList(normalizarTexto(valor).split(" ")));
        if (tokens.isEmpty() || tokens.getFirst().isEmpty()) {
            return "";
        }
        tokens.set(0, switch (tokens.getFirst()) {
            case "r" -> "rua";
            case "av" -> "avenida";
            case "rod" -> "rodovia";
            case "pc", "pca" -> "praca";
            default -> tokens.getFirst();
        });
        return String.join(" ", tokens);
    }

    static String normalizarNome(String valor) {
        return String.join(" ", tokensNome(valor));
    }

    static String normalizarNumero(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String numero = NAO_ALFANUMERICO_MAIUSCULO.matcher(
            valor.toUpperCase(Locale.ROOT)
        ).replaceAll("");
        return numero.isEmpty() || Set.of("SN", "SEMNUMERO").contains(numero)
            ? null
            : numero;
    }

    static String normalizarCep(String valor) {
        if (valor == null) {
            return null;
        }
        String cep = valor.replaceAll("\\D", "");
        return cep.length() == 8 ? cep : null;
    }

    static double similaridade(String primeiro, String segundo) {
        if (primeiro == null || segundo == null || primeiro.isEmpty() || segundo.isEmpty()) {
            return 0;
        }
        int[] anterior = new int[segundo.length() + 1];
        for (int coluna = 0; coluna <= segundo.length(); coluna++) {
            anterior[coluna] = coluna;
        }
        for (int linha = 1; linha <= primeiro.length(); linha++) {
            int[] atual = new int[segundo.length() + 1];
            atual[0] = linha;
            for (int coluna = 1; coluna <= segundo.length(); coluna++) {
                int custo = primeiro.charAt(linha - 1) == segundo.charAt(coluna - 1) ? 0 : 1;
                atual[coluna] = Math.min(
                    Math.min(atual[coluna - 1] + 1, anterior[coluna] + 1),
                    anterior[coluna - 1] + custo
                );
            }
            anterior = atual;
        }
        return 1.0 - (double) anterior[segundo.length()]
            / Math.max(primeiro.length(), segundo.length());
    }

    static double similaridadeNome(String primeiro, String segundo) {
        List<String> tokensPrimeiro = tokensNome(primeiro);
        List<String> tokensSegundo = tokensNome(segundo);
        if (tokensPrimeiro.isEmpty() || tokensSegundo.isEmpty()) {
            return 0;
        }
        List<String> segundoExpandido = expandirSiglas(tokensSegundo, tokensPrimeiro);
        List<String> primeiroExpandido = expandirSiglas(tokensPrimeiro, segundoExpandido);
        String nomePrimeiro = String.join(" ", primeiroExpandido);
        String nomeSegundo = String.join(" ", segundoExpandido);
        return Math.max(
            similaridade(nomePrimeiro, nomeSegundo),
            similaridadeJaccard(primeiroExpandido, segundoExpandido)
        );
    }

    private static List<String> tokensNome(String valor) {
        return Arrays.stream(normalizarTexto(valor).split(" "))
            .filter(token -> !token.isEmpty() && !TERMOS_JURIDICOS.contains(token))
            .toList();
    }

    private static List<String> expandirSiglas(
        List<String> tokens,
        List<String> referencia
    ) {
        List<String> expandidos = new ArrayList<>();
        for (String token : tokens) {
            List<String> expansao = localizarExpansao(token, referencia);
            if (expansao.isEmpty()) {
                expandidos.add(token);
            } else {
                expandidos.addAll(expansao);
            }
        }
        return expandidos;
    }

    private static List<String> localizarExpansao(String token, List<String> referencia) {
        if (token.length() < 2 || token.length() > 4) {
            return List.of();
        }
        for (int inicio = 0; inicio + token.length() <= referencia.size(); inicio++) {
            StringBuilder iniciais = new StringBuilder();
            for (int indice = inicio; indice < inicio + token.length(); indice++) {
                iniciais.append(referencia.get(indice).charAt(0));
            }
            if (iniciais.toString().equals(token)) {
                return referencia.subList(inicio, inicio + token.length());
            }
        }
        return List.of();
    }

    private static double similaridadeJaccard(List<String> primeiro, List<String> segundo) {
        Set<String> conjuntoPrimeiro = new HashSet<>(primeiro);
        Set<String> conjuntoSegundo = new HashSet<>(segundo);
        Set<String> intersecao = new HashSet<>(conjuntoPrimeiro);
        intersecao.retainAll(conjuntoSegundo);
        Set<String> uniao = new HashSet<>(conjuntoPrimeiro);
        uniao.addAll(conjuntoSegundo);
        return uniao.isEmpty() ? 0 : (double) intersecao.size() / uniao.size();
    }

    private boolean codigoMunicipioValido(String valor) {
        return valor != null && valor.matches("\\d{7}");
    }

    public record Correspondencia(String cnpj, String razaoSocial) {
    }

    private record CandidatoPontuado(
        CnpjEstabelecimento estabelecimento,
        double pontuacao
    ) {
    }
}
