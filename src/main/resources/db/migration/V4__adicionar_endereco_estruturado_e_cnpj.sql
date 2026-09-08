ALTER TABLE leads
    ADD COLUMN cep VARCHAR(8) NULL,
    ADD COLUMN logradouro VARCHAR(255) NULL,
    ADD COLUMN numero VARCHAR(30) NULL,
    ADD COLUMN bairro VARCHAR(120) NULL,
    ADD COLUMN cnpj VARCHAR(14) NULL,
    ADD COLUMN razao_social VARCHAR(255) NULL,
    ADD COLUMN cnpj_correspondido_em DATETIME(6) NULL,
    ADD CONSTRAINT chk_lead_cnpj_formato
        CHECK (cnpj IS NULL OR cnpj REGEXP '^[0-9]{14}$');

CREATE INDEX idx_lead_cnpj ON leads (cnpj);

CREATE TABLE cnpj_empresa (
    cnpj_base                  VARCHAR(8) PRIMARY KEY,
    razao_social               VARCHAR(255) NOT NULL,
    razao_social_normalizada   VARCHAR(255) NOT NULL,
    data_base                  DATE NOT NULL,

    CONSTRAINT chk_cnpj_empresa_base
        CHECK (cnpj_base REGEXP '^[0-9]{8}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cnpj_estabelecimento (
    cnpj                       VARCHAR(14) PRIMARY KEY,
    cnpj_base                  VARCHAR(8) NOT NULL,
    nome_fantasia              VARCHAR(255) NULL,
    nome_fantasia_normalizado  VARCHAR(255) NOT NULL,
    logradouro                 VARCHAR(255) NULL,
    logradouro_normalizado     VARCHAR(255) NOT NULL,
    numero                     VARCHAR(30) NULL,
    bairro                     VARCHAR(120) NULL,
    bairro_normalizado         VARCHAR(120) NOT NULL,
    cep                        VARCHAR(8) NULL,
    municipio_codigo_ibge      VARCHAR(7) NOT NULL,
    uf                         VARCHAR(2) NOT NULL,
    situacao_cadastral         VARCHAR(2) NOT NULL,
    data_base                  DATE NOT NULL,

    CONSTRAINT fk_cnpj_estabelecimento_empresa
        FOREIGN KEY (cnpj_base) REFERENCES cnpj_empresa(cnpj_base),
    CONSTRAINT chk_cnpj_estabelecimento_numero
        CHECK (cnpj REGEXP '^[0-9]{14}$'),
    CONSTRAINT chk_cnpj_estabelecimento_municipio
        CHECK (municipio_codigo_ibge REGEXP '^[0-9]{7}$'),
    CONSTRAINT chk_cnpj_estabelecimento_cep
        CHECK (cep IS NULL OR cep REGEXP '^[0-9]{8}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_cnpj_estabelecimento_municipio_cep
    ON cnpj_estabelecimento (municipio_codigo_ibge, situacao_cadastral, cep);
CREATE INDEX idx_cnpj_estabelecimento_municipio_numero
    ON cnpj_estabelecimento (municipio_codigo_ibge, situacao_cadastral, numero);
