package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.integracao.pesquisa.PesquisaInformacoesWebResultado;
import java.util.List;

public interface BuscaInformacoesPersistencia {

    List<BuscaInformacoesLead> carregarLeads(Long buscaId);

    PesquisaInformacoesWebResultado atualizarObservacoes(
        Long leadId,
        PesquisaInformacoesWebResultado resultado
    );
}
