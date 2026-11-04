-- Renova: o aparelho passa para a TABELA RENOVA quando a venda tem Desconto fabricante + Voucher.
-- O preço Renova da planilha já vem com o desconto do fabricante aplicado; o desconto é guardado
-- só para controle/conciliação com o fabricante (não abate o total de novo).

-- 1. Coluna DESCONTO do bloco TABELA RENOVA (preenchida só nas linhas RENOVA) --------------------
ALTER TABLE tb_preco_item
    ADD COLUMN desconto_fabricante NUMERIC(12,2);

-- 2. Voucher como forma de pagamento (com o código do voucher) -------------------------------------
-- O CHECK de V8 foi criado sem nome: remove qualquer CHECK de pagamentos_venda que trate de "forma".
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN SELECT conname
               FROM pg_constraint
              WHERE conrelid = 'pagamentos_venda'::regclass
                AND contype = 'c'
                AND pg_get_constraintdef(oid) LIKE '%forma%'
    LOOP
        EXECUTE format('ALTER TABLE pagamentos_venda DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;

ALTER TABLE pagamentos_venda
    ADD CONSTRAINT chk_pagamentos_venda_forma
        CHECK (forma IN ('CARTAO_CREDITO', 'CARTAO_DEBITO', 'PIX', 'DINHEIRO', 'BOLETO', 'VOUCHER')),
    ADD COLUMN codigo_voucher VARCHAR(60);

-- 3. Venda com Renova ligado e a soma do desconto do fabricante dos aparelhos -----------------------
ALTER TABLE vendas
    ADD COLUMN renova              BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN desconto_fabricante NUMERIC(12,2) NOT NULL DEFAULT 0;

-- 4. Desconto do fabricante de cada aparelho vendido pela Renova ------------------------------------
ALTER TABLE itens_venda
    ADD COLUMN desconto_fabricante NUMERIC(12,2);
