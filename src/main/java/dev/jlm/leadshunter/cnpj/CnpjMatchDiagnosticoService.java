package dev.jlm.leadshunter.cnpj;

import dev.jlm.leadshunter.lead.Lead;
import dev.jlm.leadshunter.lead.LeadRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Read-only, paginated diagnostic over distinct leads rather than history rows. */
@Service
@RequiredArgsConstructor
public class CnpjMatchDiagnosticoService {

    static final int LOTE = 100;
    static final int LIMITE_PADRAO = 5_000;

    private final LeadRepository leadRepository;
    private final CnpjService cnpjService;

    public List<CnpjMatchRelatorioLinha> avaliarTodos() {
        List<CnpjMatchRelatorioLinha> linhas = new ArrayList<>();
        long ultimoId = 0;
        while (true) {
            List<Lead> lote = leadRepository.buscarSemCnpjAposId(
                ultimoId,
                PageRequest.of(0, LOTE)
            );
            if (lote.isEmpty()) {
                return List.copyOf(linhas);
            }
            for (Lead lead : lote) {
                ultimoId = lead.getId();
                linhas.add(avaliar(lead));
            }
        }
    }

    public List<CnpjMatchRelatorioLinha> avaliarTodos(int limite) {
        if (limite < 1 || limite > LIMITE_PADRAO) {
            throw new IllegalArgumentException("limite deve estar entre 1 e " + LIMITE_PADRAO);
        }
        List<CnpjMatchRelatorioLinha> linhas = new ArrayList<>();
        long ultimoId = 0;
        while (linhas.size() < limite) {
            int tamanho = Math.min(LOTE, limite - linhas.size());
            List<Lead> lote = leadRepository.buscarSemCnpjAposId(
                ultimoId,
                PageRequest.of(0, tamanho)
            );
            if (lote.isEmpty()) {
                break;
            }
            for (Lead lead : lote) {
                ultimoId = lead.getId();
                linhas.add(avaliar(lead));
                if (linhas.size() == limite) {
                    break;
                }
            }
        }
        return List.copyOf(linhas);
    }

    public List<CnpjNumeroNormalizer.NumeroDescartado> listarNumerosDescartados() {
        return cnpjService.listarNumerosDescartados();
    }

    private CnpjMatchRelatorioLinha avaliar(Lead lead) {
        CnpjService.AvaliacaoMatch legado = cnpjService.avaliarLegadoAntes(lead);
        CnpjService.AvaliacaoMatch novo = cnpjService.avaliarParaDiagnostico(lead);
        CnpjService.Correspondencia correspondencia = novo.correspondencia();
        LocalDate competencia = correspondencia != null
            ? correspondencia.dataBase()
            : novo.candidatos().stream()
                .map(CnpjService.CandidatoAvaliacao::dataBase)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new CnpjMatchRelatorioLinha(
            "lead",
            lead.getId(),
            lead.getGooglePlaceId(),
            novo.classificacao(),
            novo.politicaPermitida(),
            correspondencia == null ? null : correspondencia.cnpj(),
            competencia,
            correspondencia == null ? null : correspondencia.origem(),
            novo.primeiraPontuacao(),
            novo.segundaPontuacao(),
            novo.gapNome(),
            legado.correspondencia() == null ? null : legado.correspondencia().cnpj(),
            legado.classificacao(),
            correspondencia == null ? null : correspondencia.cnpj(),
            novo.normalizacaoNumeroAlterada(),
            lead.getNumero(),
            CnpjNumeroNormalizer.classificar(lead.getNumero()),
            novo.candidatos()
        );
    }
}
