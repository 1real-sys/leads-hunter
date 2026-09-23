package dev.jlm.leadshunter.cnpj;

public enum CnpjMatchClassificacao {
    CONSULTA_TRUNCADA,
    ENDERECO_UNICO,
    ENDERECO_DESEMPATADO_POR_NOME,
    ENDERECO_MULTIPLO,
    NOME_RESOLVE,
    SEM_CANDIDATO,
    SEM_CORRESPONDENCIA
}
