package dev.jlm.leadshunter.integracao.pesquisa;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Compara endereço por ocorrência: um número distante não confirma um logradouro. */
final class AnalisadorEnderecoPesquisa {

    private static final Pattern ACENTOS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARADORES = Pattern.compile("[^a-z0-9]+");
    private static final String NUMERO = "\\d+(?:[ -]?[a-z](?![a-z]))?";
    private static final Pattern ENDERECO_FORMATADO = Pattern.compile(
        "(?iu)^([^,\\r\\n]+),\\s*(?:n[º°.]?\\s*|numero\\s+)?(" + NUMERO + ")(?![a-z0-9])"
    );
    private static final Pattern ENDERECO_EXPLICITO = Pattern.compile(
        "(?iu)\\b((?:r|rua|av|avda|avenida|rod|rodovia|est|estr|estrada|tv|trav|travessa|al|alameda|pc|pç|pca|praça)"
            + "\\.? +[\\p{L}\\d .’'-]{1,120}),\\s*(?:n[º°.]?\\s*|numero\\s+)?(" + NUMERO + ")(?![a-z0-9])"
    );
    private static final Map<String, String> TIPOS = Map.ofEntries(
        Map.entry("r", "rua"), Map.entry("av", "avenida"), Map.entry("avda", "avenida"),
        Map.entry("rod", "rodovia"), Map.entry("est", "estrada"), Map.entry("estr", "estrada"),
        Map.entry("tv", "travessa"), Map.entry("trav", "travessa"), Map.entry("al", "alameda"),
        Map.entry("pc", "praca"), Map.entry("pca", "praca")
    );

    private AnalisadorEnderecoPesquisa() {}

    static Evidencia analisar(PesquisaLeadDados lead, String texto) {
        var formatado = ENDERECO_FORMATADO.matcher(lead.enderecoFormatado() == null ? "" : lead.enderecoFormatado());
        boolean possuiFormatado = formatado.find();
        String rua = normalizarLogradouro(primeiroNaoVazio(lead.logradouro(), possuiFormatado ? formatado.group(1) : ""));
        String numero = normalizarNumero(primeiroNaoVazio(lead.numero(), possuiFormatado ? formatado.group(2) : ""));
        if (!rua.contains(" ")) return new Evidencia(false, false, false, false, false);

        String expressaoRua = "\\b" + expressaoLogradouro(rua);
        Pattern ruaSemNumero = Pattern.compile(expressaoRua + "\\b");
        Pattern ruaComNumero = Pattern.compile(expressaoRua
            + "(?:\\s*[,\\-]\\s*|\\s+)(?:(?:n[º°.]?|no|numero)\\s+)?(" + NUMERO + ")(?![a-z0-9])");
        boolean ruaCompativel = false;
        boolean numeroCompativel = false;
        boolean numeroProximo = false;
        boolean conflitoLogradouro = false;
        boolean conflitoNumero = false;
        // Cada trecho do Brave é independente; não juntar rua de um trecho com número de outro.
        for (String trecho : texto.lines().toList()) {
            String normalizado = semAcentos(trecho).toLowerCase(Locale.ROOT);
            ruaCompativel |= ruaSemNumero.matcher(normalizado).find();
            if (numero.isEmpty()) continue;
            var ocorrencias = ruaComNumero.matcher(normalizado);
            while (ocorrencias.find()) {
                boolean mesmoNumero = normalizarNumero(ocorrencias.group(1)).equals(numero);
                numeroCompativel |= mesmoNumero;
                numeroProximo |= numerosProximos(normalizarNumero(ocorrencias.group(1)), numero);
                conflitoNumero |= !mesmoNumero;
            }
            var enderecos = ENDERECO_EXPLICITO.matcher(trecho);
            while (enderecos.find()) {
                conflitoLogradouro |= !normalizarLogradouro(enderecos.group(1)).equals(rua);
                conflitoNumero |= !normalizarNumero(enderecos.group(2)).equals(numero);
            }
        }
        return new Evidencia(ruaCompativel, numeroCompativel, numeroProximo, conflitoLogradouro, conflitoNumero);
    }

    private static String normalizar(String valor) {
        return SEPARADORES.matcher(semAcentos(valor).toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    private static String semAcentos(String valor) {
        return ACENTOS.matcher(Normalizer.normalize(valor, Normalizer.Form.NFD)).replaceAll("");
    }

    private static String normalizarLogradouro(String valor) {
        String texto = normalizar(valor);
        int espaco = texto.indexOf(' ');
        if (espaco < 0) return texto;
        String tipo = texto.substring(0, espaco);
        return TIPOS.getOrDefault(tipo, tipo) + texto.substring(espaco);
    }

    private static String expressaoLogradouro(String rua) {
        int espaco = rua.indexOf(' ');
        String tipo = rua.substring(0, espaco);
        var variantes = new ArrayList<String>();
        variantes.add(Pattern.quote(tipo));
        TIPOS.forEach((abreviacao, completo) -> {
            if (completo.equals(tipo)) variantes.add(Pattern.quote(abreviacao));
        });
        String nome = java.util.Arrays.stream(rua.substring(espaco + 1).split(" "))
            .map(Pattern::quote).collect(java.util.stream.Collectors.joining("[\\s.’'-]+"));
        return "(?:" + String.join("|", variantes) + ")\\.?\\s+" + nome;
    }

    private static String normalizarNumero(String valor) {
        String numero = valor.toLowerCase(Locale.ROOT).replaceAll("[ -]", "");
        return numero.matches("\\d+[a-z]?") ? numero.replaceFirst("^0+(?=\\d)", "") : "";
    }

    private static boolean numerosProximos(String encontrado, String esperado) {
        if (!encontrado.matches("\\d+") || !esperado.matches("\\d+")) return false;
        try {
            return Math.abs(Long.parseLong(encontrado) - Long.parseLong(esperado)) <= 2;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static String primeiroNaoVazio(String primeiro, String segundo) {
        return primeiro != null && !primeiro.isBlank() ? primeiro.strip() : segundo;
    }

    record Evidencia(boolean logradouroCompativel, boolean numeroCompativel, boolean numeroProximo,
                     boolean conflitoLogradouro, boolean conflitoNumero) {
        boolean conflito() {
            return conflitoLogradouro || conflitoNumero;
        }

        int pontuacao() {
            return logradouroCompativel ? (numeroCompativel ? 20 : 10) : 0;
        }
    }
}
