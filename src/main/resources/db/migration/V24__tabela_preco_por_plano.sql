-- Tabela de preços da Telefônica por produto + grupo do plano + tabela (Regular/Renova) + forma de pagamento.
-- Substitui tb_produto_preco_plano (que deixa de ser usada; será removida em uma migration futura).
-- Literais dos CHECKs = enums Java TabelaPreco e GrupoPlano.

-- 1. Vigência como DATE ("dd/MM/yyyy" vira data; qualquer outro texto vira NULL) ------------
ALTER TABLE tb_tabela_preco_execucao
    ALTER COLUMN vigencia_em TYPE DATE
        USING CASE
                  WHEN vigencia_em ~ '^\s*\d{2}/\d{2}/\d{4}\s*$' THEN to_date(trim(vigencia_em), 'DD/MM/YYYY')
                  ELSE NULL
              END;

-- 2. Preços por item / tabela / grupo do plano -----------------------------------------------
CREATE TABLE tb_preco_item (
    id             BIGSERIAL     PRIMARY KEY,
    execucao_id    BIGINT        NOT NULL REFERENCES tb_tabela_preco_execucao (id),
    aba            VARCHAR(60)   NOT NULL,
    tabela         VARCHAR(10)   NOT NULL,
    grupo_plano    VARCHAR(30)   NOT NULL,
    nome_comercial VARCHAR(200)  NOT NULL,
    chave_nome     VARCHAR(200)  NOT NULL,
    preco          NUMERIC(12,2) NOT NULL,
    preco_pix      NUMERIC(12,2),
    parcelas       JSONB,
    CONSTRAINT chk_preco_item_tabela CHECK (tabela IN ('REGULAR', 'RENOVA')),
    CONSTRAINT chk_preco_item_grupo_plano CHECK (grupo_plano IN (
        'PRE', 'CONTROLE_BTL', 'CONTROLE_ENTRADA', 'CONTROLE_ALTO_VALOR', 'POS_INDIVIDUAL',
        'FAMILIA_2', 'FAMILIA_3', 'FAMILIA_4_5', 'VIVO_V', 'MULTIVIVO',
        'VIM_8GB', 'VIM_14GB', 'VIM_20GB', 'VIM_40GB', 'VIM_100GB', 'SEM_PLANO'))
);

CREATE INDEX idx_preco_item_busca ON tb_preco_item (execucao_id, chave_nome, tabela, grupo_plano);

-- 3. Planos (aba PLANOS): plano do cliente -> grupo de preço ----------------------------------
CREATE TABLE tb_plano_vivo (
    id              BIGSERIAL    PRIMARY KEY,
    execucao_id     BIGINT       NOT NULL REFERENCES tb_tabela_preco_execucao (id),
    nome_plano      VARCHAR(200) NOT NULL,
    chave_plano     VARCHAR(200) NOT NULL,
    sistema         VARCHAR(60),
    cod_sistema     VARCHAR(60),
    cod_sap         VARCHAR(60),
    grupo_plano     VARCHAR(30)  NOT NULL,
    disponivel_alta BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_plano_vivo_grupo_plano CHECK (grupo_plano IN (
        'PRE', 'CONTROLE_BTL', 'CONTROLE_ENTRADA', 'CONTROLE_ALTO_VALOR', 'POS_INDIVIDUAL',
        'FAMILIA_2', 'FAMILIA_3', 'FAMILIA_4_5', 'VIVO_V', 'MULTIVIVO',
        'VIM_8GB', 'VIM_14GB', 'VIM_20GB', 'VIM_40GB', 'VIM_100GB', 'SEM_PLANO'))
);

CREATE INDEX idx_plano_vivo_execucao_chave ON tb_plano_vivo (execucao_id, chave_plano);

-- 4. Produtos (aba PRODUTOS): Cod_Sap -> Nome Comercial ---------------------------------------
CREATE TABLE tb_produto_vivo (
    id             BIGSERIAL    PRIMARY KEY,
    execucao_id    BIGINT       NOT NULL REFERENCES tb_tabela_preco_execucao (id),
    cod_sap        VARCHAR(60)  NOT NULL,
    descricao_sap  VARCHAR(255),
    modelo_dpgc    VARCHAR(200),
    cor            VARCHAR(60),
    nome_comercial VARCHAR(200) NOT NULL,
    chave_nome     VARCHAR(200) NOT NULL,
    categoria      VARCHAR(80)
);

CREATE INDEX idx_produto_vivo_execucao_cod_sap ON tb_produto_vivo (execucao_id, cod_sap);

-- 5. Itens de venda guardam como o preço foi calculado ----------------------------------------
ALTER TABLE itens_venda
    ADD COLUMN grupo_plano     VARCHAR(30),
    ADD COLUMN tabela_aplicada VARCHAR(10),
    ADD COLUMN forma_pagamento VARCHAR(30),
    ADD COLUMN parcelas        INTEGER;
