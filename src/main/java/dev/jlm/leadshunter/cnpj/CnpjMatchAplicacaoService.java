package dev.jlm.leadshunter.cnpj;

import dev.jlm.leadshunter.lead.Lead;
import dev.jlm.leadshunter.lead.LeadRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Applies only an explicitly reviewed, hash-bound report. */
@Service
@RequiredArgsConstructor
public class CnpjMatchAplicacaoService {

    private static final Set<CnpjMatchClassificacao> CLASSIFICACOES_APROVADAS = Set.of(
        CnpjMatchClassificacao.ENDERECO_UNICO,
        CnpjMatchClassificacao.ENDERECO_DESEMPATADO_POR_NOME,
        CnpjMatchClassificacao.NOME_RESOLVE
    );
    private static final String CABECALHO_REVIEW =
        "leadId,cnpj,competencia,classificacao,revisao,evidenciaUrl,evidencia,hashRelatorio";

    private final LeadRepository leadRepository;
    private final CnpjService cnpjService;
    private final CnpjMatchPolicy policy;
    private final ObjectMapper objectMapper;

    @Transactional
    public ResultadoAplicacao aplicar(Path caminhoJsonl, Path caminhoRevisao, int limite) {
        if (limite < 1 || limite > CnpjMatchDiagnosticoService.LIMITE_PADRAO) {
            throw new IllegalArgumentException(
                "limite deve estar entre 1 e " + CnpjMatchDiagnosticoService.LIMITE_PADRAO
            );
        }
        String hash = CnpjMatchRelatorioWriter.sha256(caminhoJsonl);
        List<EntradaRelatorio> relatorio = lerRelatorio(caminhoJsonl);
        List<EntradaRevisao> revisao = lerRevisao(caminhoRevisao, hash);
        Map<String, EntradaRelatorio> porChave = new HashMap<>();
        for (EntradaRelatorio entrada : relatorio) {
            if (porChave.put(chave(entrada.leadId(), entrada.cnpj()), entrada) != null) {
                throw new IllegalArgumentException("Lead/CNPJ duplicado no relatório JSONL");
            }
        }
        Map<String, EntradaRevisao> revisaoPorChave = new HashMap<>();
        for (EntradaRevisao entrada : revisao) {
            revisaoPorChave.put(chave(entrada.leadId(), entrada.cnpj()), entrada);
        }
        List<EntradaRelatorio> aprovacoesObrigatorias = relatorio.stream()
            .filter(CnpjMatchAplicacaoService::exigeRevisao)
            .toList();
        ResumoRevisao resumoRevisao = resumirRevisao(
            aprovacoesObrigatorias,
            revisaoPorChave
        );
        boolean possuiErrado = revisao.stream()
            .anyMatch(entrada -> "ERRADO".equals(entrada.revisao()));
        if (possuiErrado || resumoRevisao.pendentes() > 0) {
            return new ResultadoAplicacao(
                itensPromocaoVetada(aprovacoesObrigatorias, revisaoPorChave),
                hash,
                resumoRevisao
            );
        }

        List<ItemAplicado> resultados = new ArrayList<>();
        int autorizados = 0;
        for (EntradaRevisao entrada : revisao) {
            EntradaRelatorio linha = porChave.get(chave(entrada.leadId(), entrada.cnpj()));
            if (!entrada.revisao().equals("CERTO")) {
                resultados.add(new ItemAplicado(
                    entrada.leadId(), entrada.cnpj(), StatusAplicacao.REJEITADO_REVISAO_NAO_CERTO
                ));
                continue;
            }
            if (vazio(entrada.evidenciaUrl()) && vazio(entrada.evidencia())) {
                resultados.add(new ItemAplicado(
                    entrada.leadId(), entrada.cnpj(),
                    StatusAplicacao.REJEITADO_REVISAO_SEM_EVIDENCIA
                ));
                continue;
            }
            if (linha == null || !linha.politicaPermitida()
                || !CLASSIFICACOES_APROVADAS.contains(linha.classificacao())
                || entrada.classificacao() != linha.classificacao()
                || !entrada.cnpj().equals(linha.resultadoNovo())
                || entrada.competencia() == null
                || !entrada.competencia().equals(linha.competencia())) {
                resultados.add(new ItemAplicado(
                    entrada.leadId(), entrada.cnpj(), StatusAplicacao.REJEITADO_LINHA_NAO_AUTORIZADA
                ));
                continue;
            }
            if (autorizados >= limite) {
                resultados.add(new ItemAplicado(
                    entrada.leadId(), entrada.cnpj(), StatusAplicacao.REJEITADO_LIMITE
                ));
                continue;
            }
            autorizados++;

            Lead lead = leadRepository.findById(entrada.leadId()).orElse(null);
            if (lead == null) {
                resultados.add(new ItemAplicado(
                    entrada.leadId(), entrada.cnpj(), StatusAplicacao.REJEITADO_LEAD_NAO_ENCONTRADO
                ));
                continue;
            }
            if (entrada.cnpj().equals(lead.getCnpj())) {
                resultados.add(new ItemAplicado(
                    entrada.leadId(), entrada.cnpj(), StatusAplicacao.PULADO_IDEMPOTENTE
                ));
                continue;
            }
            if (lead.getCnpj() != null) {
                resultados.add(new ItemAplicado(
                    entrada.leadId(), entrada.cnpj(), StatusAplicacao.REJEITADO_CNPJ_DIVERGENTE
                ));
                continue;
            }

            CnpjService.AvaliacaoMatch atual = cnpjService.avaliarParaDiagnostico(lead);
            CnpjService.Correspondencia correspondencia = atual.correspondencia();
            if (!atual.politicaPermitida()
                || correspondencia == null
                || !entrada.cnpj().equals(correspondencia.cnpj())
                || !entrada.competencia().equals(correspondencia.dataBase())
                || !CLASSIFICACOES_APROVADAS.contains(atual.classificacao())) {
                resultados.add(new ItemAplicado(
                    entrada.leadId(), entrada.cnpj(), StatusAplicacao.REJEITADO_AVALIACAO_DIVERGENTE
                ));
                continue;
            }
            correspondencia.preencherLead(lead);
            leadRepository.save(lead);
            resultados.add(new ItemAplicado(
                entrada.leadId(), entrada.cnpj(), StatusAplicacao.APLICADO
            ));
        }
        return new ResultadoAplicacao(List.copyOf(resultados), hash, resumoRevisao);
    }

