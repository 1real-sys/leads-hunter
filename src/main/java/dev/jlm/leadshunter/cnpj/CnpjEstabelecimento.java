package dev.jlm.leadshunter.cnpj;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cnpj_estabelecimento")
@Getter
@Setter
@NoArgsConstructor
public class CnpjEstabelecimento {

    @Id
    @Column(length = 14, nullable = false)
    private String cnpj;

    @Column(name = "cnpj_base", length = 8, nullable = false)
    private String cnpjBase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "cnpj_base",
        referencedColumnName = "cnpj_base",
        insertable = false,
        updatable = false,
        nullable = false
    )
    private CnpjEmpresa empresa;

    @Column(name = "nome_fantasia", length = 255)
    private String nomeFantasia;

    @Column(name = "nome_fantasia_normalizado", length = 255, nullable = false)
    private String nomeFantasiaNormalizado;

    @Column(length = 255)
    private String logradouro;

    @Column(name = "logradouro_normalizado", length = 255, nullable = false)
    private String logradouroNormalizado;

    @Column(length = 30)
    private String numero;

    @Column(length = 120)
    private String bairro;

    @Column(name = "bairro_normalizado", length = 120, nullable = false)
    private String bairroNormalizado;

    @Column(length = 8)
    private String cep;

    @Column(name = "municipio_codigo_ibge", length = 7, nullable = false)
    private String municipioCodigoIbge;

    @Column(length = 2, nullable = false)
    private String uf;

    @Column(name = "situacao_cadastral", length = 2, nullable = false)
    private String situacaoCadastral;

    @Column(name = "data_base", nullable = false)
    private LocalDate dataBase;
}
