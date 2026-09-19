package dev.jlm.leadshunter.busca;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pesquisa_informacoes_execucao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PesquisaInformacoesExecucao {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "busca_id", nullable = false)
    private Busca busca;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PesquisaInformacoesStatus status;
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;
    private LocalDateTime iniciadoEm;
    @Column(nullable = false)
    private LocalDateTime atualizadoEm;
    private LocalDateTime terminadoEm;
    @Column(nullable = false) private int totalLeads;
    @Column(nullable = false) private int processados;
    @Column(nullable = false) private int ignoradosJaCompletos;
    @Column(nullable = false) private int comInstagram;
    @Column(nullable = false) private int comSite;
    @Column(nullable = false) private int comAmbos;
    @Column(nullable = false) private int semInformacoes;
    @Column(nullable = false) private int falhas;
    @Column(name = "usar_brave", nullable = false) private boolean usarBrave;
    @Enumerated(EnumType.STRING) @Column(length = 50)
    private PesquisaInformacoesErro erroCodigo;
    @Column(length = 255)
    private String erroMensagem;

    public PesquisaInformacoesExecucao(Busca busca, int totalLeads) {
        this(busca, totalLeads, true);
    }

    public PesquisaInformacoesExecucao(Busca busca, int totalLeads, boolean usarBrave) {
        this.busca = busca;
        this.totalLeads = totalLeads;
        this.usarBrave = usarBrave;
        status = PesquisaInformacoesStatus.PENDENTE;
        criadoEm = LocalDateTime.now();
        atualizadoEm = criadoEm;
    }

    public boolean iniciar() {
        if (status != PesquisaInformacoesStatus.PENDENTE) return false;
        status = PesquisaInformacoesStatus.EM_ANDAMENTO;
        iniciadoEm = LocalDateTime.now();
        atualizadoEm = iniciadoEm;
        return true;
    }

    public void atualizar(BuscaInformacoesResponse resumo, PesquisaInformacoesErro erro) {
        if (status != PesquisaInformacoesStatus.EM_ANDAMENTO) {
            throw new IllegalStateException("A execução não está em andamento.");
        }
        totalLeads = resumo.totalLeads();
        processados = resumo.processados();
        ignoradosJaCompletos = resumo.ignoradosJaCompletos();
        comInstagram = resumo.comInstagram();
        comSite = resumo.comSite();
        comAmbos = resumo.comAmbos();
        semInformacoes = resumo.semInformacoes();
        falhas = resumo.falhas();
        registrarErro(erro);
        atualizadoEm = LocalDateTime.now();
    }

    public void concluir() {
        if (status != PesquisaInformacoesStatus.EM_ANDAMENTO) return;
        if (processados + ignoradosJaCompletos + falhas != totalLeads) {
            throw new IllegalStateException("O progresso da execução está incompleto.");
        }
        status = falhas == 0 ? PesquisaInformacoesStatus.CONCLUIDA
            : processados + ignoradosJaCompletos == 0 ? PesquisaInformacoesStatus.FALHA
            : PesquisaInformacoesStatus.CONCLUIDA_COM_FALHAS;
        terminadoEm = LocalDateTime.now();
        atualizadoEm = terminadoEm;
    }

    public void falhar(PesquisaInformacoesErro erro) {
        if (!status.ativa()) return;
        status = PesquisaInformacoesStatus.FALHA;
        registrarErro(erro);
        terminadoEm = LocalDateTime.now();
        atualizadoEm = terminadoEm;
    }

    private void registrarErro(PesquisaInformacoesErro erro) {
        if (erro != null) {
            erroCodigo = erro;
            erroMensagem = erro.mensagem();
        }
    }
}