    private static boolean exigeRevisao(EntradaRelatorio linha) {
        return linha.politicaPermitida()
            && linha.cnpj() != null
            && linha.resultadoNovo() != null
            && CLASSIFICACOES_APROVADAS.contains(linha.classificacao());
    }

    private static ResumoRevisao resumirRevisao(
        List<EntradaRelatorio> aprovacoes,
        Map<String, EntradaRevisao> revisaoPorChave
    ) {
        int certos = 0;
        int errados = 0;
        int inconclusivos = 0;
        int pendentes = 0;
        for (EntradaRelatorio linha : aprovacoes) {
            EntradaRevisao revisao = revisaoPorChave.get(chave(linha.leadId(), linha.cnpj()));
            if (!revisaoValida(revisao, linha)) {
                pendentes++;
                continue;
            }
            switch (revisao.revisao()) {
                case "CERTO" -> certos++;
                case "ERRADO" -> errados++;
                case "INCONCLUSIVO" -> inconclusivos++;
                default -> pendentes++;
            }
        }
        int total = aprovacoes.size();
        BigDecimal cobertura = total == 0
            ? BigDecimal.ZERO.setScale(4)
            : BigDecimal.valueOf(certos + errados)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        return new ResumoRevisao(
            total,
            certos,
            errados,
            inconclusivos,
            pendentes,
            cobertura
        );
    }

