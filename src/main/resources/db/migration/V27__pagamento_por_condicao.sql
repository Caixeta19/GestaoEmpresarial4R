-- Pagamento ligado à condição: Vivo Pay, tipo da linha de pagamento e composição do total da venda.
-- A coluna condicao_pagamento já veio na V26. Literais dos CHECKs = enums Java FormaPagamento e TipoPagamento.

-- 1. Vivo Pay como forma de pagamento ---------------------------------------------------------------
ALTER TABLE pagamentos_venda DROP CONSTRAINT chk_pagamentos_venda_forma;

ALTER TABLE pagamentos_venda
    ADD CONSTRAINT chk_pagamentos_venda_forma
        CHECK (forma IN ('CARTAO_CREDITO', 'CARTAO_DEBITO', 'PIX', 'DINHEIRO', 'BOLETO', 'VOUCHER', 'VIVO_PAY'));

-- 2. Tipo da linha: PAGAMENTO (PIX e A_VISTA), ENTRADA e FINANCIADO (PARCELADO_N) -------------------
ALTER TABLE pagamentos_venda
    ADD COLUMN tipo VARCHAR(12) NOT NULL DEFAULT 'PAGAMENTO',
    ADD CONSTRAINT chk_pagamentos_venda_tipo CHECK (tipo IN ('PAGAMENTO', 'ENTRADA', 'FINANCIADO'));

-- 3. Composição do total da venda -------------------------------------------------------------------
-- valor_itens: soma no preço cheio, sem juros nem desconto PIX.
-- ajuste_condicao: valor_total - valor_itens (negativo = desconto PIX, positivo = juros).
ALTER TABLE vendas
    ADD COLUMN valor_itens     NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN ajuste_condicao NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN valor_parcela   NUMERIC(12,2) NOT NULL DEFAULT 0;

-- Vendas existentes: o total fica como está, sem ajuste.
UPDATE vendas
SET valor_itens   = valor_total,
    valor_parcela = CASE condicao_pagamento
                        WHEN 'PIX' THEN valor_total
                        WHEN 'A_VISTA' THEN ROUND(valor_total / 12, 2)
                        ELSE ROUND(valor_total / CAST(SUBSTRING(condicao_pagamento FROM 11) AS INTEGER), 2)
                    END;
