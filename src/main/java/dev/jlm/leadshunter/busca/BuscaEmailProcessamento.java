package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.integracao.pesquisa.EmailLeadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuscaEmailProcessamento {
    private final BuscaEmailPersistencia persistencia;
    private final EmailLeadService extrator;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void executar(Long id, Long buscaId) {
        for (BuscaEmailLead lead : persistencia.carregar(buscaId)) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Busca de e-mails interrompida.");
            }
            EmailLeadService.Resultado resultado;
            if (lead.jaComEmail()) {
                resultado = new EmailLeadService.Resultado(EmailLeadService.Estado.SEM_EMAIL_ELEGIVEL,
                    null, null, false);
            } else if (lead.website() == null || lead.website().isBlank()) {
                resultado = new EmailLeadService.Resultado(EmailLeadService.Estado.SEM_SITE,
                    null, null, false);
            } else if (lead.dados() == null) {
                resultado = new EmailLeadService.Resultado(EmailLeadService.Estado.FALHA,
                    null, null, false);
            } else {
                try {
                    resultado = extrator.extrair(lead.dados());
                } catch (RuntimeException exception) {
                    resultado = new EmailLeadService.Resultado(EmailLeadService.Estado.FALHA,
                        null, null, false);
                }
            }
            persistencia.registrar(id, lead, resultado);
        }
        persistencia.concluir(id);
    }
}
