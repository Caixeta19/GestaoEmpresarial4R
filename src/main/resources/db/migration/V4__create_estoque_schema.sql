-- V2__create_estoque_schema.sql
-- Módulo Estoque: Produto (SKU) e ItemEstoque (seriais/IMEI),
-- e integração com itens_venda (US-203: baixa de serial na venda).

-- 1. TABELA DE PRODUTOS / SKUS
CREATE TABLE tb_produto (
                            id                BIGSERIAL PRIMARY KEY,
                            sku               VARCHAR(50)  NOT NULL UNIQUE,
                            descricao         VARCHAR(150) NOT NULL,
                            categoria         VARCHAR(30)  NOT NULL,
                            estoque_minimo    INTEGER      NOT NULL DEFAULT 2,
                            preco_base        NUMERIC(12,2) NOT NULL,
                            requer_serial     BOOLEAN      NOT NULL DEFAULT FALSE,
                            criado_em         TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tb_produto_sku ON tb_produto (sku);
CREATE INDEX idx_tb_produto_categoria ON tb_produto (categoria);

-- 2. TABELA DE ITENS SERIALIZADOS (IMEI / SERIAL)
CREATE TABLE tb_item_estoque (
                                 id             BIGSERIAL PRIMARY KEY,
                                 produto_id     BIGINT NOT NULL REFERENCES tb_produto (id),
                                 serial_imei    VARCHAR(50) NOT NULL UNIQUE,
                                 status         VARCHAR(20) NOT NULL DEFAULT 'DISPONIVEL',
                                 deposito_sap   VARCHAR(10) DEFAULT 'DP01',
                                 data_entrada   TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 data_saida     TIMESTAMP WITHOUT TIME ZONE,
                                 version        BIGINT NOT NULL DEFAULT 0,
                                 CONSTRAINT chk_tb_item_estoque_status
                                     CHECK (status IN ('DISPONIVEL', 'RESERVADO', 'VENDIDO'))
);

CREATE INDEX idx_tb_item_estoque_status ON tb_item_estoque (produto_id, status);
CREATE INDEX idx_tb_item_estoque_serial ON tb_item_estoque (serial_imei);

-- 3. AMARRAÇÃO COM A TABELA EXISTENTE DE ITENS DE VENDA
-- (tabela real criada em V1 é "itens_venda", não "tb_item_venda")
ALTER TABLE itens_venda ADD COLUMN IF NOT EXISTS item_estoque_id BIGINT REFERENCES tb_item_estoque (id);
ALTER TABLE itens_venda ADD COLUMN IF NOT EXISTS serial_imei VARCHAR(50);

CREATE INDEX idx_itens_venda_item_estoque_id ON itens_venda (item_estoque_id);