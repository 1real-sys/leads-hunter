package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.lead.Lead;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "busca_lead", uniqueConstraints = {
    @UniqueConstraint(name = "uk_busca_lead_busca_lead", columnNames = {"busca_id", "lead_id"})
})
public class BuscaLead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "busca_id", nullable = false)
    private Busca busca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @Column(name = "score_na_busca")
    private Integer scoreNaBusca;

    @Column(name = "temperatura_na_busca", length = 10)
    private String temperaturaNaBusca;

    @Column(name = "encontrado_em")
    private LocalDateTime encontradoEm;

    @PrePersist
    void prePersist() {
        this.encontradoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Busca getBusca() {
        return busca;
    }

    public void setBusca(Busca busca) {
        this.busca = busca;
    }

    public Lead getLead() {
        return lead;
    }

    public void setLead(Lead lead) {
        this.lead = lead;
    }

    public Integer getScoreNaBusca() {
        return scoreNaBusca;
    }

    public void setScoreNaBusca(Integer scoreNaBusca) {
        this.scoreNaBusca = scoreNaBusca;
    }

    public String getTemperaturaNaBusca() {
        return temperaturaNaBusca;
    }

    public void setTemperaturaNaBusca(String temperaturaNaBusca) {
        this.temperaturaNaBusca = temperaturaNaBusca;
    }

    public LocalDateTime getEncontradoEm() {
        return encontradoEm;
    }

    public void setEncontradoEm(LocalDateTime encontradoEm) {
        this.encontradoEm = encontradoEm;
    }
}
