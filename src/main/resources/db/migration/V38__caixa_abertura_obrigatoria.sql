-- Caixa: abertura diária obrigatória antes de vender (docs/financeiro_caixa.md).
-- Um caixa por dia por caixa da filial; a venda passa a apontar para o caixa em que foi lançada.

ALTER TABLE tb_caixa_sessao
    ADD COLUMN aberto_por_id             BIGINT       REFERENCES usuarios (id),
    ADD COLUMN fechado_por_id            BIGINT       REFERENCES usuarios (id),
    ADD COLUMN conferencia               VARCHAR(12)  NOT NULL DEFAULT 'AGUARDANDO',
    ADD COLUMN conferido_por_id          BIGINT       REFERENCES usuarios (id),
    ADD COLUMN conferido_em              TIMESTAMPTZ,
    ADD COLUMN observacao                VARCHAR(300),
    ADD COLUMN justificativa_abertura    VARCHAR(300),
    ADD COLUMN reaberto_por_id           BIGINT       REFERENCES usuarios (id),
    ADD COLUMN reaberto_em               TIMESTAMPTZ,
    ADD COLUMN justificativa_reabertura  VARCHAR(300);

ALTER TABLE tb_caixa_sessao
    ADD CONSTRAINT chk_caixa_sessao_status CHECK (status IN ('ABERTO', 'FECHADO')),
    ADD CONSTRAINT chk_caixa_sessao_conferencia CHECK (conferencia IN ('AGUARDANDO', 'CONFERIDO'));

-- O nome do caixa é texto livre: o índice compara sem diferenciar maiúsculas nem espaços das pontas.
CREATE UNIQUE INDEX uk_caixa_sessao_filial_caixa_data
    ON tb_caixa_sessao (filial_id, upper(btrim(caixa_pdv)), data_abertura);

CREATE INDEX idx_caixa_sessao_data ON tb_caixa_sessao (data_abertura);
CREATE INDEX idx_caixa_sessao_status ON tb_caixa_sessao (status, conferencia);

-- Vendas antigas ficam sem caixa (NULL).
ALTER TABLE vendas ADD COLUMN caixa_sessao_id BIGINT REFERENCES tb_caixa_sessao (id);
CREATE INDEX idx_vendas_caixa_sessao ON vendas (caixa_sessao_id);

CREATE TABLE tb_caixa_historico (
    id               BIGSERIAL PRIMARY KEY,
    caixa_sessao_id  BIGINT       NOT NULL REFERENCES tb_caixa_sessao (id),
    acao             VARCHAR(12)  NOT NULL,
    usuario_id       BIGINT       REFERENCES usuarios (id),
    em               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    texto            VARCHAR(300),
    CONSTRAINT chk_caixa_historico_acao CHECK (acao IN ('ABRIU', 'FECHOU', 'CONFEROU', 'REABRIU'))
);

CREATE INDEX idx_caixa_historico_sessao ON tb_caixa_historico (caixa_sessao_id, em);
