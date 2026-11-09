-- Renova: o voucher deixa de ser forma de pagamento e o número passa a ficar na venda.
-- A forma VOUCHER e pagamentos_venda.codigo_voucher continuam só para o histórico das vendas finalizadas.

ALTER TABLE vendas
    ADD COLUMN codigo_voucher VARCHAR(60);

-- Vendas abertas com Renova ligado levam o código da primeira linha VOUCHER.
UPDATE vendas v
   SET codigo_voucher = (SELECT NULLIF(TRIM(p.codigo_voucher), '')
                           FROM pagamentos_venda p
                          WHERE p.venda_id = v.id
                            AND p.forma = 'VOUCHER'
                          ORDER BY p.id
                          LIMIT 1)
 WHERE v.status = 'ABERTA'
   AND v.renova = TRUE;

-- Em toda venda aberta, a linha VOUCHER sai dos pagamentos. O total e a linha FINANCIADO são acertados na próxima
-- edição ou ao finalizar, que reprecifica a venda.
DELETE FROM pagamentos_venda p
 USING vendas v
 WHERE p.venda_id = v.id
   AND v.status = 'ABERTA'
   AND p.forma = 'VOUCHER';

-- A checagem de voucher repetido consulta as duas colunas.
CREATE INDEX idx_vendas_codigo_voucher
    ON vendas (codigo_voucher)
    WHERE codigo_voucher IS NOT NULL;

CREATE INDEX idx_pagamentos_venda_codigo_voucher
    ON pagamentos_venda (codigo_voucher)
    WHERE codigo_voucher IS NOT NULL;
