package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.integracao.pesquisa.FormatadorObservacoesPesquisa;
import dev.jlm.leadshunter.integracao.pesquisa.GooglePesquisaWebBloqueadaException;
import dev.jlm.leadshunter.integracao.pesquisa.GooglePesquisaWebException;
import dev.jlm.leadshunter.integracao.pesquisa.PesquisaInformacoesGateway;
import dev.jlm.leadshunter.integracao.pesquisa.PesquisaInformacoesWebResultado;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuscaInformacoesService {

    private static final int MAXIMO_FALHAS_TECNICAS_CONSECUTIVAS = 3;

    private final BuscaInformacoesPersistencia persistencia;
    private final PesquisaInformacoesGateway pesquisa;
    private final FormatadorObservacoesPesquisa formatador;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BuscaInformacoesResponse buscarInformacoes(Long buscaId) {
        return buscarInformacoes(buscaId, (passo, erro) -> passo.get());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BuscaInformacoesResponse buscarInformacoes(Long buscaId, BuscaInformacoesProgresso progresso) {
        List<BuscaInformacoesLead> leads = persistencia.carregarLeads(buscaId);
        Contadores contadores = new Contadores(leads.size());
        progresso.registrar(contadores::resposta, null);
        int falhasConsecutivas = 0;

        for (int indice = 0; indice < leads.size(); indice++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Pesquisa interrompida.");
            }
            BuscaInformacoesLead lead = leads.get(indice);
            if (formatador.possuiInstagramESiteValidos(lead.observacoes())) {
                contadores.ignorarCompleto();
                progresso.registrar(contadores::resposta, null);
                continue;
            }

            try {
                PesquisaInformacoesWebResultado encontrado = pesquisa.pesquisar(lead.dados());
                progresso.registrar(() -> {
                    PesquisaInformacoesWebResultado persistido =
                        persistencia.atualizarObservacoes(lead.leadId(), encontrado);
                    contadores.processar(persistido);
                    return contadores.resposta();
                }, null);
                falhasConsecutivas = 0;
            } catch (GooglePesquisaWebBloqueadaException exception) {
                contadores.registrarFalha();
                progresso.registrar(contadores::resposta, PesquisaInformacoesErro.deFalhaExterna(exception));
                contabilizarRestantesSemPesquisar(leads, indice + 1, contadores, progresso);
                break;
            } catch (GooglePesquisaWebException exception) {
                contadores.registrarFalha();
                progresso.registrar(contadores::resposta, PesquisaInformacoesErro.deFalhaExterna(exception));
                falhasConsecutivas++;
                if (falhasConsecutivas >= MAXIMO_FALHAS_TECNICAS_CONSECUTIVAS) {
                    contabilizarRestantesSemPesquisar(leads, indice + 1, contadores, progresso);
                    break;
                }
            }
        }
        return contadores.resposta();
    }

    private void contabilizarRestantesSemPesquisar(
        List<BuscaInformacoesLead> leads,
        int proximoIndice,
        Contadores contadores,
        BuscaInformacoesProgresso progresso
    ) {
        for (int indice = proximoIndice; indice < leads.size(); indice++) {
            if (formatador.possuiInstagramESiteValidos(leads.get(indice).observacoes())) {
                contadores.ignorarCompleto();
            } else {
                contadores.registrarFalha();
            }
            progresso.registrar(contadores::resposta, null);
        }
    }

    private static final class Contadores {

        private final int totalLeads;
        private int processados;
        private int ignoradosJaCompletos;
        private int comInstagram;
        private int comSite;
        private int comAmbos;
        private int semInformacoes;
        private int falhas;

        private Contadores(int totalLeads) {
            this.totalLeads = totalLeads;
        }

        private void ignorarCompleto() {
            ignoradosJaCompletos++;
        }

        private void registrarFalha() {
            falhas++;
        }

        private void processar(PesquisaInformacoesWebResultado resultado) {
            processados++;
            boolean instagram = resultado.instagram().isPresent();
            boolean site = resultado.siteProprio().isPresent();
            if (instagram) {
                comInstagram++;
            }
            if (site) {
                comSite++;
            }
            if (instagram && site) {
                comAmbos++;
            }
            if (!instagram && !site) {
                semInformacoes++;
            }
        }

        private BuscaInformacoesResponse resposta() {
            return new BuscaInformacoesResponse(
                totalLeads,
                processados,
                ignoradosJaCompletos,
                comInstagram,
                comSite,
                comAmbos,
                semInformacoes,
                falhas
            );
        }
    }
}
