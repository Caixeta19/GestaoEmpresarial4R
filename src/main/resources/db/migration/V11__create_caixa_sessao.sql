CREATE TABLE tb_caixa_sessao (
                                 id             BIGSERIAL PRIMARY KEY,
                                 filial_id      BIGINT NOT NULL REFERENCES tb_filial(id),
                                 caixa_pdv      VARCHAR(100) NOT NULL,
                                 data_abertura  DATE NOT NULL,
                                 status         VARCHAR(10) NOT NULL,
                                 aberto_em      TIMESTAMPTZ NOT NULL,
                                 fechado_em     TIMESTAMPTZ
);

CREATE TABLE tb_forma_pagamento_caixa (
                                          id                BIGSERIAL PRIMARY KEY,
                                          caixa_sessao_id   BIGINT NOT NULL REFERENCES tb_caixa_sessao(id),
                                          forma             VARCHAR(30) NOT NULL,
                                          abertura          NUMERIC(12,2) NOT NULL DEFAULT 0,
                                          movimento         NUMERIC(12,2) NOT NULL DEFAULT 0,
                                          saidas            NUMERIC(12,2) NOT NULL DEFAULT 0,
                                          valor_conferido   NUMERIC(12,2),
                                          diferenca         NUMERIC(12,2)
);

CREATE INDEX idx_forma_pagamento_caixa_sessao ON tb_forma_pagamento_caixa(caixa_sessao_id);