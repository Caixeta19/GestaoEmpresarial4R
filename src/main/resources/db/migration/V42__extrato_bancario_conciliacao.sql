-- Extrato do Banco do Brasil (API Extratos v2) e conciliação automática das contas a pagar
-- (docs/contas_pagar.md, Fase D). O extrato é somente leitura: só entram DÉBITOS contabilizados.
-- id_externo é o textoIdentificadorUnicoTransacao da v2; quando vem vazio, "H:" + SHA-256 de campos estáveis do lançamento.

CREATE TABLE tb_extrato_lancamento (
    id                      BIGSERIAL     PRIMARY KEY,
    provider                VARCHAR(30)   NOT NULL,
    id_externo              VARCHAR(80)   NOT NULL,
    data_lancamento         DATE          NOT NULL,
    valor                   NUMERIC(15,2) NOT NULL,
    numero_documento        BIGINT,
    numero_lote             INTEGER,
    codigo_historico        INTEGER,
    sub_historico           VARCHAR(25),
    info_complementar       VARCHAR(38),
    contraparte_documento   VARCHAR(15),
    contraparte_tipo        VARCHAR(1),
    contraparte_banco       INTEGER,
    situacao                VARCHAR(20)   NOT NULL DEFAULT 'NOVO',
    -- Conta casada (CONCILIADO) ou conta divergente ligada ao lançamento (AMBIGUO com valor diferente).
    titulo_id               BIGINT        REFERENCES tb_titulo_financeiro (id),
    conciliado_em           TIMESTAMPTZ,
    origem                  VARCHAR(10),
    motivo                  VARCHAR(300),
    -- true depois que a baixa foi desfeita ao reabrir a conta: o reprocesso automático não casa de novo (senão
    -- baixaria a mesma conta errada); só a pessoa vincula ou ignora.
    desvinculado            BOOLEAN       NOT NULL DEFAULT false,
    -- Item do extrato como o banco enviou (sem credenciais).
    payload_raw             JSONB,
    criado_em               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_extrato_lancamento_externo UNIQUE (provider, id_externo),
    CONSTRAINT chk_extrato_lancamento_valor CHECK (valor > 0),
    CONSTRAINT chk_extrato_lancamento_situacao CHECK (situacao IN
        ('NOVO', 'CONCILIADO', 'AMBIGUO', 'SEM_CORRESPONDENCIA', 'IGNORADO')),
    CONSTRAINT chk_extrato_lancamento_origem CHECK (origem IS NULL OR origem IN ('AUTO', 'MANUAL'))
);

CREATE INDEX idx_extrato_lancamento_situacao ON tb_extrato_lancamento (situacao, data_lancamento DESC);
CREATE INDEX idx_extrato_lancamento_titulo ON tb_extrato_lancamento (titulo_id) WHERE titulo_id IS NOT NULL;
-- Uma conta só é baixada por um lançamento.
CREATE UNIQUE INDEX uk_extrato_lancamento_conciliado_titulo ON tb_extrato_lancamento (titulo_id)
    WHERE situacao = 'CONCILIADO';

CREATE TABLE tb_extrato_sincronizacao (
    id                  BIGSERIAL    PRIMARY KEY,
    inicio              TIMESTAMPTZ  NOT NULL,
    fim                 TIMESTAMPTZ  NOT NULL,
    status              VARCHAR(10)  NOT NULL,
    origem              VARCHAR(10)  NOT NULL,
    disparado_por_id    BIGINT       REFERENCES usuarios (id),
    lancamentos_novos   INTEGER      NOT NULL DEFAULT 0,
    conciliados         INTEGER      NOT NULL DEFAULT 0,
    erro                VARCHAR(500),
    CONSTRAINT chk_extrato_sincronizacao_status CHECK (status IN ('OK', 'ERRO')),
    CONSTRAINT chk_extrato_sincronizacao_origem CHECK (origem IN ('AGENDADA', 'MANUAL'))
);

CREATE INDEX idx_extrato_sincronizacao_fim ON tb_extrato_sincronizacao (fim DESC);

-- A coluna nasceu na V41 sem FK porque a tabela do extrato só chega agora.
ALTER TABLE tb_titulo_financeiro
    ADD CONSTRAINT fk_titulo_extrato_lancamento FOREIGN KEY (extrato_lancamento_id)
        REFERENCES tb_extrato_lancamento (id);

-- Um lançamento baixa no máximo uma conta.
CREATE UNIQUE INDEX uk_titulo_extrato_lancamento ON tb_titulo_financeiro (extrato_lancamento_id)
    WHERE extrato_lancamento_id IS NOT NULL;
