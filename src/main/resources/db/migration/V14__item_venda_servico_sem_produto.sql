-- Serviço (plano) entra no carrinho sem produto e com valor 0:
-- a cobrança é feita na fatura da operadora.
ALTER TABLE itens_venda ALTER COLUMN produto_id DROP NOT NULL;

ALTER TABLE itens_venda DROP CONSTRAINT IF EXISTS itens_venda_valor_unitario_check;

ALTER TABLE itens_venda ADD CONSTRAINT itens_venda_valor_unitario_check
    CHECK (valor_unitario >= 0);