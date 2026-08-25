package dev.jlm.leadshunter.lead;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeadService {

    private static final Sort ORDENACAO_PADRAO = Sort.by(
        Sort.Order.desc("score").nullsLast(),
        Sort.Order.asc("nome").nullsLast()
    );

    private final LeadRepository leadRepository;
    private final WhatsAppLinkGenerator whatsAppLinkGenerator;

    @Transactional(readOnly = true)
    public List<LeadResponse> listar(
        StatusFunil status,
        CategoriaNegocio categoria,
        Temperatura temperatura
    ) {
        Lead filtros = new Lead();
        filtros.setStatus(status);
        filtros.setCategoria(categoria);
        filtros.setTemperatura(temperatura);

        ExampleMatcher matcher = ExampleMatcher.matchingAll().withIgnoreNullValues();
        return leadRepository.findAll(Example.of(filtros, matcher), ORDENACAO_PADRAO).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public LeadResponse buscarPorId(Long id) {
        return leadRepository.findById(id)
            .map(this::toResponse)
            .orElseThrow(() -> new LeadNaoEncontradoException(id));
    }

    @Transactional
    public LeadResponse atualizar(Long id, AtualizarLeadRequest request) {
        Lead lead = leadRepository.findById(id)
            .orElseThrow(() -> new LeadNaoEncontradoException(id));

        if (request.status() != null) {
            lead.setStatus(request.status());
        }
        if (request.observacoes() != null) {
            lead.setObservacoes(request.observacoes());
        }
        if (request.ultimoContatoEm() != null) {
            lead.setUltimoContatoEm(request.ultimoContatoEm());
        }

        return toResponse(leadRepository.saveAndFlush(lead));
    }

    private LeadResponse toResponse(Lead lead) {
        return LeadResponse.from(lead, whatsAppLinkGenerator);
    }
}
