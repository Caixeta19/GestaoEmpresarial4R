-- Acessório é vendido por unidade (uma leitura do serial = 1 unidade), mas a linha do estoque tem um serial por SKU
-- com quantidade N. quantidade_reservada conta as unidades presas em carrinhos abertos; a linha segue DISPONIVEL
-- enquanto quantidade - quantidade_reservada > 0.
ALTER TABLE tb_estoque_geral
    ADD COLUMN quantidade_reservada INTEGER NOT NULL DEFAULT 0;

ALTER TABLE tb_estoque_geral
    ADD CONSTRAINT chk_estoque_geral_quantidade_reservada CHECK (quantidade_reservada >= 0);
