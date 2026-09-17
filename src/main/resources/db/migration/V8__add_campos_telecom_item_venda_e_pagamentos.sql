ALTER TABLE itens_venda
    ADD COLUMN tabela_preco           VARCHAR(50),
    ADD COLUMN sva                    BOOLEAN,
    ADD COLUMN seguro                 BOOLEAN,
    ADD COLUMN segmento               VARCHAR(50),
    ADD COLUMN tipo_servico           VARCHAR(50),
    ADD COLUMN ddd                    VARCHAR(2),
    ADD COLUMN plano_antigo           VARCHAR(60),
    ADD COLUMN plano                  VARCHAR(60),
    ADD COLUMN debito_automatico      BOOLEAN,
    ADD COLUMN valor_adicional        NUMERIC(12,2),
    ADD COLUMN valor_acrescimo        NUMERIC(12,2),
    ADD COLUMN desconto               NUMERIC(12,2),
    ADD COLUMN cupom                  BOOLEAN,
    ADD COLUMN vencimento_fatura      VARCHAR(2),
    ADD COLUMN numero_acesso          VARCHAR(20),
    ADD COLUMN sistema_origem         VARCHAR(10),
    ADD COLUMN num_ordem_next         VARCHAR(30),
    ADD COLUMN num_solicitacao_ged    VARCHAR(30),
    ADD COLUMN simcard_3g             VARCHAR(30),
    ADD COLUMN simcard_4g             VARCHAR(30),
    ADD COLUMN cliente_possui_simcard BOOLEAN,
    ADD COLUMN simcard_doado          BOOLEAN,
    ADD COLUMN desconto_chip          NUMERIC(12,2),
    ADD COLUMN valor_chip             NUMERIC(12,2),
    ADD COLUMN serial_confirmado      BOOLEAN;

CREATE INDEX idx_itens_venda_numero_acesso ON itens_venda (numero_acesso);

CREATE TABLE pagamentos_venda (
                                  id          BIGSERIAL PRIMARY KEY,
                                  venda_id    BIGINT      NOT NULL REFERENCES vendas (id),
                                  forma       VARCHAR(30) NOT NULL
                                      CHECK (forma IN ('CARTAO_CREDITO', 'CARTAO_DEBITO', 'PIX', 'DINHEIRO', 'BOLETO')),
                                  valor       NUMERIC(12,2) NOT NULL,
                                  parcelas    INTEGER     NOT NULL DEFAULT 1,
                                  criado_em   TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_pagamentos_venda_venda ON pagamentos_venda (venda_id);