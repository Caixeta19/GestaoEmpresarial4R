-- Inventário por filial: IQ09 (SAP) x estoque do sistema x contagem física (bipagem).
-- As execuções antigas (V5) continuam legíveis: ficam FECHADO, sem filial, com os resultados antigos.

-- 1. Filial x centro/depósito do SAP. O "Centro" da IQ09 (ex.: 039G) não é o código Adabas da filial.
ALTER TABLE filiais
    ADD COLUMN centro_sap   VARCHAR(10),
    ADD COLUMN deposito_sap VARCHAR(10);

ALTER TABLE filiais
    ADD CONSTRAINT uk_filial_centro_sap UNIQUE (centro_sap);

-- 2. Execução: ciclo ABERTO -> EM_CONTAGEM -> FECHADO (ou CANCELADO), uma aberta por filial.
ALTER TABLE tb_inventario_execucao
    ADD COLUMN filial_id                     BIGINT REFERENCES filiais (id),
    ADD COLUMN status                        VARCHAR(15) NOT NULL DEFAULT 'FECHADO',
    ADD COLUMN aberto_por_id                 BIGINT REFERENCES usuarios (id),
    ADD COLUMN fechado_por_id                BIGINT REFERENCES usuarios (id),
    ADD COLUMN fechado_em                    TIMESTAMP,
    ADD COLUMN linhas_ignoradas_outro_centro INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN linhas_sem_serial             INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN centros_sem_filial            VARCHAR(2000),
    ADD COLUMN total_esperados               INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN total_bipados                 INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN total_conferidos              INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN total_falta                   INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN total_sem_entrada_sistema     INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN total_sobra_fisica            INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN total_vendido_em_estoque      INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN total_so_no_sistema           INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN total_status_sap_divergente   INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN resumo_ia                     TEXT,
    ADD COLUMN diagnosticado_em              TIMESTAMP;

ALTER TABLE tb_inventario_execucao
    ADD CONSTRAINT chk_inventario_execucao_status
        CHECK (status IN ('ABERTO', 'EM_CONTAGEM', 'FECHADO', 'CANCELADO'));

-- Uma execução aberta por filial (abrir outra -> 409).
CREATE UNIQUE INDEX uk_inventario_execucao_aberta_por_filial
    ON tb_inventario_execucao (filial_id)
    WHERE status IN ('ABERTO', 'EM_CONTAGEM');

CREATE INDEX idx_inventario_execucao_filial ON tb_inventario_execucao (filial_id, criado_em DESC);

-- 3. Item: um por serial da execução (IQ09 da filial ∪ estoque da filial ∪ bipados fora da lista).
ALTER TABLE tb_inventario_item
    ALTER COLUMN resultado TYPE VARCHAR(30),
    ADD COLUMN serial_normalizado    VARCHAR(60),
    ADD COLUMN esperado_iq09         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN esperado_sistema      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN status_sistema        VARCHAR(20),
    ADD COLUMN filial_sistema        VARCHAR(100),
    ADD COLUMN status_usuario_sap    VARCHAR(30),
    ADD COLUMN tipo_estoque_sap      VARCHAR(10),
    ADD COLUMN categoria_equipamento VARCHAR(10),
    ADD COLUMN modificado_em_sap     DATE,
    ADD COLUMN modificado_por_sap    VARCHAR(40),
    ADD COLUMN bipado_em             TIMESTAMP,
    ADD COLUMN bipado_por_id         BIGINT REFERENCES usuarios (id),
    ADD COLUMN justificativa         VARCHAR(300),
    ADD COLUMN causa_provavel        VARCHAR(60),
    ADD COLUMN confianca_ia          VARCHAR(5),
    ADD COLUMN acao_sugerida         VARCHAR(500),
    ADD COLUMN diagnosticado_em      TIMESTAMP;

ALTER TABLE tb_inventario_item DROP CONSTRAINT tb_inventario_item_resultado_check;
ALTER TABLE tb_inventario_item
    ADD CONSTRAINT chk_inventario_item_resultado CHECK (resultado IN (
        -- novos
        'PENDENTE', 'CONFERIDO', 'FALTA', 'SEM_ENTRADA_SISTEMA', 'SOBRA_FISICA', 'VENDIDO_EM_ESTOQUE',
        'SO_NO_SISTEMA', 'STATUS_SAP_DIVERGENTE', 'FORA_ESTOQUE_SAP',
        -- execuções antigas (V5)
        'CORRETO', 'DIVERGENCIA'));

ALTER TABLE tb_inventario_item
    ADD CONSTRAINT chk_inventario_item_confianca CHECK (confianca_ia IS NULL OR confianca_ia IN ('ALTA', 'MEDIA', 'BAIXA'));

-- Itens antigos ganham o serial normalizado (mesma regra de antes: sem apóstrofo/espaços, maiúsculo).
UPDATE tb_inventario_item
SET serial_normalizado = upper(regexp_replace(coalesce(serial_sap, ''), '[''[:space:]]', '', 'g'))
WHERE serial_normalizado IS NULL;

-- Bipagem: INSERT ... ON CONFLICT (execucao_id, serial_normalizado). Também é o índice da leitura (< 200 ms).
ALTER TABLE tb_inventario_item
    ADD CONSTRAINT uk_inventario_item_execucao_serial UNIQUE (execucao_id, serial_normalizado);

CREATE INDEX idx_inventario_item_execucao_resultado ON tb_inventario_item (execucao_id, resultado);

-- 4. Seriais da mesma IQ09 em outros centros: contexto do diagnóstico ("aparelho em outra loja").
CREATE TABLE tb_inventario_serial_outro_centro (
    id                 BIGSERIAL PRIMARY KEY,
    execucao_id        BIGINT      NOT NULL REFERENCES tb_inventario_execucao (id) ON DELETE CASCADE,
    serial_normalizado VARCHAR(60) NOT NULL,
    centro_sap         VARCHAR(20),
    deposito_sap       VARCHAR(20),
    status_usuario_sap VARCHAR(30)
);

CREATE INDEX idx_inventario_outro_centro_serial ON tb_inventario_serial_outro_centro (execucao_id, serial_normalizado);
