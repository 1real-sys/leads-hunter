CREATE TABLE nome_bloqueado (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    termo               VARCHAR(120) NOT NULL,
    termo_normalizado   VARCHAR(120) NOT NULL,
    criado_em           DATETIME NOT NULL,

    CONSTRAINT uk_nome_bloqueado_termo_normalizado UNIQUE (termo_normalizado)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
