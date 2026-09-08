package dev.jlm.leadshunter.cnpj;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
