-- V2__add_filial_vendedor_dados_iniciais_venda.sql
-- Suporte à tela "Início" da venda: filial, vendedor (com reautenticação),
-- flag de estoque avançado, score do cliente e numeração do comprovante.

CREATE TABLE filiais (
                         id      BIGSERIAL PRIMARY KEY,
                         codigo  VARCHAR(20)  NOT NULL,
                         nome    VARCHAR(150) NOT NULL,
                         ativo   BOOLEAN      NOT NULL DEFAULT TRUE,
                         CONSTRAINT uk_filial_codigo UNIQUE (codigo)
);

CREATE TABLE vendedores (
                            id          BIGSERIAL PRIMARY KEY,
                            nome        VARCHAR(150) NOT NULL,
                            email       VARCHAR(150) NOT NULL,
                            senha_hash  VARCHAR(100) NOT NULL,
                            ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
                            CONSTRAINT uk_vendedor_email UNIQUE (email)
);

-- Colunas novas em vendas. Adicionadas como NULLABLE primeiro para não
-- quebrar em bases já existentes; se a tabela ainda estiver vazia neste
-- ambiente, os NOT NULL abaixo já podem ser aplicados diretamente.
ALTER TABLE vendas
    ADD COLUMN filial_id                     BIGINT,
    ADD COLUMN vendedor_id                   BIGINT,
    ADD COLUMN estoque_avancado              BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN status_score_cliente          VARCHAR(20) NOT NULL DEFAULT 'NAO_REALIZADA',
    ADD COLUMN numero_serie_nota             VARCHAR(10),
    ADD COLUMN numero_nota                   VARCHAR(20);

ALTER TABLE vendas
    ADD CONSTRAINT fk_vendas_filial FOREIGN KEY (filial_id) REFERENCES filiais (id),
    ADD CONSTRAINT fk_vendas_vendedor FOREIGN KEY (vendedor_id) REFERENCES vendedores (id),
    ADD CONSTRAINT chk_vendas_status_score_cliente
        CHECK (status_score_cliente IN ('NAO_REALIZADA', 'CONSULTANDO', 'APROVADO', 'REPROVADO'));

-- A entidade Java mapeia filial/vendedor como obrigatórios (optional = false).
-- Torna-se NOT NULL aqui, depois de garantir que não há linha órfã (base nova/testcontainers = vazia).
ALTER TABLE vendas
    ALTER COLUMN filial_id SET NOT NULL,
ALTER COLUMN vendedor_id SET NOT NULL;

CREATE INDEX idx_vendas_filial_id ON vendas (filial_id);
CREATE INDEX idx_vendas_vendedor_id ON vendas (vendedor_id);