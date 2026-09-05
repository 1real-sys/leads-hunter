ALTER TABLE leads
    ADD COLUMN municipio_codigo_ibge VARCHAR(7) NULL,
    ADD COLUMN municipio_nome VARCHAR(120) NULL,
    ADD COLUMN uf VARCHAR(2) NULL,
    ADD COLUMN idhm DECIMAL(4, 3) NULL,
    ADD COLUMN idhm_referencia SMALLINT NULL;

CREATE INDEX idx_lead_uf ON leads (uf);
CREATE INDEX idx_lead_idhm ON leads (idhm);
