-- Caixa: sangria e suprimento de dinheiro, com estorno por contrapartida (docs/correcao_caixa.md).
-- Nunca se apaga linha: o lançamento errado é estornado por outro lançamento. Os lançamentos entram no esperado do
-- dinheiro (suprimentos no movimento, sangrias nas saídas) e valem só com o caixa ABERTO. O histórico (V39) já aceita
-- as ações LANCAMENTO e ESTORNO.

CREATE TABLE tb_caixa_lancamento (
    id                  BIGSERIAL     PRIMARY KEY,
    caixa_sessao_id     BIGINT        NOT NULL REFERENCES tb_caixa_sessao (id),
    tipo                VARCHAR(12)   NOT NULL,
    valor               NUMERIC(12,2) NOT NULL,
    descricao           VARCHAR(200),
    conta               VARCHAR(100),
    observacao          VARCHAR(300),
    -- Do estorno: por que o lançamento original estava errado.
    justificativa       VARCHAR(300),
    estornado_de_id     BIGINT        REFERENCES tb_caixa_lancamento (id),
    criado_por_id       BIGINT        REFERENCES usuarios (id),
    criado_em           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Gerada pelo front por formulário: repetir a mesma chave (duplo clique) devolve o lançamento já feito.
    chave_idempotencia  VARCHAR(80)   NOT NULL,
    CONSTRAINT chk_caixa_lancamento_tipo CHECK (tipo IN ('SANGRIA', 'SUPRIMENTO', 'ESTORNO')),
    CONSTRAINT chk_caixa_lancamento_valor CHECK (valor > 0),
    CONSTRAINT chk_caixa_lancamento_estorno CHECK ((tipo = 'ESTORNO') = (estornado_de_id IS NOT NULL)),
    CONSTRAINT uk_caixa_lancamento_chave UNIQUE (caixa_sessao_id, chave_idempotencia)
);

-- Um lançamento só pode ser estornado uma vez.
CREATE UNIQUE INDEX uk_caixa_lancamento_estorno ON tb_caixa_lancamento (estornado_de_id) WHERE estornado_de_id IS NOT NULL;
CREATE INDEX idx_caixa_lancamento_sessao ON tb_caixa_lancamento (caixa_sessao_id, criado_em);
