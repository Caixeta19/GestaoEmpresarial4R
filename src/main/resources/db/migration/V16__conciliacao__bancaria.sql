ALTER TABLE tb_titulo_financeiro
    ADD COLUMN IF NOT EXISTS identificador_pagamento VARCHAR(100),
    ADD COLUMN IF NOT EXISTS codigo_barras           VARCHAR(60),
    ADD COLUMN IF NOT EXISTS banco_pagador           VARCHAR(30),
    ADD COLUMN IF NOT EXISTS pago_em                 TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS valor_pago              NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS transacao_bancaria_id   BIGINT,
    ADD COLUMN IF NOT EXISTS comprovante_url         TEXT,
    ADD COLUMN IF NOT EXISTS versao                  BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS ux_titulo_ident
    ON tb_titulo_financeiro (identificador_pagamento)
    WHERE identificador_pagamento IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_titulo_barras
    ON tb_titulo_financeiro (codigo_barras)
    WHERE codigo_barras IS NOT NULL;

CREATE TABLE webhook_log (
                             id                BIGSERIAL PRIMARY KEY,
                             provider          VARCHAR(30)  NOT NULL,
                             payload_sha256    CHAR(64)     NOT NULL,
                             payload_raw       TEXT         NOT NULL,
                             ip_origem         VARCHAR(64),
                             assinatura_valida BOOLEAN      NOT NULL,
                             status            VARCHAR(20)  NOT NULL,
                             tentativas        INT          NOT NULL DEFAULT 0,
                             erro              TEXT,
                             recebido_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),
                             processado_em     TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_webhook_log_idem
    ON webhook_log (provider, payload_sha256) WHERE status <> 'REJEITADO';
CREATE INDEX ix_webhook_log_fila ON webhook_log (status, recebido_em);

CREATE TABLE transacao_bancaria (
                                    id              BIGSERIAL PRIMARY KEY,
                                    webhook_log_id  BIGINT        NOT NULL REFERENCES webhook_log(id),
                                    provider        VARCHAR(30)   NOT NULL,
                                    id_externo      VARCHAR(120)  NOT NULL,
                                    identificador   VARCHAR(100),
                                    valor           NUMERIC(15,2) NOT NULL,
                                    liquidado_em    TIMESTAMPTZ   NOT NULL,
                                    resultado       VARCHAR(20)   NOT NULL,
                                    titulo_id       BIGINT REFERENCES tb_titulo_financeiro(id),
                                    comprovante_url TEXT,
                                    motivo          TEXT,
                                    criado_em       TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                    UNIQUE (provider, id_externo)
);
CREATE INDEX ix_transacao_titulo ON transacao_bancaria (titulo_id);