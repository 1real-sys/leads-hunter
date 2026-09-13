CREATE TABLE pesquisa_informacoes_execucao (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    busca_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    iniciado_em DATETIME(6),
    atualizado_em DATETIME(6) NOT NULL,
    terminado_em DATETIME(6),
    total_leads INT NOT NULL DEFAULT 0,
    processados INT NOT NULL DEFAULT 0,
    ignorados_ja_completos INT NOT NULL DEFAULT 0,
    com_instagram INT NOT NULL DEFAULT 0,
    com_site INT NOT NULL DEFAULT 0,
    com_ambos INT NOT NULL DEFAULT 0,
    sem_informacoes INT NOT NULL DEFAULT 0,
    falhas INT NOT NULL DEFAULT 0,
    erro_codigo VARCHAR(50),
    erro_mensagem VARCHAR(255),
    busca_ativa_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('PENDENTE', 'EM_ANDAMENTO') THEN busca_id ELSE NULL END
    ) STORED,
    CONSTRAINT fk_pesquisa_execucao_busca FOREIGN KEY (busca_id) REFERENCES busca(id),
    CONSTRAINT uk_pesquisa_execucao_ativa UNIQUE (busca_ativa_id),
    CONSTRAINT ck_pesquisa_execucao_status CHECK (
        status IN ('PENDENTE', 'EM_ANDAMENTO', 'CONCLUIDA', 'CONCLUIDA_COM_FALHAS', 'FALHA')
    ),
    CONSTRAINT ck_pesquisa_execucao_progresso CHECK (
        total_leads >= 0 AND processados >= 0 AND ignorados_ja_completos >= 0 AND falhas >= 0
        AND processados + ignorados_ja_completos + falhas <= total_leads
        AND com_instagram >= 0 AND com_site >= 0 AND com_ambos >= 0 AND sem_informacoes >= 0
        AND com_instagram <= processados AND com_site <= processados
        AND com_ambos <= com_instagram AND com_ambos <= com_site
        AND processados = com_instagram + com_site - com_ambos + sem_informacoes
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_pesquisa_execucao_busca_id ON pesquisa_informacoes_execucao (busca_id, id);
CREATE INDEX idx_pesquisa_execucao_status ON pesquisa_informacoes_execucao (status);
