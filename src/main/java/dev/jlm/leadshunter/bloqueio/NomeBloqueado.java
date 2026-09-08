package dev.jlm.leadshunter.bloqueio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "nome_bloqueado")
@Getter
@NoArgsConstructor
public class NomeBloqueado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String termo;

    @Column(name = "termo_normalizado", nullable = false, length = 120, unique = true)
    private String termoNormalizado;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    public NomeBloqueado(String termo, String termoNormalizado) {
        this.termo = termo;
        this.termoNormalizado = termoNormalizado;
    }

    @PrePersist
    void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
    }
}
