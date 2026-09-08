ALTER TABLE leads
    ADD COLUMN cnpj_data_base DATE NULL,
    ADD COLUMN cnpj_confianca DECIMAL(5, 4) NULL,
    ADD CONSTRAINT chk_lead_cnpj_data_base
        CHECK (cnpj_data_base IS NULL OR cnpj IS NOT NULL),
    ADD CONSTRAINT chk_lead_cnpj_confianca
        CHECK (
            cnpj_confianca IS NULL
            OR (cnpj IS NOT NULL AND cnpj_confianca BETWEEN 0.0000 AND 1.0000)
        );

-- Remove correspondencias produzidas pela antiga carga bootstrap sem proveniencia.
-- Os valores poderao ser correspondidos novamente quando a carga mensal real existir.
UPDATE leads
SET cnpj = NULL,
    razao_social = NULL,
    cnpj_correspondido_em = NULL,
    cnpj_data_base = NULL,
    cnpj_confianca = NULL
WHERE cnpj IN ('23502037000113', '23681920000118', '43869215000156');

DELETE FROM cnpj_estabelecimento
WHERE cnpj IN ('23502037000113', '23681920000118', '43869215000156');

DELETE FROM cnpj_empresa
WHERE NOT EXISTS (
    SELECT 1
    FROM cnpj_estabelecimento
    WHERE cnpj_estabelecimento.cnpj_base = cnpj_empresa.cnpj_base
);
