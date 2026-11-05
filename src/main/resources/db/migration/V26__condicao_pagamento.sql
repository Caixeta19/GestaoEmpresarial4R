-- Condição de pagamento da venda: define o preço dos aparelhos e acessórios com preço de tabela.
-- Literais do CHECK = enum Java CondicaoPagamento.

ALTER TABLE vendas
    ADD COLUMN condicao_pagamento VARCHAR(20) NOT NULL DEFAULT 'A_VISTA',
    ADD CONSTRAINT chk_vendas_condicao_pagamento CHECK (condicao_pagamento IN (
        'PIX', 'A_VISTA',
        'PARCELADO_13', 'PARCELADO_14', 'PARCELADO_15', 'PARCELADO_16', 'PARCELADO_17',
        'PARCELADO_18', 'PARCELADO_19', 'PARCELADO_20', 'PARCELADO_21'));
