-- Caixa: conferência por forma de pagamento, fechamento só com diferença zero (docs/correcao_caixa.md).
-- O gerente da loja nunca fecha com divergência; financeiro, gerente de área, auditoria e admin fecham com justificativa.

-- Novas ações no histórico (LANCAMENTO e ESTORNO já entram aqui; quem as usa é a V40 de sangria e suprimento).
-- TENTATIVA_FECHAMENTO tem 20 caracteres e a coluna nasceu com 12.
ALTER TABLE tb_caixa_historico ALTER COLUMN acao TYPE VARCHAR(30);
ALTER TABLE tb_caixa_historico DROP CONSTRAINT chk_caixa_historico_acao;
ALTER TABLE tb_caixa_historico
    ADD CONSTRAINT chk_caixa_historico_acao
        CHECK (acao IN ('ABRIU', 'FECHOU', 'CONFEROU', 'REABRIU', 'TENTATIVA_FECHAMENTO', 'LANCAMENTO', 'ESTORNO'));

-- Caixa fechado com diferença (exceção): fica AGUARDANDO conferência e a lista mostra a divergência.
ALTER TABLE tb_caixa_sessao
    ADD COLUMN fechado_com_divergencia   BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN justificativa_divergencia VARCHAR(300);

-- Cada fechamento recusado por diferença (contra tentativa e erro). Gravada numa transação própria, para não sumir
-- com o rollback do 422. Máximo de 5 por caixa por dia (São Paulo) para quem não pode fechar com divergência.
CREATE TABLE tb_caixa_tentativa_fechamento (
    id               BIGSERIAL   PRIMARY KEY,
    caixa_sessao_id  BIGINT      NOT NULL REFERENCES tb_caixa_sessao (id),
    usuario_id       BIGINT      REFERENCES usuarios (id),
    em               TIMESTAMPTZ NOT NULL DEFAULT now(),
    dia              DATE        NOT NULL,
    detalhe_json     TEXT        NOT NULL
);

CREATE INDEX idx_caixa_tentativa_sessao_dia ON tb_caixa_tentativa_fechamento (caixa_sessao_id, dia);
