-- Contas a pagar: ciclo de vida completo (docs/contas_pagar.md, Fase B).
-- 1) Corrige o CHECK de status: a V10 só aceitava ABERTO/BAIXADO, mas o enum StatusTitulo e o MatchingService já usam
--    DIVERGENTE (marcar divergente estourava no banco). Passa a aceitar também CANCELADO.
-- 2) Colunas novas do cadastro de contas a pagar (nulas para RECEBER).
-- 3) Auditoria em tb_titulo_historico (nunca se apaga linha).
-- extrato_lancamento_id nasce sem FK: a tabela do extrato chega na Fase D, que cria a FK.

-- Remove o CHECK de status seja qual for o nome que o Postgres deu à constraint inline da V10.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT c.conname
             FROM pg_constraint c
             WHERE c.conrelid = 'tb_titulo_financeiro'::regclass
               AND c.contype = 'c'
               AND pg_get_constraintdef(c.oid) LIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE tb_titulo_financeiro DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;

ALTER TABLE tb_titulo_financeiro
    ADD CONSTRAINT chk_titulo_status CHECK (status IN ('ABERTO', 'BAIXADO', 'DIVERGENTE', 'CANCELADO'));

ALTER TABLE tb_titulo_financeiro
    ADD COLUMN forma_pagamento       VARCHAR(10),
    ADD COLUMN documento_fornecedor  VARCHAR(14),
    ADD COLUMN filial_id             BIGINT REFERENCES filiais (id),
    ADD COLUMN categoria             VARCHAR(60),
    ADD COLUMN observacao            VARCHAR(500),
    ADD COLUMN criado_por_id         BIGINT REFERENCES usuarios (id),
    ADD COLUMN origem_baixa          VARCHAR(20),
    ADD COLUMN baixado_por_id        BIGINT REFERENCES usuarios (id),
    ADD COLUMN justificativa_baixa   VARCHAR(300),
    ADD COLUMN cancelado_em          TIMESTAMPTZ,
    ADD COLUMN cancelado_por_id      BIGINT REFERENCES usuarios (id),
    ADD COLUMN motivo_cancelamento   VARCHAR(300),
    ADD COLUMN extrato_lancamento_id BIGINT;

ALTER TABLE tb_titulo_financeiro
    ADD CONSTRAINT chk_titulo_forma_pagamento CHECK (forma_pagamento IS NULL
        OR forma_pagamento IN ('BOLETO', 'PIX', 'TED', 'TRIBUTO', 'OUTRO')),
    ADD CONSTRAINT chk_titulo_origem_baixa CHECK (origem_baixa IS NULL
        OR origem_baixa IN ('CONCILIACAO_BB', 'MANUAL')),
    ADD CONSTRAINT chk_titulo_documento_fornecedor CHECK (documento_fornecedor IS NULL
        OR documento_fornecedor ~ '^[0-9]{11}$' OR documento_fornecedor ~ '^[0-9]{14}$');

CREATE INDEX idx_titulo_financeiro_filial ON tb_titulo_financeiro (filial_id) WHERE filial_id IS NOT NULL;
CREATE INDEX idx_titulo_financeiro_documento ON tb_titulo_financeiro (documento_fornecedor)
    WHERE documento_fornecedor IS NOT NULL;

CREATE TABLE tb_titulo_historico (
    id          BIGSERIAL    PRIMARY KEY,
    titulo_id   BIGINT       NOT NULL REFERENCES tb_titulo_financeiro (id),
    acao        VARCHAR(30)  NOT NULL,
    usuario_id  BIGINT       REFERENCES usuarios (id),
    em          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    texto       VARCHAR(500),
    CONSTRAINT chk_titulo_historico_acao CHECK (acao IN
        ('CRIOU', 'EDITOU', 'CANCELOU', 'BAIXA_AUTOMATICA', 'BAIXA_MANUAL', 'REABRIU', 'VINCULOU', 'COMPROVANTE'))
);

CREATE INDEX idx_titulo_historico_titulo ON tb_titulo_historico (titulo_id, em);
