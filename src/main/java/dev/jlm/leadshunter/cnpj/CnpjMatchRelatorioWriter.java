package dev.jlm.leadshunter.cnpj;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Writes the generated JSONL and the separate human review CSV. */
@Component
public class CnpjMatchRelatorioWriter {

    public static final String SCHEMA = "cnpj-match-report-v1";

    private static final String CABECALHO = "cabecalho";
    private static final String LINHA = "lead";
    private static final String CSV_CABECALHO =
        "leadId,cnpj,competencia,classificacao,revisao,evidenciaUrl,evidencia,hashRelatorio\n";

    private final ObjectMapper objectMapper;

    public CnpjMatchRelatorioWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RelatorioGerado escrever(
        Path caminhoJsonl,
        Path caminhoRevisao,
        List<CnpjMatchRelatorioLinha> linhas,
        CnpjMatchPolicy policy
    ) {
        return escrever(caminhoJsonl, caminhoRevisao, linhas, List.of(), policy);
    }

    public RelatorioGerado escrever(
        Path caminhoJsonl,
        Path caminhoRevisao,
        List<CnpjMatchRelatorioLinha> linhas,
        List<CnpjNumeroNormalizer.NumeroDescartado> numerosDescartados,
        CnpjMatchPolicy policy
    ) {
        Objects.requireNonNull(caminhoJsonl, "caminhoJsonl");
        Objects.requireNonNull(caminhoRevisao, "caminhoRevisao");
        Objects.requireNonNull(numerosDescartados, "numerosDescartados");
        try {
            if (Files.exists(caminhoJsonl) || Files.exists(caminhoRevisao)) {
                throw new IllegalStateException("Artefato de relatório já existe; use novos caminhos");
            }
            Files.createDirectories(parent(caminhoJsonl));
            Files.createDirectories(parent(caminhoRevisao));

            List<LocalDate> competencias = linhas.stream()
                .map(CnpjMatchRelatorioLinha::competencia)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
            Map<String, Object> cabecalho = new LinkedHashMap<>();
            cabecalho.put("tipo", CABECALHO);
            cabecalho.put("schema", SCHEMA);
            cabecalho.put("competencias", competencias);
            cabecalho.put("versaoNormalizador", CnpjNumeroNormalizer.VERSAO);
            cabecalho.put("versaoNormalizadorLegado", CnpjNumeroNormalizer.VERSAO_LEGADO);
            cabecalho.put("fingerprintAllowlist", policy.fingerprint());
            cabecalho.put("numerosDescartados", numerosDescartados);

            Path temporarioJsonl = Files.createTempFile(parent(caminhoJsonl), ".cnpj-match-", ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(
                temporarioJsonl,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING
            )) {
                writer.write(objectMapper.writeValueAsString(cabecalho));
                writer.write('\n');
                for (CnpjMatchRelatorioLinha linha : linhas) {
                    writer.write(objectMapper.writeValueAsString(linha));
                    writer.write('\n');
                }
            }
            moverNovo(temporarioJsonl, caminhoJsonl);
            String hash = sha256(caminhoJsonl);
            escreverRevisao(caminhoRevisao, linhas, hash);
            return new RelatorioGerado(caminhoJsonl, caminhoRevisao, hash, linhas.size());
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível escrever o relatório CNPJ", exception);
        }
    }

    private void escreverRevisao(
        Path caminho,
        List<CnpjMatchRelatorioLinha> linhas,
        String hash
    ) throws IOException {
        Path temporario = Files.createTempFile(parent(caminho), ".cnpj-match-review-", ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(
            temporario,
            StandardCharsets.UTF_8,
            StandardOpenOption.TRUNCATE_EXISTING
        )) {
            writer.write(CSV_CABECALHO);
            for (CnpjMatchRelatorioLinha linha : linhas) {
                if (linha.cnpj() == null || !linha.politicaPermitida()
                    || linha.resultadoNovo() == null) {
                    continue;
                }
                writer.write(csvLinha(List.of(
                    String.valueOf(linha.leadId()),
                    linha.cnpj(),
                    String.valueOf(linha.competencia()),
                    linha.classificacao().name(),
                    "",
                    "",
                    "",
                    hash
                )));
                writer.write('\n');
            }
        }
        moverNovo(temporario, caminho);
    }

    private static String csvLinha(List<String> valores) {
        return valores.stream().map(CnpjMatchRelatorioWriter::csv).collect(Collectors.joining(","));
    }

    private static String csv(String valor) {
        String seguro = valor == null ? "" : valor;
        return '"' + seguro.replace("\"", "\"\"") + '"';
    }

    private static Path parent(Path caminho) {
        Path parent = caminho.toAbsolutePath().getParent();
        return parent == null ? Path.of(".").toAbsolutePath() : parent;
    }

    private static void moverNovo(Path temporario, Path destino) throws IOException {
        try {
            Files.move(temporario, destino, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporario, destino);
        }
    }

    public static String sha256(Path caminho) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(caminho));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | IOException exception) {
            throw new IllegalStateException("Não foi possível calcular SHA-256", exception);
        }
    }

    public record RelatorioGerado(
        Path caminhoJsonl,
        Path caminhoRevisao,
        String hashSha256,
        int linhas
    ) {
    }
}
