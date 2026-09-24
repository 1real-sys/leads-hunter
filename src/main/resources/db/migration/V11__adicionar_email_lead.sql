ALTER TABLE leads
    ADD COLUMN email VARCHAR(320) NULL,
    ADD COLUMN email_capturado_em DATETIME(6) NULL,
    ADD COLUMN email_origem_host VARCHAR(255) NULL;
