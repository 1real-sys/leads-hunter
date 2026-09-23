ALTER TABLE leads
    ADD COLUMN cnpj_origem VARCHAR(30) NULL,
    ADD CONSTRAINT chk_lead_cnpj_origem
        CHECK (
            cnpj_origem IS NULL
            OR (
                cnpj IS NOT NULL
                AND cnpj_origem IN ('ENDERECO_EXATO', 'NOME_ENDERECO')
            )
        );
