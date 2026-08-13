CREATE TABLE processos (
    id VARCHAR(36) PRIMARY KEY,
    nome_arquivo VARCHAR(500) NOT NULL,
    numero_cnj VARCHAR(30),
    status VARCHAR(40) NOT NULL,
    progresso INTEGER NOT NULL DEFAULT 0,
    etapa VARCHAR(120),
    mensagem VARCHAR(2000),
    arquivo_pdf BYTEA NOT NULL,
    relatorio_pdf BYTEA,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_processos_numero_cnj ON processos(numero_cnj);
CREATE INDEX idx_processos_status ON processos(status);
