-- V3__add_numero_venda_sequence.sql
-- Gera um identificador público de 7 dígitos para a venda (numero_venda),
-- distinto da PK interna (id). START WITH 1000000 garante 7 dígitos desde
-- o primeiro valor até 9999999.

CREATE SEQUENCE seq_numero_venda START WITH 1000000 INCREMENT BY 1;

ALTER TABLE vendas
    ADD COLUMN numero_venda BIGINT;

-- Backfill de eventuais linhas já existentes (ambientes de teste/dev).
UPDATE vendas SET numero_venda = nextval('seq_numero_venda') WHERE numero_venda IS NULL;

ALTER TABLE vendas
    ALTER COLUMN numero_venda SET NOT NULL,
    ADD CONSTRAINT uk_vendas_numero_venda UNIQUE (numero_venda);

CREATE INDEX idx_vendas_numero_venda ON vendas (numero_venda);