    private static boolean revisaoValida(EntradaRevisao revisao, EntradaRelatorio linha) {
        if (revisao == null
            || (vazio(revisao.evidenciaUrl()) && vazio(revisao.evidencia()))
            || revisao.classificacao() != linha.classificacao()
            || !revisao.cnpj().equals(linha.resultadoNovo())
            || revisao.competencia() == null
            || !revisao.competencia().equals(linha.competencia())) {
            return false;
        }
        return Set.of("CERTO", "ERRADO", "INCONCLUSIVO").contains(revisao.revisao());
    }

    private static List<ItemAplicado> itensPromocaoVetada(
        List<EntradaRelatorio> aprovacoes,
        Map<String, EntradaRevisao> revisaoPorChave
    ) {
        return aprovacoes.stream().map(linha -> {
            EntradaRevisao revisao = revisaoPorChave.get(chave(linha.leadId(), linha.cnpj()));
            StatusAplicacao status;
            if (revisao == null) {
                status = StatusAplicacao.REJEITADO_REVISAO_AUSENTE;
            } else if ("ERRADO".equals(revisao.revisao())) {
                status = StatusAplicacao.REJEITADO_REVISAO_NAO_CERTO;
            } else if (!revisaoValida(revisao, linha)) {
                status = StatusAplicacao.REJEITADO_REVISAO_INVALIDA;
            } else {
                status = StatusAplicacao.REJEITADO_PROMOCAO_VETADA;
            }
            return new ItemAplicado(linha.leadId(), linha.cnpj(), status);
        }).toList();
    }

    private List<EntradaRelatorio> lerRelatorio(Path caminho) {
        try {
            List<EntradaRelatorio> entradas = new ArrayList<>();
            boolean cabecalhoLido = false;
            for (String linha : Files.readAllLines(caminho, StandardCharsets.UTF_8)) {
                if (linha.isBlank()) {
                    continue;
                }
                JsonNode json = objectMapper.readTree(linha);
                if ("cabecalho".equals(json.path("tipo").asText())) {
                    if (cabecalhoLido
                        || !entradas.isEmpty()
                        || !CnpjMatchRelatorioWriter.SCHEMA.equals(json.path("schema").asText())
                        || !CnpjNumeroNormalizer.VERSAO.equals(
                            json.path("versaoNormalizador").asText()
                        )
                        || !CnpjNumeroNormalizer.VERSAO_LEGADO.equals(
                            json.path("versaoNormalizadorLegado").asText()
                        )
                        || !policy.fingerprint().equals(json.path("fingerprintAllowlist").asText())) {
                        throw new IllegalArgumentException("Schema do relatório não suportado");
                    }
                    cabecalhoLido = true;
                    continue;
                }
                if (!cabecalhoLido || !"lead".equals(json.path("tipo").asText())) {
                    throw new IllegalArgumentException("Linha desconhecida no relatório JSONL");
                }
                entradas.add(new EntradaRelatorio(
                    json.path("leadId").asLong(),
                    texto(json, "cnpj"),
                    enumValue(CnpjMatchClassificacao.class, json, "classificacao"),
                    json.path("politicaPermitida").asBoolean(),
                    texto(json, "resultadoNovo"),
                    data(json, "competencia")
                ));
            }
            if (!cabecalhoLido) {
                throw new IllegalArgumentException("Relatório JSONL sem cabeçalho");
            }
            return entradas;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Relatório JSONL inválido", exception);
        }
    }

