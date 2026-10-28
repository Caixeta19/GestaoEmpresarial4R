-- Alinha o nome da coluna com a entidade ItemVenda (estoque_geral_id).
-- A FK fk_itens_venda_estoque_geral criada na V19 acompanha o rename.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'itens_venda' AND column_name = 'item_estoque_id'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'itens_venda' AND column_name = 'estoque_geral_id'
    ) THEN
ALTER TABLE itens_venda RENAME COLUMN item_estoque_id TO estoque_geral_id;
END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_itens_venda_estoque_geral_id
    ON itens_venda (estoque_geral_id);