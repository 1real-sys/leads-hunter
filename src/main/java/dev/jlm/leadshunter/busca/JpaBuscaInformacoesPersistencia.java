package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.integracao.pesquisa.FormatadorObservacoesPesquisa;
import dev.jlm.leadshunter.integracao.pesquisa.PesquisaInformacoesWebResultado;
import dev.jlm.leadshunter.integracao.pesquisa.PesquisaLeadDados;
import dev.jlm.leadshunter.lead.Lead;
import dev.jlm.leadshunter.lead.LeadNaoEncontradoException;
import dev.jlm.leadshunter.lead.LeadRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JpaBuscaInformacoesPersistencia implements BuscaInformacoesPersistencia {

    private final BuscaRepository buscaRepository;
    private final BuscaLeadRepository buscaLeadRepository;
    private final LeadRepository leadRepository;
    private final FormatadorObservacoesPesquisa formatador;

    @Override
    @Transactional(readOnly = true)
    public List<BuscaInformacoesLead> carregarLeads(Long buscaId) {
        if (!buscaRepository.existsById(buscaId)) {
            throw new BuscaNaoEncontradaException(buscaId);
        }
        return buscaLeadRepository.findByBuscaIdOrderByScoreNaBuscaDesc(buscaId).stream()
            .map(BuscaLead::getLead)
            .map(this::snapshot)
            .toList();
    }

    @Override
    @Transactional
    public PesquisaInformacoesWebResultado atualizarObservacoes(
        Long leadId,
        PesquisaInformacoesWebResultado resultado
    ) {
        Lead lead = leadRepository.findById(leadId)
            .orElseThrow(() -> new LeadNaoEncontradoException(leadId));
        String atualizadas = formatador.atualizar(lead.getObservacoes(), resultado);
        if (!Objects.equals(lead.getObservacoes(), atualizadas)) {
            lead.setObservacoes(atualizadas);
        }
        return formatador.extrairLinks(atualizadas);
    }

    private BuscaInformacoesLead snapshot(Lead lead) {
        return new BuscaInformacoesLead(
            lead.getId(),
            PesquisaLeadDados.de(lead),
            lead.getObservacoes()
        );
    }
}