    private List<EntradaRevisao> lerRevisao(Path caminho, String hash) {
        try {
            List<String> linhas = Files.readAllLines(caminho, StandardCharsets.UTF_8);
            if (linhas.isEmpty() || !CABECALHO_REVIEW.equals(linhas.getFirst())) {
                throw new IllegalArgumentException("Cabeçalho de revisão inválido");
            }
            List<EntradaRevisao> entradas = new ArrayList<>();
            Set<String> chaves = new HashSet<>();
            for (String linha : linhas.subList(1, linhas.size())) {
                if (linha.isBlank()) {
                    continue;
                }
                List<String> campos = parseCsv(linha);
                if (campos.size() != 8 || !hash.equals(campos.get(7))) {
                    throw new IllegalArgumentException(
                        "Hash ou formato da revisão não corresponde ao JSONL"
                    );
                }
                EntradaRevisao entrada = new EntradaRevisao(
                    Long.parseLong(campos.get(0)),
                    campos.get(1),
                    parseDate(campos.get(2)),
                    enumValue(campos.get(3)),
                    campos.get(4),
                    campos.get(5),
                    campos.get(6)
                );
                if (!chaves.add(chave(entrada.leadId(), entrada.cnpj()))) {
                    throw new IllegalArgumentException("Lead/CNPJ duplicado na revisão");
                }
                entradas.add(entrada);
            }
            return entradas;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Arquivo de revisão inválido", exception);
        }
    }

    private static List<String> parseCsv(String linha) {
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreAspas = false;
        for (int indice = 0; indice < linha.length(); indice++) {
            char caractere = linha.charAt(indice);
            if (caractere == '"') {
                if (entreAspas && indice + 1 < linha.length() && linha.charAt(indice + 1) == '"') {
                    campo.append('"');
                    indice++;
                } else {
                    entreAspas = !entreAspas;
                }
            } else if (caractere == ',' && !entreAspas) {
                campos.add(campo.toString());
                campo.setLength(0);
            } else {
                campo.append(caractere);
            }
        }
        if (entreAspas) {
            throw new IllegalArgumentException("CSV de revisão com aspas incompletas");
        }
        campos.add(campo.toString());
        return campos;
    }

    private static String chave(Long leadId, String cnpj) {
        return leadId + "|" + cnpj;
    }

    private static String texto(JsonNode json, String campo) {
        JsonNode valor = json.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }

    private static LocalDate data(JsonNode json, String campo) {
        String valor = texto(json, campo);
        return parseDate(valor);
    }

    private static LocalDate parseDate(String valor) {
        return valor == null || valor.isBlank() || "null".equals(valor)
            ? null
            : LocalDate.parse(valor);
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> tipo, JsonNode json, String campo) {
        return Enum.valueOf(tipo, json.path(campo).asText());
    }

    private static CnpjMatchClassificacao enumValue(String valor) {
        return Enum.valueOf(CnpjMatchClassificacao.class, valor);
    }

    public record ResultadoAplicacao(
        List<ItemAplicado> itens,
        String hashRelatorio,
        ResumoRevisao revisao
    ) {
        public ResultadoAplicacao {
            itens = List.copyOf(itens);
        }
    }

    public record ResumoRevisao(
        int totalAprovacoes,
        int certos,
        int errados,
        int inconclusivos,
        int pendentes,
        BigDecimal taxaCobertura
    ) {
    }

    public record ItemAplicado(Long leadId, String cnpj, StatusAplicacao status) {
    }

    public enum StatusAplicacao {
        APLICADO,
        PULADO_IDEMPOTENTE,
        REJEITADO_CNPJ_DIVERGENTE,
        REJEITADO_AVALIACAO_DIVERGENTE,
        REJEITADO_REVISAO_NAO_CERTO,
        REJEITADO_REVISAO_SEM_EVIDENCIA,
        REJEITADO_REVISAO_AUSENTE,
        REJEITADO_REVISAO_INVALIDA,
        REJEITADO_PROMOCAO_VETADA,
        REJEITADO_LINHA_NAO_AUTORIZADA,
        REJEITADO_LEAD_NAO_ENCONTRADO,
        REJEITADO_LIMITE
    }

    private record EntradaRelatorio(
        Long leadId,
        String cnpj,
        CnpjMatchClassificacao classificacao,
        boolean politicaPermitida,
        String resultadoNovo,
        LocalDate competencia
    ) {
    }

    private record EntradaRevisao(
        Long leadId,
        String cnpj,
        LocalDate competencia,
        CnpjMatchClassificacao classificacao,
        String revisao,
        String evidenciaUrl,
        String evidencia
    ) {
    }
}
