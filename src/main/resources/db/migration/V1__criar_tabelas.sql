CREATE TABLE busca (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    endereco_base    VARCHAR(255),
    latitude         DECIMAL(10, 7),
    longitude        DECIMAL(10, 7),
    raio_km          INT,
    categorias_buscadas VARCHAR(500),
    total_encontrados   INT,
    criado_em        DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_busca_criado_em ON busca (criado_em);

CREATE TABLE lead (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    google_place_id     VARCHAR(255) NOT NULL,
    nome                VARCHAR(255),
    categoria           VARCHAR(50),
    endereco_formatado  VARCHAR(255),
    telefone            VARCHAR(30),
    telefone_normalizado VARCHAR(20),
    latitude            DECIMAL(10, 7),
    longitude           DECIMAL(10, 7),
    rating_google       DECIMAL(3, 2),
    total_reviews       INT,
    score               INT,
    temperatura         VARCHAR(10),
    status              VARCHAR(20),
    observacoes         TEXT,
    ultimo_contato_em   DATETIME,
    criado_em           DATETIME NOT NULL,
    atualizado_em       DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE UNIQUE INDEX uk_lead_google_place_id ON lead (google_place_id);
CREATE INDEX idx_lead_status        ON lead (status);
CREATE INDEX idx_lead_categoria     ON lead (categoria);
CREATE INDEX idx_lead_temperatura   ON lead (temperatura);
CREATE INDEX idx_lead_score         ON lead (score);

CREATE TABLE busca_lead (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    busca_id               BIGINT NOT NULL,
    lead_id                BIGINT NOT NULL,
    score_na_busca         INT,
    temperatura_na_busca   VARCHAR(10),
    encontrado_em          DATETIME,

    CONSTRAINT fk_busca_lead_busca FOREIGN KEY (busca_id) REFERENCES busca(id),
    CONSTRAINT fk_busca_lead_lead  FOREIGN KEY (lead_id)  REFERENCES lead(id),
    CONSTRAINT uk_busca_lead_busca_lead UNIQUE (busca_id, lead_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_busca_lead_lead_id      ON busca_lead (lead_id);
CREATE INDEX idx_busca_lead_encontrado_em ON busca_lead (encontrado_em);
