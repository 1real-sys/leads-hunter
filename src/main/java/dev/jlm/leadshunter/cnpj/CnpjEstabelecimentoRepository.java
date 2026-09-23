package dev.jlm.leadshunter.cnpj;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CnpjEstabelecimentoRepository
    extends JpaRepository<CnpjEstabelecimento, String> {

    @EntityGraph(attributePaths = "empresa")
    Slice<CnpjEstabelecimento> findByMunicipioCodigoIbgeAndSituacaoCadastralAndCep(
        String municipioCodigoIbge,
        String situacaoCadastral,
        String cep,
        Pageable pageable
    );

    @EntityGraph(attributePaths = "empresa")
    Slice<CnpjEstabelecimento> findByMunicipioCodigoIbgeAndSituacaoCadastralAndNumero(
        String municipioCodigoIbge,
        String situacaoCadastral,
        String numero,
        Pageable pageable
    );

    @EntityGraph(attributePaths = "empresa")
    Slice<CnpjEstabelecimento>
        findByMunicipioCodigoIbgeAndSituacaoCadastralAndNumeroNormalizado(
            String municipioCodigoIbge,
            String situacaoCadastral,
            String numeroNormalizado,
            Pageable pageable
        );

    @EntityGraph(attributePaths = "empresa")
    Slice<CnpjEstabelecimento>
        findByMunicipioCodigoIbgeAndSituacaoCadastralAndCepAndNumeroNormalizado(
            String municipioCodigoIbge,
            String situacaoCadastral,
            String cep,
            String numeroNormalizado,
            Pageable pageable
        );

    @Query("""
        SELECT MAX(estabelecimento.dataBase)
        FROM CnpjEstabelecimento estabelecimento
        WHERE estabelecimento.municipioCodigoIbge = :municipioCodigoIbge
          AND estabelecimento.situacaoCadastral = :situacaoCadastral
        """)
    Optional<LocalDate> findDataBaseAtual(
        String municipioCodigoIbge,
        String situacaoCadastral
    );

    @Query("""
        SELECT estabelecimento.numero AS numero,
               COUNT(estabelecimento) AS quantidade
        FROM CnpjEstabelecimento estabelecimento
        WHERE estabelecimento.numero IS NOT NULL
          AND TRIM(estabelecimento.numero) <> ''
          AND estabelecimento.numeroNormalizado IS NULL
        GROUP BY estabelecimento.numero
        ORDER BY COUNT(estabelecimento) DESC, estabelecimento.numero
        """)
    List<NumeroDescartadoContagem> listarNumerosDescartados();

    interface NumeroDescartadoContagem {
        String getNumero();

        long getQuantidade();
    }
}
