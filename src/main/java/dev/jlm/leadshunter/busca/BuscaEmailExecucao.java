package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.integracao.pesquisa.EmailLeadService;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "busca_email_execucao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuscaEmailExecucao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "busca_id", nullable = false)
    private Busca busca;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PesquisaInformacoesStatus status;
    @Column(nullable = false, updatable = false) private LocalDateTime criadoEm;
    private LocalDateTime iniciadoEm;
    @Column(nullable = false) private LocalDateTime atualizadoEm;
    private LocalDateTime terminadoEm;
    @Column(nullable = false) private int totalLeads;
    @Column(nullable = false) private int ignoradosJaComEmail;
    @Column(nullable = false) private int ignoradosSemSite;
    @Column(nullable = false) private int processados;
    @Column(nullable = false) private int encontrados;
    @Column(nullable = false) private int semEmailElegivel;
    @Column(nullable = false) private int descartadosDominioExterno;
    @Column(nullable = false) private int falhas;
    @Column(length = 50) private String erroCodigo;
    @Column(length = 255) private String erroMensagem;

    public BuscaEmailExecucao(Busca busca, int totalLeads) {
        this.busca = busca;
        this.totalLeads = totalLeads;
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

    public void registrar(EmailLeadService.Estado estado, boolean descartouExterno) {
        if (status != PesquisaInformacoesStatus.EM_ANDAMENTO) {
            throw new IllegalStateException("A busca de e-mails não está em andamento.");
        }
        switch (estado) {
            case SEM_SITE -> ignoradosSemSite++;
            case ENCONTRADO -> { processados++; encontrados++; }
            case SEM_EMAIL_ELEGIVEL -> { processados++; semEmailElegivel++; }
            case FALHA -> { processados++; falhas++; }
        }
        if (descartouExterno) descartadosDominioExterno++;
        atualizadoEm = LocalDateTime.now();
    }

    public void ignorarJaComEmail() {
        if (status != PesquisaInformacoesStatus.EM_ANDAMENTO) {
            throw new IllegalStateException("A busca de e-mails não está em andamento.");
        }
        ignoradosJaComEmail++;
        atualizadoEm = LocalDateTime.now();
    }

    public void concluir() {
        if (status != PesquisaInformacoesStatus.EM_ANDAMENTO) return;
        if (ignoradosJaComEmail + ignoradosSemSite + processados != totalLeads) {
            throw new IllegalStateException("O progresso da busca de e-mails está incompleto.");
        }
        status = falhas == 0 ? PesquisaInformacoesStatus.CONCLUIDA
            : falhas == totalLeads ? PesquisaInformacoesStatus.FALHA
            : PesquisaInformacoesStatus.CONCLUIDA_COM_FALHAS;
        terminadoEm = LocalDateTime.now();
        atualizadoEm = terminadoEm;
    }

    public void falhar(String codigo, String mensagem) {
        if (!status.ativa()) return;
        status = PesquisaInformacoesStatus.FALHA;
        erroCodigo = codigo;
        erroMensagem = mensagem;
        terminadoEm = LocalDateTime.now();
        atualizadoEm = terminadoEm;
    }
}
