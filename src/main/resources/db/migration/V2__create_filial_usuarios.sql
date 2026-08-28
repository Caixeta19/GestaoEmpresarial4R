-- Suporte à tela "Início" da venda: filial, usuário/vendedor (com reautenticação),
-- flag de estoque avançado, score do cliente e numeração da nota/comprovante.

CREATE TABLE filiais (
                         id      BIGSERIAL PRIMARY KEY,
                         codigo  VARCHAR(20)  NOT NULL,
                         nome    VARCHAR(150) NOT NULL,
                         ativo   BOOLEAN      NOT NULL DEFAULT TRUE,
                         CONSTRAINT uk_filial_codigo UNIQUE (codigo)
);

CREATE TABLE usuarios (
                          id          BIGSERIAL PRIMARY KEY,
                          nome        VARCHAR(150) NOT NULL,
                          login       VARCHAR(150) NOT NULL,
                          email       VARCHAR(150) NOT NULL,
                          senha_hash  VARCHAR(100) NOT NULL,
                          filial      VARCHAR(100) NOT NULL,
                          cargo       VARCHAR(100) NOT NULL,
                          ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
                          CONSTRAINT uk_usuario_email UNIQUE (email),
                          CONSTRAINT uk_usuario_login UNIQUE (login)
);

-- Colunas complementares em vendas
ALTER TABLE vendas
    ADD COLUMN filial_id                     BIGINT,
    ADD COLUMN usuario_id                    BIGINT,
    ADD COLUMN estoque_avancado              BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN status_score_cliente          VARCHAR(20) NOT NULL DEFAULT 'NAO_REALIZADA',
    ADD COLUMN numero_serie_nota             VARCHAR(10),
    ADD COLUMN numero_nota                   VARCHAR(20);

ALTER TABLE vendas
    ADD CONSTRAINT fk_vendas_filial FOREIGN KEY (filial_id) REFERENCES filiais (id),
    ADD CONSTRAINT fk_vendas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    ADD CONSTRAINT chk_vendas_status_score_cliente
        CHECK (status_score_cliente IN ('NAO_REALIZADA', 'CONSULTANDO', 'APROVADO', 'REPROVADO'));

-- Torna as chaves estrangeiras obrigatórias
ALTER TABLE vendas
    ALTER COLUMN filial_id SET NOT NULL,
ALTER COLUMN usuario_id SET NOT NULL;

CREATE INDEX idx_vendas_filial_id ON vendas (filial_id);
CREATE INDEX idx_vendas_usuario_id ON vendas (usuario_id);