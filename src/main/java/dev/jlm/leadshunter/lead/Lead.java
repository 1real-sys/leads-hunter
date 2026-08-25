package dev.jlm.leadshunter.lead;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "leads")
@Getter
@Setter
@NoArgsConstructor
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_place_id", length = 255, unique = true, nullable = false)
    private String googlePlaceId;

    @Column(length = 255)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private CategoriaNegocio categoria;

    @Column(name = "endereco_formatado", length = 255)
    private String enderecoFormatado;

    @Column(length = 30)
    private String telefone;

    @Column(name = "telefone_normalizado", length = 20)
    private String telefoneNormalizado;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "rating_google", precision = 3, scale = 2)
    private BigDecimal ratingGoogle;

    @Column(name = "total_reviews")
    private Integer totalReviews;

    @Column
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Temperatura temperatura;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StatusFunil status;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "ultimo_contato_em")
    private LocalDateTime ultimoContatoEm;

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
