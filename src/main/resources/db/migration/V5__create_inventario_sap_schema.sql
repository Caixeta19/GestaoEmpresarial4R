-- V6__create_inventario_sap_schema.sql
-- US-206: conciliação entre o relatório SAP (transação IQ09) e o nosso
-- estoque serializado. Cada upload é uma "execução"; cada linha do arquivo
-- SAP vira um InventarioItem classificado como CORRETO / DIVERGENCIA / PENDENTE.

CREATE TABLE tb_inventario_execucao (
                                        id                   BIGSERIAL PRIMARY KEY,
                                        nome_arquivo         VARCHAR(255),
                                        total_linhas_sap     INTEGER     NOT NULL DEFAULT 0,
                                        total_corretos       INTEGER     NOT NULL DEFAULT 0,
                                        total_divergencias   INTEGER     NOT NULL DEFAULT 0,
                                        total_pendentes      INTEGER     NOT NULL DEFAULT 0,
                                        executado_por        VARCHAR(150),
                                        criado_em            TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE tb_inventario_item (
                                    id                BIGSERIAL PRIMARY KEY,
                                    execucao_id       BIGINT      NOT NULL REFERENCES tb_inventario_execucao (id),
                                    material_sap      VARCHAR(40),
                                    denominacao_sap   VARCHAR(200),
                                    serial_sap        VARCHAR(50),
                                    centro_sap        VARCHAR(20),
                                    deposito_sap      VARCHAR(20),
                                    status_sap        VARCHAR(30),
                                    resultado         VARCHAR(20) NOT NULL
                                        CHECK (resultado IN ('CORRETO', 'DIVERGENCIA', 'PENDENTE')),
                                    observacao        VARCHAR(300),
                                    item_estoque_id   BIGINT REFERENCES tb_item_estoque (id)
);

CREATE INDEX idx_inventario_item_execucao ON tb_inventario_item (execucao_id);
CREATE INDEX idx_inventario_item_resultado ON tb_inventario_item (resultado);