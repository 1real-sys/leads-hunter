package dev.jlm.leadshunter.busca;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "busca")
@Getter
@Setter
@NoArgsConstructor
public class Busca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "endereco_base", length = 255)
    private String enderecoBase;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "raio_km")
    private Integer raioKm;

    @Column(name = "categorias_buscadas", length = 500)
    private String categoriasBuscadas;

    @Column(name = "total_encontrados")
    private Integer totalEncontrados;

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm;

    @OneToMany(mappedBy = "busca", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BuscaLead> buscaLeads = new ArrayList<>();

    @PrePersist
    void prePersist() {
        this.criadoEm = LocalDateTime.now();
    }
}
