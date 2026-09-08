package dev.jlm.leadshunter.cnpj;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cnpj_empresa")
@Getter
@Setter
@NoArgsConstructor
public class CnpjEmpresa {

    @Id
    @Column(name = "cnpj_base", length = 8, nullable = false)
    private String cnpjBase;

    @Column(name = "razao_social", length = 255, nullable = false)
    private String razaoSocial;

    @Column(name = "razao_social_normalizada", length = 255, nullable = false)
    private String razaoSocialNormalizada;

    @Column(name = "data_base", nullable = false)
    private LocalDate dataBase;
}
