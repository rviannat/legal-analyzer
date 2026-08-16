CREATE TABLE datajud_pesquisas (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tipo VARCHAR(30) NOT NULL,
    parametro VARCHAR(255),
    tribunal VARCHAR(30),
    assunto VARCHAR(255),
    numero_cnj VARCHAR(30),
    classe_codigo VARCHAR(50),
    classe_nome VARCHAR(255),
    grau VARCHAR(30),
    orgao_julgador VARCHAR(255),
    resultado_encontrado BOOLEAN NOT NULL,
    processado BOOLEAN NOT NULL DEFAULT FALSE,
    analise_id VARCHAR(36),
    mensagem VARCHAR(2000),
    consultado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_datajud_pesquisas_cnj ON datajud_pesquisas(numero_cnj);
CREATE INDEX idx_datajud_pesquisas_tipo_parametro ON datajud_pesquisas(tipo, parametro);
CREATE INDEX idx_datajud_pesquisas_criado_em ON datajud_pesquisas(criado_em DESC);
