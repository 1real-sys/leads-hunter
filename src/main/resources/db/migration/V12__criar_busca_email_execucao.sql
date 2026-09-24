CREATE TABLE busca_email_execucao (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    busca_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    iniciado_em DATETIME(6),
    atualizado_em DATETIME(6) NOT NULL,
    terminado_em DATETIME(6),
    total_leads INT NOT NULL DEFAULT 0,
    ignorados_ja_com_email INT NOT NULL DEFAULT 0,
    ignorados_sem_site INT NOT NULL DEFAULT 0,
    processados INT NOT NULL DEFAULT 0,
    encontrados INT NOT NULL DEFAULT 0,
    sem_email_elegivel INT NOT NULL DEFAULT 0,
    descartados_dominio_externo INT NOT NULL DEFAULT 0,
    falhas INT NOT NULL DEFAULT 0,
    erro_codigo VARCHAR(50),
    erro_mensagem VARCHAR(255),
    busca_ativa_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('PENDENTE', 'EM_ANDAMENTO') THEN busca_id ELSE NULL END
    ) STORED,
    CONSTRAINT fk_email_execucao_busca FOREIGN KEY (busca_id) REFERENCES busca(id),
    CONSTRAINT uk_email_execucao_ativa UNIQUE (busca_ativa_id),
    CONSTRAINT ck_email_execucao_status CHECK (
        status IN ('PENDENTE', 'EM_ANDAMENTO', 'CONCLUIDA', 'CONCLUIDA_COM_FALHAS', 'FALHA')
    ),
    CONSTRAINT ck_email_execucao_progresso CHECK (
        total_leads >= 0 AND ignorados_ja_com_email >= 0 AND ignorados_sem_site >= 0
        AND processados >= 0 AND encontrados >= 0 AND sem_email_elegivel >= 0
        AND descartados_dominio_externo >= 0 AND falhas >= 0
        AND ignorados_ja_com_email + ignorados_sem_site + processados <= total_leads
        AND processados = encontrados + sem_email_elegivel + falhas
        AND descartados_dominio_externo <= processados
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_email_execucao_busca_id ON busca_email_execucao (busca_id, id);
CREATE INDEX idx_email_execucao_status ON busca_email_execucao (status);
