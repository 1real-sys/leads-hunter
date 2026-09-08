package dev.jlm.leadshunter.cnpj;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CnpjEmpresaRepository extends JpaRepository<CnpjEmpresa, String> {
}
