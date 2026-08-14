CREATE TABLE analises_especializadas (
    id VARCHAR(36) PRIMARY KEY,
    analise_base_id VARCHAR(36) NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    status VARCHAR(40) NOT NULL,
    progresso INTEGER NOT NULL,
    etapa VARCHAR(160),
    mensagem VARCHAR(2000),
    resultado_json TEXT,
    relatorio_pdf BYTEA,
    logs_json TEXT,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_analise_especializada_base FOREIGN KEY (analise_base_id) REFERENCES processos(id)
);

CREATE INDEX idx_analises_especializadas_base ON analises_especializadas(analise_base_id);
CREATE INDEX idx_analises_especializadas_status ON analises_especializadas(status);
