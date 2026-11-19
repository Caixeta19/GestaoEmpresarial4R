-- Inventário: conferência por SKU com a MB52 do SAP (estoque por material x centro x depósito, em quantidade).
-- Segunda conferência, informativa: não muda o cruzamento por serial nem o fechamento (RN.82).

-- 1. Execução: MB52 vigente (reenviar substitui) e totais por resultado de SKU gravados no fechamento.
ALTER TABLE tb_inventario_execucao
    ADD COLUMN mb52_nome_arquivo          VARCHAR(255),
    ADD COLUMN mb52_importada_em          TIMESTAMP,
    ADD COLUMN mb52_importada_por_id      BIGINT REFERENCES usuarios (id),
    ADD COLUMN mb52_total_linhas          INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN mb52_linhas_da_filial      INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN mb52_linhas_outro_centro   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN mb52_linhas_ignoradas      INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_sku_conferido            INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_sku_mb52_difere_iq09     INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_sku_sap_acima_sistema    INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_sku_sap_abaixo_sistema   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_sku_so_no_sap            INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_sku_so_no_sistema        INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_sku_nao_identificado     INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_sku_contagem_divergente  INTEGER NOT NULL DEFAULT 0;

-- 2. Histórico das importações da MB52 (quem/quando; a execução guarda só a vigente).
CREATE TABLE tb_inventario_mb52_importacao
(
    id               BIGSERIAL PRIMARY KEY,
    execucao_id      BIGINT       NOT NULL REFERENCES tb_inventario_execucao (id),
    nome_arquivo     VARCHAR(255),
    importada_em     TIMESTAMP    NOT NULL,
    importada_por_id BIGINT REFERENCES usuarios (id),
    total_linhas     INTEGER      NOT NULL,
    linhas_da_filial INTEGER      NOT NULL
);

CREATE INDEX idx_inventario_mb52_importacao_execucao ON tb_inventario_mb52_importacao (execucao_id, importada_em DESC);

-- 3. Item: SKU resolvido (acessório, IQ09, tabela de preços) ou NAO_IDENTIFICADO. Nulo = execução sem MB52.
ALTER TABLE tb_inventario_item
    ADD COLUMN sku_normalizado VARCHAR(40),
    ADD COLUMN sku_origem      VARCHAR(20);

ALTER TABLE tb_inventario_item
    ADD CONSTRAINT chk_inventario_item_sku_origem
        CHECK (sku_origem IS NULL OR sku_origem IN ('ACESSORIO', 'IQ09', 'TABELA_PRECO', 'NAO_IDENTIFICADO'));

CREATE INDEX idx_inventario_item_execucao_sku ON tb_inventario_item (execucao_id, sku_normalizado);

-- 4. Conferência por SKU. material_normalizado nulo = linha agrupada dos itens sem SKU identificado.
CREATE TABLE tb_inventario_sku
(
    id                     BIGSERIAL PRIMARY KEY,
    execucao_id            BIGINT         NOT NULL REFERENCES tb_inventario_execucao (id),
    material_normalizado   VARCHAR(40),
    material_sap           VARCHAR(40),
    denominacao            VARCHAR(200),
    unidade                VARCHAR(10),
    tem_linha_mb52         BOOLEAN        NOT NULL DEFAULT FALSE,
    qtd_mb52_livre         NUMERIC(15, 3) NOT NULL DEFAULT 0,
    qtd_mb52_qualidade     NUMERIC(15, 3) NOT NULL DEFAULT 0,
    qtd_mb52_bloqueado     NUMERIC(15, 3) NOT NULL DEFAULT 0,
    qtd_mb52_transito      NUMERIC(15, 3) NOT NULL DEFAULT 0,
    valor_unitario_sap     NUMERIC(16, 4),
    qtd_iq09               INTEGER        NOT NULL DEFAULT 0,
    qtd_sistema            INTEGER        NOT NULL DEFAULT 0,
    qtd_bipada             INTEGER        NOT NULL DEFAULT 0,
    qtd_contagem_manual    NUMERIC(15, 3),
    contagem_justificativa VARCHAR(300),
    contado_por_id         BIGINT REFERENCES usuarios (id),
    contado_em             TIMESTAMP,
    origem_sku             VARCHAR(20),
    dif_sap_sistema        NUMERIC(15, 3) NOT NULL DEFAULT 0,
    dif_sap_iq09           NUMERIC(15, 3) NOT NULL DEFAULT 0,
    dif_contagem           NUMERIC(15, 3),
    valor_diferenca        NUMERIC(16, 2),
    resultado              VARCHAR(30)    NOT NULL,
    observacao             VARCHAR(300),
    CONSTRAINT uk_inventario_sku_execucao_material UNIQUE (execucao_id, material_normalizado),
    CONSTRAINT chk_inventario_sku_resultado CHECK (resultado IN (
        'CONFERIDO', 'MB52_DIFERE_IQ09', 'SAP_ACIMA_SISTEMA', 'SAP_ABAIXO_SISTEMA', 'SO_NO_SAP', 'SO_NO_SISTEMA',
        'SKU_NAO_IDENTIFICADO', 'CONTAGEM_DIVERGENTE')),
    CONSTRAINT chk_inventario_sku_origem CHECK (origem_sku IS NULL OR origem_sku IN ('ACESSORIO', 'IQ09', 'TABELA_PRECO'))
);

-- Uma só linha de "não identificados" por execução.
CREATE UNIQUE INDEX uk_inventario_sku_nao_identificado
    ON tb_inventario_sku (execucao_id) WHERE material_normalizado IS NULL;

CREATE INDEX idx_inventario_sku_execucao_resultado ON tb_inventario_sku (execucao_id, resultado);
