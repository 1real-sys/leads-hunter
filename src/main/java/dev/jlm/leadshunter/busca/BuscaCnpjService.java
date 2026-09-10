package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.cnpj.CnpjService;
import dev.jlm.leadshunter.lead.Lead;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuscaCnpjService {

    private final BuscaRepository buscaRepository;
    private final BuscaLeadRepository buscaLeadRepository;
    private final CnpjService cnpjService;

    @Transactional
    public BuscaCnpjResponse buscarCnpj(Long buscaId) {
        if (!buscaRepository.existsById(buscaId)) {
            throw new BuscaNaoEncontradaException(buscaId);
        }
        var vinculos = buscaLeadRepository.findByBuscaIdOrderByScoreNaBuscaDesc(buscaId);
        int ignorados = 0;
        int encontrados = 0;
        for (BuscaLead vinculo : vinculos) {
            Lead lead = vinculo.getLead();
            if (lead.getCnpj() != null) {
                ignorados++;
                continue;
            }
            var correspondencia = cnpjService.corresponder(lead);
            if (correspondencia.isPresent()) {
                correspondencia.get().preencherLead(lead);
                encontrados++;
            }
        }
        // Os leads carregados estão gerenciados; a transação persiste somente os alterados.
        return new BuscaCnpjResponse(
            vinculos.size(), ignorados, encontrados, vinculos.size() - ignorados - encontrados
        );
    }
}
