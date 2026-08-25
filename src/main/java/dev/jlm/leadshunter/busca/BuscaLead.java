package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.lead.Lead;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "busca_lead", uniqueConstraints = {
    @UniqueConstraint(name = "uk_busca_lead_busca_lead", columnNames = {"busca_id", "lead_id"})
})
@Getter
@Setter
@NoArgsConstructor
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
}
