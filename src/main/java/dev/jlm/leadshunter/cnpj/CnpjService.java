package dev.jlm.leadshunter.cnpj;

import dev.jlm.leadshunter.lead.Lead;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CnpjService {

    static final String SITUACAO_ATIVA = "02";
    static final int MAXIMO_CANDIDATOS = 200;
    static final double LIMIAR_COM_CEP = CnpjMatchEvaluator.LIMIAR_COM_CEP;
    static final double LIMIAR_SEM_CEP = CnpjMatchEvaluator.LIMIAR_SEM_CEP;
    static final double MINIMO_LOGRADOURO = CnpjMatchEvaluator.MINIMO_LOGRADOURO;
    static final double MINIMO_NOME = CnpjMatchEvaluator.MINIMO_NOME;
    static final double LIMIAR_DESEMPATE_NOME = CnpjMatchEvaluator.LIMIAR_DESEMPATE_NOME;

    private final CnpjEstabelecimentoRepository estabelecimentoRepository;
    private final CnpjMatchPolicy policy;
    private final CnpjMatchEvaluator evaluator;

    public CnpjService(CnpjEstabelecimentoRepository estabelecimentoRepository) {
        this(estabelecimentoRepository, CnpjMatchPolicy.desabilitada());
    }

    @Autowired
    public CnpjService(
        CnpjEstabelecimentoRepository estabelecimentoRepository,
        CnpjMatchPolicy policy
    ) {
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.policy = policy;
        this.evaluator = new CnpjMatchEvaluator();
    }

    @Transactional(readOnly = true)
    public Optional<Correspondencia> corresponder(Lead lead) {
        return avaliar(lead, true).correspondenciaOptional();
    }

    /** Revalidation must confirm an existing identity even after the capture gate is disabled. */
    @Transactional(readOnly = true)
    public Optional<Correspondencia> corresponderParaRevalidacao(Lead lead) {
        return avaliar(lead, false).correspondenciaOptional();
    }

    /** Evaluates the new rule even when production policy is disabled. */
    @Transactional(readOnly = true)
    public AvaliacaoMatch avaliarParaDiagnostico(Lead lead) {
        return avaliar(lead, false);
    }

    /** Reproduces the pre-V9 query and number normalization for before/after reports. */
    @Transactional(readOnly = true)
    public AvaliacaoMatch avaliarLegadoAntes(Lead lead) {
        return avaliarLegado(lead);
    }

    @Transactional(readOnly = true)
    public Optional<LocalDate> buscarDataBaseAtual(String municipioCodigoIbge) {
        if (!codigoMunicipioValido(municipioCodigoIbge)) {
            return Optional.empty();
        }
        return estabelecimentoRepository.findDataBaseAtual(
            municipioCodigoIbge,
            SITUACAO_ATIVA
        );
    }

    /** Lists every non-blank raw establishment number discarded by V9. */
    @Transactional(readOnly = true)
    public List<CnpjNumeroNormalizer.NumeroDescartado> listarNumerosDescartados() {
        return estabelecimentoRepository.listarNumerosDescartados().stream()
            .map(item -> new CnpjNumeroNormalizer.NumeroDescartado(
                item.getNumero(),
                CnpjNumeroNormalizer.compacto(item.getNumero()),
                CnpjNumeroNormalizer.classificar(item.getNumero()),
                item.getQuantidade()
            ))
            .toList();
    }

    private AvaliacaoMatch avaliar(Lead lead, boolean respeitarPolitica) {
        boolean politicaPermitida = policy.permite(lead);
        boolean normalizacaoAlterada = normalizacaoNumeroAlterada(lead);
        if (lead == null || !codigoMunicipioValido(lead.getMunicipioCodigoIbge())) {
            return vazia(
                CnpjMatchClassificacao.SEM_CORRESPONDENCIA,
                politicaPermitida,
                normalizacaoAlterada
            );
        }

        String numero = CnpjNumeroNormalizer.normalizar(lead.getNumero());
        String cep = CnpjMatchEvaluator.normalizarCep(lead.getCep());
        boolean enderecoElegivel = cep != null
            && numero != null
            && !CnpjMatchEvaluator.normalizarLogradouro(lead.getLogradouro()).isEmpty();
        if (enderecoElegivel && (!respeitarPolitica || politicaPermitida)) {
            Consulta consulta = consultarNormalizada(lead, cep, numero);
            boolean normalizacaoComCandidatos = normalizacaoNumeroAlterada(
                lead,
                consulta.candidatos()
            );
            if (consulta.truncada()) {
                return new AvaliacaoMatch(
                    CnpjMatchClassificacao.CONSULTA_TRUNCADA,
                    politicaPermitida,
                    null,
                    avaliarCandidatosExatos(lead, consulta.candidatos()),
                    null,
                    null,
                    null,
                    true,
                    normalizacaoAlterada || normalizacaoComCandidatos
                );
            }

            List<CandidatoAvaliacao> exatos = avaliarCandidatosExatos(
                lead,
                consulta.candidatos()
            );
            if (!exatos.isEmpty()) {
                return decidirEnderecoExato(
                    exatos,
                    politicaPermitida,
                    normalizacaoAlterada || normalizacaoComCandidatos
                );
            }
        }

        return avaliarLegadoAtual(lead, politicaPermitida, normalizacaoAlterada);
    }

    private AvaliacaoMatch avaliarLegadoAtual(
        Lead lead,
        boolean politicaPermitida,
        boolean normalizacaoAlterada
    ) {
        String numero = CnpjNumeroNormalizer.normalizar(lead.getNumero());
        String logradouro = CnpjMatchEvaluator.normalizarLogradouro(lead.getLogradouro());
        String nome = CnpjMatchEvaluator.normalizarTexto(lead.getNome());
        if (numero == null || logradouro.isEmpty() || nome.isEmpty()) {
            return vazia(
                CnpjMatchClassificacao.SEM_CORRESPONDENCIA,
                politicaPermitida,
                normalizacaoAlterada
            );
        }

        String cep = CnpjMatchEvaluator.normalizarCep(lead.getCep());
        Consulta consulta = consultarNormalizada(lead, cep, numero);
        return avaliarLegadoComCandidatos(
            lead,
            consulta.candidatos(),
            consulta.truncada(),
            politicaPermitida,
            normalizacaoAlterada
        );
    }

    private AvaliacaoMatch avaliarLegadoComCandidatos(
        Lead lead,
        List<CnpjEstabelecimento> candidatos,
        boolean truncada,
        boolean politicaPermitida,
        boolean normalizacaoAlterada
    ) {
        normalizacaoAlterada = normalizacaoAlterada
            || normalizacaoNumeroAlterada(lead, candidatos);
        if (truncada) {
            return new AvaliacaoMatch(
                CnpjMatchClassificacao.CONSULTA_TRUNCADA,
                politicaPermitida,
                null,
                List.of(),
                null,
                null,
                null,
                true,
                normalizacaoAlterada
            );
        }
        if (candidatos.isEmpty()) {
            return vazia(
                CnpjMatchClassificacao.SEM_CANDIDATO,
                politicaPermitida,
                normalizacaoAlterada
            );
        }

        boolean comCep = CnpjMatchEvaluator.normalizarCep(lead.getCep()) != null;
        List<CandidatoAvaliacao> avaliados = candidatos.stream()
            .map(candidato -> avaliarLegadoCandidato(lead, candidato, comCep, false))
            .sorted(Comparator.comparing(CandidatoAvaliacao::pontuacao).reversed())
            .toList();
        List<CandidatoAvaliacao> aprovados = avaliados.stream()
            .filter(CandidatoAvaliacao::aprovado)
            .toList();
        if (aprovados.size() != 1) {
            return new AvaliacaoMatch(
                CnpjMatchClassificacao.SEM_CORRESPONDENCIA,
                politicaPermitida,
                null,
                avaliados,
                primeiraPontuacao(avaliados),
                segundaPontuacao(avaliados),
                gap(avaliados),
                false,
                normalizacaoAlterada
            );
        }
        CandidatoAvaliacao escolhido = aprovados.getFirst();
        return new AvaliacaoMatch(
            CnpjMatchClassificacao.NOME_RESOLVE,
            politicaPermitida,
            correspondencia(escolhido, CnpjOrigem.NOME_ENDERECO),
            avaliados,
            primeiraPontuacao(avaliados),
            segundaPontuacao(avaliados),
            gap(avaliados),
            false,
            normalizacaoAlterada
        );
    }

    private AvaliacaoMatch decidirEnderecoExato(
        List<CandidatoAvaliacao> candidatos,
        boolean politicaPermitida,
        boolean normalizacaoAlterada
    ) {
        List<CandidatoAvaliacao> ordenados = candidatos.stream()
            .sorted(Comparator.comparing(CandidatoAvaliacao::similaridadeNome).reversed())
            .toList();
        if (ordenados.size() == 1) {
            CandidatoAvaliacao escolhido = ordenados.getFirst();
            return new AvaliacaoMatch(
                CnpjMatchClassificacao.ENDERECO_UNICO,
                politicaPermitida,
                correspondencia(escolhido, CnpjOrigem.ENDERECO_EXATO),
                ordenados,
                primeiraPontuacao(ordenados),
                null,
                null,
                false,
                normalizacaoAlterada
            );
        }

        List<CandidatoAvaliacao> acimaDoLimiar = ordenados.stream()
            .filter(candidato -> candidato.similaridadeNome().doubleValue() >= LIMIAR_DESEMPATE_NOME)
            .toList();
        if (acimaDoLimiar.size() == 1) {
            CandidatoAvaliacao escolhido = acimaDoLimiar.getFirst();
            return new AvaliacaoMatch(
                CnpjMatchClassificacao.ENDERECO_DESEMPATADO_POR_NOME,
                politicaPermitida,
                correspondencia(escolhido, CnpjOrigem.ENDERECO_EXATO),
                ordenados,
                primeiraPontuacao(ordenados),
                segundaPontuacao(ordenados),
                gap(ordenados),
                false,
                normalizacaoAlterada
            );
        }
        return new AvaliacaoMatch(
            CnpjMatchClassificacao.ENDERECO_MULTIPLO,
            politicaPermitida,
            null,
            ordenados,
            primeiraPontuacao(ordenados),
            segundaPontuacao(ordenados),
            gap(ordenados),
            false,
            normalizacaoAlterada
        );
    }

    private List<CandidatoAvaliacao> avaliarCandidatosExatos(
        Lead lead,
        List<CnpjEstabelecimento> candidatos
    ) {
        return candidatos.stream()
            .map(candidato -> avaliarExatoCandidato(lead, candidato))
            .filter(CandidatoAvaliacao::aprovado)
            .toList();
    }

    private CandidatoAvaliacao avaliarExatoCandidato(Lead lead, CnpjEstabelecimento candidato) {
        CnpjMatchEvaluator.Resultado resultado = evaluator.avaliarEnderecoExato(
            dadosLead(lead),
            dadosCandidato(candidato)
        );
        boolean municipioAtivo = Objects.equals(
            lead.getMunicipioCodigoIbge(), candidato.getMunicipioCodigoIbge()
        ) && Objects.equals(SITUACAO_ATIVA, candidato.getSituacaoCadastral());
        return candidatoAvaliacao(candidato, resultado, resultado.aprovado() && municipioAtivo);
    }

    private CandidatoAvaliacao avaliarLegadoCandidato(
        Lead lead,
        CnpjEstabelecimento candidato,
        boolean comCep,
        boolean legadoAntes
    ) {
        CnpjMatchEvaluator.Resultado resultado = legadoAntes
            ? evaluator.avaliarLegado(dadosLead(lead), dadosCandidato(candidato), comCep)
            : evaluator.avaliarNovoLegado(dadosLead(lead), dadosCandidato(candidato), comCep);
        return candidatoAvaliacao(candidato, resultado, resultado.aprovado());
    }

    private AvaliacaoMatch avaliarLegado(Lead lead) {
        boolean normalizacaoAlterada = normalizacaoNumeroAlterada(lead);
        boolean politicaPermitida = policy.permite(lead);
        if (lead == null || !codigoMunicipioValido(lead.getMunicipioCodigoIbge())) {
            return vazia(
                CnpjMatchClassificacao.SEM_CORRESPONDENCIA,
                politicaPermitida,
                normalizacaoAlterada
            );
        }
        String numero = CnpjNumeroNormalizer.normalizarLegado(lead.getNumero());
        String nome = CnpjMatchEvaluator.normalizarTexto(lead.getNome());
        String logradouro = CnpjMatchEvaluator.normalizarLogradouro(lead.getLogradouro());
        if (numero == null || nome.isEmpty() || logradouro.isEmpty()) {
            return vazia(
                CnpjMatchClassificacao.SEM_CORRESPONDENCIA,
                politicaPermitida,
                normalizacaoAlterada
            );
        }
        String cep = CnpjMatchEvaluator.normalizarCep(lead.getCep());
        Slice<CnpjEstabelecimento> slice = cep == null
            ? estabelecimentoRepository.findByMunicipioCodigoIbgeAndSituacaoCadastralAndNumero(
                lead.getMunicipioCodigoIbge(), SITUACAO_ATIVA, numero,
                PageRequest.of(0, MAXIMO_CANDIDATOS)
            )
            : estabelecimentoRepository.findByMunicipioCodigoIbgeAndSituacaoCadastralAndCep(
                lead.getMunicipioCodigoIbge(), SITUACAO_ATIVA, cep,
                PageRequest.of(0, MAXIMO_CANDIDATOS)
            );
        if (slice == null) {
            slice = new org.springframework.data.domain.SliceImpl<>(List.of());
        }
        if (slice.hasNext()) {
            return new AvaliacaoMatch(
                CnpjMatchClassificacao.CONSULTA_TRUNCADA,
                politicaPermitida,
                null,
                List.of(),
                null,
                null,
                null,
                true,
                normalizacaoAlterada
            );
        }
        List<CandidatoAvaliacao> avaliados = slice.getContent().stream()
            .map(candidato -> avaliarLegadoCandidato(
                lead,
                candidato,
                cep != null,
                true
            ))
            .sorted(Comparator.comparing(CandidatoAvaliacao::pontuacao).reversed())
            .toList();
        List<CandidatoAvaliacao> aprovados = avaliados.stream()
            .filter(CandidatoAvaliacao::aprovado)
            .toList();
        return aprovados.size() == 1
            ? new AvaliacaoMatch(
                CnpjMatchClassificacao.NOME_RESOLVE,
                politicaPermitida,
                correspondencia(aprovados.getFirst(), CnpjOrigem.NOME_ENDERECO),
                avaliados,
                primeiraPontuacao(avaliados),
                segundaPontuacao(avaliados),
                gap(avaliados),
                false,
                normalizacaoAlterada
            )
            : new AvaliacaoMatch(
                avaliados.isEmpty()
                    ? CnpjMatchClassificacao.SEM_CANDIDATO
                    : CnpjMatchClassificacao.SEM_CORRESPONDENCIA,
                politicaPermitida,
                null,
                avaliados,
                primeiraPontuacao(avaliados),
                segundaPontuacao(avaliados),
                gap(avaliados),
                false,
                normalizacaoAlterada
            );
    }

    private Consulta consultarNormalizada(Lead lead, String cep, String numero) {
        PageRequest limite = PageRequest.of(0, MAXIMO_CANDIDATOS);
        Slice<CnpjEstabelecimento> slice = cep != null
            ? estabelecimentoRepository
                .findByMunicipioCodigoIbgeAndSituacaoCadastralAndCepAndNumeroNormalizado(
                    lead.getMunicipioCodigoIbge(), SITUACAO_ATIVA, cep, numero, limite
                )
            : estabelecimentoRepository
                .findByMunicipioCodigoIbgeAndSituacaoCadastralAndNumeroNormalizado(
                    lead.getMunicipioCodigoIbge(), SITUACAO_ATIVA, numero, limite
                );
        if (slice == null) {
            slice = new org.springframework.data.domain.SliceImpl<>(List.of());
        }
        return new Consulta(slice.getContent(), slice.hasNext());
    }

    private CandidatoAvaliacao candidatoAvaliacao(
        CnpjEstabelecimento candidato,
        CnpjMatchEvaluator.Resultado resultado,
        boolean aprovado
    ) {
        return new CandidatoAvaliacao(
            candidato.getCnpj(),
            candidato.getEmpresa() == null ? null : candidato.getEmpresa().getRazaoSocial(),
            candidato.getDataBase(),
            decimal(resultado.pontuacao()),
            decimal(resultado.similaridadeNome()),
            decimal(resultado.similaridadeLogradouro()),
            aprovado,
            resultado.motivo().name()
        );
    }

    private Correspondencia correspondencia(CandidatoAvaliacao candidato, CnpjOrigem origem) {
        return new Correspondencia(
            candidato.cnpj(),
            candidato.razaoSocial(),
            candidato.dataBase(),
            candidato.pontuacao(),
            origem
        );
    }

    private CnpjMatchEvaluator.DadosLead dadosLead(Lead lead) {
        return new CnpjMatchEvaluator.DadosLead(
            lead.getNome(),
            lead.getLogradouro(),
            lead.getNumero(),
            lead.getBairro(),
            lead.getCep(),
            lead.getMunicipioCodigoIbge(),
            lead.getUf()
        );
    }

    private CnpjMatchEvaluator.DadosCandidato dadosCandidato(CnpjEstabelecimento candidato) {
        String logradouro = candidato.getLogradouroNormalizado();
        if (logradouro == null || logradouro.isBlank()) {
            logradouro = candidato.getLogradouro();
        }
        String bairro = candidato.getBairroNormalizado();
        if (bairro == null || bairro.isBlank()) {
            bairro = candidato.getBairro();
        }
        return new CnpjMatchEvaluator.DadosCandidato(
            candidato.getCnpj(),
            candidato.getEmpresa() == null ? null : candidato.getEmpresa().getRazaoSocial(),
            candidato.getNomeFantasiaNormalizado() == null
                ? candidato.getNomeFantasia()
                : candidato.getNomeFantasiaNormalizado(),
            logradouro,
            candidato.getNumero(),
            candidato.getNumeroNormalizado(),
            bairro,
            candidato.getCep(),
            candidato.getMunicipioCodigoIbge(),
            candidato.getSituacaoCadastral()
        );
    }

    private AvaliacaoMatch vazia(
        CnpjMatchClassificacao classificacao,
        boolean politicaPermitida,
        boolean normalizacaoAlterada
    ) {
        return new AvaliacaoMatch(
            classificacao,
            politicaPermitida,
            null,
            List.of(),
            null,
            null,
            null,
            false,
            normalizacaoAlterada
        );
    }

    private static BigDecimal primeiraPontuacao(List<CandidatoAvaliacao> candidatos) {
        return candidatos.isEmpty() ? null : candidatos.getFirst().pontuacao();
    }

    private static BigDecimal segundaPontuacao(List<CandidatoAvaliacao> candidatos) {
        return candidatos.size() < 2 ? null : candidatos.get(1).pontuacao();
    }

    private static BigDecimal gap(List<CandidatoAvaliacao> candidatos) {
        if (candidatos.size() < 2) {
            return null;
        }
        return candidatos.getFirst().similaridadeNome()
            .subtract(candidatos.get(1).similaridadeNome())
            .setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(double valor) {
        return BigDecimal.valueOf(valor).setScale(4, RoundingMode.HALF_UP);
    }

    private static boolean normalizacaoNumeroAlterada(Lead lead) {
        if (lead == null) {
            return false;
        }
        return !Objects.equals(
            CnpjNumeroNormalizer.normalizarLegado(lead.getNumero()),
            CnpjNumeroNormalizer.normalizar(lead.getNumero())
        );
    }

    private static boolean normalizacaoNumeroAlterada(
        Lead lead,
        List<CnpjEstabelecimento> candidatos
    ) {
        if (normalizacaoNumeroAlterada(lead)) {
            return true;
        }
        return candidatos.stream().anyMatch(candidato -> {
            String normalizado = candidato.getNumeroNormalizado() == null
                ? CnpjNumeroNormalizer.normalizar(candidato.getNumero())
                : CnpjNumeroNormalizer.normalizar(candidato.getNumeroNormalizado());
            return !Objects.equals(
                CnpjNumeroNormalizer.normalizarLegado(candidato.getNumero()),
                normalizado
            );
        });
    }

    private boolean codigoMunicipioValido(String valor) {
        return valor != null && valor.matches("\\d{7}");
    }

    static String normalizarTexto(String valor) {
        return CnpjMatchEvaluator.normalizarTexto(valor);
    }

    static String normalizarLogradouro(String valor) {
        return CnpjMatchEvaluator.normalizarLogradouro(valor);
    }

    static String normalizarNome(String valor) {
        return CnpjMatchEvaluator.normalizarTexto(valor);
    }

    static String normalizarNumero(String valor) {
        return CnpjNumeroNormalizer.normalizar(valor);
    }

    static String normalizarNumeroLegado(String valor) {
        return CnpjNumeroNormalizer.normalizarLegado(valor);
    }

    static String normalizarCep(String valor) {
        return CnpjMatchEvaluator.normalizarCep(valor);
    }

    static double similaridade(String primeiro, String segundo) {
        return CnpjMatchEvaluator.similaridade(primeiro, segundo);
    }

    static double similaridadeNome(String primeiro, String segundo) {
        return new CnpjMatchEvaluator().similaridadeNome(primeiro, segundo);
    }

    public record Correspondencia(
        String cnpj,
        String razaoSocial,
        LocalDate dataBase,
        BigDecimal confianca,
        CnpjOrigem origem
    ) {
        public Correspondencia(
            String cnpj,
            String razaoSocial,
            LocalDate dataBase,
            BigDecimal confianca
        ) {
            this(cnpj, razaoSocial, dataBase, confianca, CnpjOrigem.NOME_ENDERECO);
        }

        public void preencherLead(Lead lead) {
            lead.setCnpj(cnpj);
            lead.setRazaoSocial(razaoSocial);
            lead.setCnpjCorrespondidoEm(LocalDateTime.now());
            lead.setCnpjDataBase(dataBase);
            lead.setCnpjConfianca(confianca);
            lead.setCnpjOrigem(origem);
        }

        public void atualizarMetadados(Lead lead) {
            lead.setCnpjCorrespondidoEm(LocalDateTime.now());
            lead.setCnpjDataBase(dataBase);
            lead.setCnpjConfianca(confianca);
            lead.setCnpjOrigem(origem);
        }
    }

    public record AvaliacaoMatch(
        CnpjMatchClassificacao classificacao,
        boolean politicaPermitida,
        Correspondencia correspondencia,
        List<CandidatoAvaliacao> candidatos,
        BigDecimal primeiraPontuacao,
        BigDecimal segundaPontuacao,
        BigDecimal gapNome,
        boolean consultaTruncada,
        boolean normalizacaoNumeroAlterada
    ) {
        public AvaliacaoMatch {
            candidatos = List.copyOf(candidatos);
        }

        public Optional<Correspondencia> correspondenciaOptional() {
            return Optional.ofNullable(correspondencia);
        }
    }

    public record CandidatoAvaliacao(
        String cnpj,
        String razaoSocial,
        LocalDate dataBase,
        BigDecimal pontuacao,
        BigDecimal similaridadeNome,
        BigDecimal similaridadeLogradouro,
        boolean aprovado,
        String motivo
    ) {
    }

    private record Consulta(List<CnpjEstabelecimento> candidatos, boolean truncada) {
    }
}
