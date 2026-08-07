package dev.jlm.leadshunter.lead;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "leads")
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGooglePlaceId() {
        return googlePlaceId;
    }

    public void setGooglePlaceId(String googlePlaceId) {
        this.googlePlaceId = googlePlaceId;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public CategoriaNegocio getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaNegocio categoria) {
        this.categoria = categoria;
    }

    public String getEnderecoFormatado() {
        return enderecoFormatado;
    }

    public void setEnderecoFormatado(String enderecoFormatado) {
        this.enderecoFormatado = enderecoFormatado;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getTelefoneNormalizado() {
        return telefoneNormalizado;
    }

    public void setTelefoneNormalizado(String telefoneNormalizado) {
        this.telefoneNormalizado = telefoneNormalizado;
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

    public BigDecimal getRatingGoogle() {
        return ratingGoogle;
    }

    public void setRatingGoogle(BigDecimal ratingGoogle) {
        this.ratingGoogle = ratingGoogle;
    }

    public Integer getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(Integer totalReviews) {
        this.totalReviews = totalReviews;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Temperatura getTemperatura() {
        return temperatura;
    }

    public void setTemperatura(Temperatura temperatura) {
        this.temperatura = temperatura;
    }

    public StatusFunil getStatus() {
        return status;
    }

    public void setStatus(StatusFunil status) {
        this.status = status;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public LocalDateTime getUltimoContatoEm() {
        return ultimoContatoEm;
    }

    public void setUltimoContatoEm(LocalDateTime ultimoContatoEm) {
        this.ultimoContatoEm = ultimoContatoEm;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}
