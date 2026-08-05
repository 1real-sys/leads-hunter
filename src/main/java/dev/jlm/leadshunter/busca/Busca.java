package dev.jlm.leadshunter.busca;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "busca")
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEnderecoBase() {
        return enderecoBase;
    }

    public void setEnderecoBase(String enderecoBase) {
        this.enderecoBase = enderecoBase;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public Integer getRaioKm() {
        return raioKm;
    }

    public void setRaioKm(Integer raioKm) {
        this.raioKm = raioKm;
    }

    public String getCategoriasBuscadas() {
        return categoriasBuscadas;
    }

    public void setCategoriasBuscadas(String categoriasBuscadas) {
        this.categoriasBuscadas = categoriasBuscadas;
    }

    public Integer getTotalEncontrados() {
        return totalEncontrados;
    }

    public void setTotalEncontrados(Integer totalEncontrados) {
        this.totalEncontrados = totalEncontrados;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public List<BuscaLead> getBuscaLeads() {
        return buscaLeads;
    }

    public void setBuscaLeads(List<BuscaLead> buscaLeads) {
        this.buscaLeads = buscaLeads;
    }
}
