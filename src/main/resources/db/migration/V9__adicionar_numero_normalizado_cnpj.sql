ALTER TABLE cnpj_estabelecimento
    ADD COLUMN numero_normalizado VARCHAR(30) NULL;

-- Keep this expression in sync with CnpjNumeroNormalizer.normalizar().
-- A value with no digit is intentionally stored as NULL; sentinels are a
-- diagnostic classification, not a different matching value.
UPDATE cnpj_estabelecimento
SET numero_normalizado = CASE
    WHEN numero IS NULL THEN NULL
    WHEN REGEXP_REPLACE(UPPER(TRIM(numero)), '[^0-9A-Z]', '') NOT REGEXP '[0-9]'
        THEN NULL
    WHEN REGEXP_REPLACE(UPPER(TRIM(numero)), '[^0-9A-Z]', '') REGEXP '^[0-9]+$'
        THEN CASE
            WHEN TRIM(LEADING '0' FROM
                REGEXP_REPLACE(UPPER(TRIM(numero)), '[^0-9A-Z]', '')
            ) = '' THEN '0'
            ELSE TRIM(LEADING '0' FROM
                REGEXP_REPLACE(UPPER(TRIM(numero)), '[^0-9A-Z]', '')
            )
        END
    WHEN REGEXP_REPLACE(UPPER(TRIM(numero)), '[^0-9A-Z]', '') REGEXP '^0*[1-9]'
        THEN REGEXP_REPLACE(
            REGEXP_REPLACE(UPPER(TRIM(numero)), '[^0-9A-Z]', ''),
            '^0+',
            ''
        )
    ELSE REGEXP_REPLACE(UPPER(TRIM(numero)), '[^0-9A-Z]', '')
END;

CREATE INDEX idx_cnpj_estabelecimento_municipio_numero_normalizado
    ON cnpj_estabelecimento (municipio_codigo_ibge, situacao_cadastral, numero_normalizado);

CREATE INDEX idx_cnpj_estabelecimento_municipio_cep_numero_normalizado
    ON cnpj_estabelecimento (
        municipio_codigo_ibge,
        situacao_cadastral,
        cep,
        numero_normalizado
    );
