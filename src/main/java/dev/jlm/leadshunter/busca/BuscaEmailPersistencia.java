package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.integracao.pesquisa.EmailLeadService;
import dev.jlm.leadshunter.integracao.pesquisa.PesquisaLeadDados;
import dev.jlm.leadshunter.lead.EmailSiteHost;
import dev.jlm.leadshunter.lead.Lead;
import dev.jlm.leadshunter.lead.LeadRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuscaEmailPersistencia {
    private final BuscaLeadRepository vinculos;
    private final BuscaEmailExecucaoRepository execucoes;
    private final LeadRepository leads;

    @Transactional(readOnly = true)
    public List<BuscaEmailLead> carregar(Long buscaId) {
        return vinculos.findByBuscaIdOrderByScoreNaBuscaDesc(buscaId).stream()
            .map(BuscaLead::getLead).map(this::snapshot).toList();
    }

    private BuscaEmailLead snapshot(Lead lead) {
        boolean jaComEmail = lead.getEmail() != null && !lead.getEmail().isBlank();
        PesquisaLeadDados dados = null;
        if (!jaComEmail && lead.getWebsite() != null && !lead.getWebsite().isBlank()
            && lead.getNome() != null && !lead.getNome().isBlank() && lead.getCategoria() != null) {
            dados = PesquisaLeadDados.de(lead);
        }
        return new BuscaEmailLead(lead.getId(), jaComEmail, lead.getWebsite(), dados);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long iniciar(Long id) {
        BuscaEmailExecucao execucao = execucoes.bloquearPorId(id).orElseThrow();
        return execucao.iniciar() ? execucao.getBusca().getId() : null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Long id, BuscaEmailLead snapshot, EmailLeadService.Resultado resultado) {
        BuscaEmailExecucao execucao = execucoes.bloquearPorId(id).orElseThrow();
        Lead lead = leads.findById(snapshot.id()).orElseThrow();
        if (lead.getEmail() != null && !lead.getEmail().isBlank()) {
            execucao.ignorarJaComEmail();
            return;
        }
        if (lead.getWebsite() == null || lead.getWebsite().isBlank()) {
            execucao.registrar(EmailLeadService.Estado.SEM_SITE, false);
            return;
        }
        if (resultado.estado() == EmailLeadService.Estado.ENCONTRADO) {
            if (!resultado.origemHost().equals(EmailSiteHost.de(lead.getWebsite()))) {
                execucao.registrar(EmailLeadService.Estado.SEM_EMAIL_ELEGIVEL,
                    resultado.descartouDominioExterno());
                return;
            }
            lead.setEmail(resultado.email());
            lead.setEmailCapturadoEm(LocalDateTime.now());
            lead.setEmailOrigemHost(resultado.origemHost());
        }
        execucao.registrar(resultado.estado(), resultado.descartouDominioExterno());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void concluir(Long id) {
        execucoes.bloquearPorId(id).orElseThrow().concluir();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void falhar(Long id, String codigo, String mensagem) {
        execucoes.bloquearPorId(id).ifPresent(e -> e.falhar(codigo, mensagem));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recuperarInterrompidas() {
        return execucoes.interromperAtivas(BuscaEmailExecucaoService.ATIVAS,
            PesquisaInformacoesStatus.FALHA, "EMAIL_INTERROMPIDO",
            "A busca de e-mails foi interrompida. Inicie uma nova tentativa.", LocalDateTime.now());
    }
}
