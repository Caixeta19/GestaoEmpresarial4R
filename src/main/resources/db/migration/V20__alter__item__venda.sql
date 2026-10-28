ALTER TABLE itens_venda
    ADD COLUMN IF NOT EXISTS estoque_geral_id BIGINT;

ALTER TABLE itens_venda
    ADD CONSTRAINT fk_itens_venda_estoque_geral
        FOREIGN KEY (estoque_geral_id) REFERENCES tb_estoque_geral (id);

CREATE INDEX IF NOT EXISTS idx_itens_venda_estoque_geral_id
    ON itens_venda (estoque_geral_id);