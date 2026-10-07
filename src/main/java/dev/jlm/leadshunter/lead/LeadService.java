package dev.jlm.leadshunter.lead;

import dev.jlm.leadshunter.busca.BuscaLead;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeadService {

    private static final Sort ORDENACAO_PADRAO = Sort.by(
        Sort.Order.desc("score").nullsLast(),
        Sort.Order.asc("nome").nullsLast(),
        Sort.Order.asc("id")
    );

    private final LeadRepository leadRepository;
    private final WhatsAppLinkGenerator whatsAppLinkGenerator;

    @Transactional(readOnly = true)
    public List<LeadResponse> listar(
        StatusFunil status,
        CategoriaNegocio categoria,
        Temperatura temperatura
    ) {
        return listar(status, categoria, temperatura, null);
    }

    @Transactional(readOnly = true)
    public List<LeadResponse> listar(
        StatusFunil status,
        CategoriaNegocio categoria,
        Temperatura temperatura,
        Long buscaId
    ) {
        List<Lead> leads = buscaId == null
            ? leadRepository.findAll(criarExemplo(status, categoria, temperatura), ORDENACAO_PADRAO)
            : leadRepository.findAll(criarEspecificacao(status, categoria, temperatura, buscaId), ORDENACAO_PADRAO);
        return leads
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public PaginaLeadsResponse listarPagina(
        StatusFunil status,
        CategoriaNegocio categoria,
        Temperatura temperatura,
        int page,
        int size
    ) {
        return listarPagina(status, categoria, temperatura, null, page, size);
    }

    @Transactional(readOnly = true)
    public PaginaLeadsResponse listarPagina(
        StatusFunil status,
        CategoriaNegocio categoria,
        Temperatura temperatura,
        Long buscaId,
        int page,
        int size
    ) {
        var pageable = PageRequest.of(page, size, ORDENACAO_PADRAO);
        var pagina = (buscaId == null
            ? leadRepository.findAll(criarExemplo(status, categoria, temperatura), pageable)
            : leadRepository.findAll(criarEspecificacao(status, categoria, temperatura, buscaId), pageable))
            .map(this::toResponse);

        return PaginaLeadsResponse.from(pagina);
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

    private Example<Lead> criarExemplo(
        StatusFunil status,
        CategoriaNegocio categoria,
        Temperatura temperatura
    ) {
        Lead filtros = new Lead();
        filtros.setStatus(status);
        filtros.setCategoria(categoria);
        filtros.setTemperatura(temperatura);

        ExampleMatcher matcher = ExampleMatcher.matchingAll().withIgnoreNullValues();
        return Example.of(filtros, matcher);
    }

    private Specification<Lead> criarEspecificacao(
        StatusFunil status,
        CategoriaNegocio categoria,
        Temperatura temperatura,
        Long buscaId
    ) {
        return (root, query, builder) -> {
            var vinculo = query.subquery(Long.class);
            var buscaLead = vinculo.from(BuscaLead.class);
            vinculo.select(buscaLead.get("id"));
            vinculo.where(
                builder.equal(buscaLead.get("lead").get("id"), root.get("id")),
                builder.equal(buscaLead.get("busca").get("id"), buscaId)
            );
            var predicado = builder.exists(vinculo);
            if (status != null) {
                predicado = builder.and(predicado, builder.equal(root.get("status"), status));
            }
            if (categoria != null) {
                predicado = builder.and(predicado, builder.equal(root.get("categoria"), categoria));
            }
            if (temperatura != null) {
                predicado = builder.and(predicado, builder.equal(root.get("temperatura"), temperatura));
            }
            return predicado;
        };
    }
}
